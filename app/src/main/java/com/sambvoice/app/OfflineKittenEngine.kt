package com.sambvoice.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OfflineKittenEngine(context: Context) {
    private val root = File(context.filesDir, "voice-models")
    private val modelDir = File(root, "kitten-nano-en-v0_1-fp16")
    private val archiveUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_1-fp16.tar.bz2"

    fun isInstalled(): Boolean =
        File(modelDir, "model.fp16.onnx").exists() &&
            File(modelDir, "voices.bin").exists() &&
            File(modelDir, "tokens.txt").exists() &&
            File(modelDir, "espeak-ng-data").isDirectory

    fun install(onStatus: (String) -> Unit) {
        root.mkdirs()
        val archive = File(root, "kitten-nano-en-v0_1-fp16.tar.bz2")
        try {
            onStatus("Downloading offline English voice pack…")
            val connection = (URL(archiveUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output -> input.copyTo(output) }
            }
            connection.disconnect()

            onStatus("Installing voice files…")
            val rootPath = root.canonicalPath + File.separator
            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val target = File(root, entry.name).canonicalFile
                        require(target.path.startsWith(rootPath)) { "Unsafe model archive entry." }
                        if (entry.isDirectory) target.mkdirs()
                        else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output -> tar.copyTo(output) }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
            archive.delete()
            check(isInstalled()) { "Downloaded voice pack is incomplete." }
            onStatus("Offline English voice pack installed • 8 speakers ready.")
        } catch (error: Exception) {
            archive.delete()
            onStatus("Offline model install failed: ${error.message ?: "unknown error"}")
        }
    }

    fun speak(text: String, speakerId: Int, speed: Float = 1.0f, onStatus: (String) -> Unit) {
        Thread {
            var tts: OfflineTts? = null
            var track: AudioTrack? = null
            try {
                if (!isInstalled()) {
                    onStatus("Install the offline voice pack first.")
                    return@Thread
                }
                if (text.isBlank()) {
                    onStatus("Enter text before generating speech.")
                    return@Thread
                }

                onStatus("Generating speech offline…")
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        kitten = OfflineTtsKittenModelConfig(
                            model = File(modelDir, "model.fp16.onnx").absolutePath,
                            voices = File(modelDir, "voices.bin").absolutePath,
                            tokens = File(modelDir, "tokens.txt").absolutePath,
                            dataDir = File(modelDir, "espeak-ng-data").absolutePath
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1
                )
                tts = OfflineTts(config = config)

                val audio = tts.generate(
                    text = text,
                    sid = speakerId.coerceIn(0, 7),
                    speed = speed.coerceIn(0.5f, 2.0f)
                )
                // Use the initialized model's metadata as the authoritative rate.
                // GeneratedAudio.sampleRate may be invalid in some Android bindings.
                val sampleRate = tts.sampleRate
                require(sampleRate in 8000..48000) {
                    "Offline model reported unsupported sample rate: $sampleRate Hz."
                }
                require(audio.samples.isNotEmpty()) {
                    "The voice model returned empty audio. Try different text."
                }

                val pcm = ShortArray(audio.samples.size) { index ->
                    val sample = audio.samples[index]
                    (if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f)
                        .let { (it * Short.MAX_VALUE).toInt().toShort() }
                }

                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                require(minBuffer > 0) { "This device does not support undefined Hz mono PCM audio (code $minBuffer)." }

                val bufferBytes = maxOf(minBuffer, 8192)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                check(track.state == AudioTrack.STATE_INITIALIZED) {
                    "Audio output could not initialize. Try restarting the app or using another audio output."
                }

                track.play()
                var offset = 0
                while (offset < pcm.size) {
                    val count = minOf(4096, pcm.size - offset)
                    val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                    check(written > 0) { "Audio output write failed (code $written)." }
                    offset += written
                }

                onStatus("Playing offline neural voice • Speaker ${speakerId + 1}")
                val deadline = SystemClock.elapsedRealtime() +
                    (pcm.size * 1000L / sampleRate) + 10000L
                while (track.playbackHeadPosition.toLong() < pcm.size.toLong()) {
                    check(SystemClock.elapsedRealtime() < deadline) { "Audio playback timed out." }
                    SystemClock.sleep(25)
                }
                onStatus("Offline speech finished.")
            } catch (error: Exception) {
                onStatus("Offline speech error: ${error.message ?: "unknown error"}")
            } finally {
                try { track?.pause() } catch (_: Exception) {}
                try { track?.flush() } catch (_: Exception) {}
                try { track?.stop() } catch (_: Exception) {}
                track?.release()
                tts?.release()
            }
        }.start()
    }
}
