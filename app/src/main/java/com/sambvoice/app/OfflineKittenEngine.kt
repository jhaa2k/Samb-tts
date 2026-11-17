package com.sambvoice.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class OfflineKittenEngine(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "voice-models")
    private val modelDir = File(root, "kitten-nano-en-v0_1-fp16")
    private val archiveUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_1-fp16.tar.bz2"

    @Volatile private var player: AudioTrack? = null
    private val generationId = AtomicInteger(0)

    fun isInstalled(): Boolean =
        File(modelDir, "model.fp16.onnx").let { it.isFile && it.length() > 0L } &&
            File(modelDir, "voices.bin").let { it.isFile && it.length() > 0L } &&
            File(modelDir, "tokens.txt").let { it.isFile && it.length() > 0L } &&
            File(modelDir, "espeak-ng-data").isDirectory

    fun install(onStatus: (String) -> Unit) {
        Thread {
            root.mkdirs()
            val archive = File(root, "kitten-nano-en-v0_1-fp16.tar.bz2")
            val staging = File(root, "kitten-nano-installing")
            try {
                onStatus("Downloading offline English voice pack…")
                val connection = (URL(archiveUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 180000
                    instanceFollowRedirects = true
                }
                try {
                    val code = connection.responseCode
                    check(code in 200..299) { "Model download failed (HTTP $code)." }
                    connection.inputStream.use { input ->
                        FileOutputStream(archive).use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }

                onStatus("Installing voice files…")
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()
                val stagingPath = staging.canonicalPath + File.separator
                BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            val target = File(staging, entry.name).canonicalFile
                            require(target.path.startsWith(stagingPath)) { "Unsafe model archive entry." }
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = tar.read(buffer)
                                        if (count <= 0) break
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            entry = tar.nextTarEntry
                        }
                    }
                }

                val extractedModel = if (File(staging, "kitten-nano-en-v0_1-fp16").isDirectory) {
                    File(staging, "kitten-nano-en-v0_1-fp16")
                } else staging
                val required = listOf("model.fp16.onnx", "voices.bin", "tokens.txt", "espeak-ng-data")
                required.forEach { name ->
                    val file = File(extractedModel, name)
                    check(if (name == "espeak-ng-data") file.isDirectory else file.isFile && file.length() > 0L) {
                        "Voice pack is missing $name."
                    }
                }
                if (modelDir.exists()) modelDir.deleteRecursively()
                val installed = if (extractedModel.renameTo(modelDir)) {
                    true
                } else {
                    extractedModel.copyRecursively(modelDir, overwrite = true)
                }
                check(installed) {
                    "Could not install offline model files."
                }
                check(isInstalled()) { "Installed voice pack failed validation." }
                onStatus("Offline English voice pack installed. Tap Preview to hear a sample.")
            } catch (error: Exception) {
                onStatus("Offline model install failed: ${error.message ?: "unknown error"}")
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }.start()
    }

    fun stop() {
        generationId.incrementAndGet()
        val old = player
        player = null
        try { old?.pause() } catch (_: Exception) {}
        try { old?.flush() } catch (_: Exception) {}
        try { old?.stop() } catch (_: Exception) {}
        try { old?.release() } catch (_: Exception) {}
    }

    fun speak(
        text: String,
        speakerId: Int,
        speed: Float = 1.0f,
        volume: Float = 1.0f,
        numThreads: Int = 2,
        onStatus: (String) -> Unit
    ) {
        val requestId = generationId.incrementAndGet()
        Thread {
            var tts: OfflineTts? = null
            var activeTrack: AudioTrack? = null
            try {
                if (!isInstalled()) {
                    onStatus("Offline voice pack is not installed. Open Offline and download it first.")
                    return@Thread
                }
                if (text.isBlank()) {
                    onStatus("Enter text before generating speech.")
                    return@Thread
                }

                onStatus("Loading offline neural voice…")
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        kitten = OfflineTtsKittenModelConfig(
                            model = File(modelDir, "model.fp16.onnx").absolutePath,
                            voices = File(modelDir, "voices.bin").absolutePath,
                            tokens = File(modelDir, "tokens.txt").absolutePath,
                            dataDir = File(modelDir, "espeak-ng-data").absolutePath
                        ),
                        numThreads = numThreads.coerceIn(1, 4),
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1
                )
                tts = OfflineTts(config = config)
                val modelRate = tts.sampleRate()
                check(modelRate in 8000..48000) { "Offline model returned invalid sample rate: $modelRate Hz." }
                check(tts.numSpeakers() <= 0 || speakerId in 0 until tts.numSpeakers()) {
                    "Selected speaker is not available in this voice pack."
                }

                val outputRate = tts.sampleRate()
                check(outputRate in 8000..48000) { "Offline output sample rate is invalid: $outputRate Hz." }
                val minBuffer = AudioTrack.getMinBufferSize(
                    outputRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0) { "Android audio output is unavailable (buffer=$minBuffer)." }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(outputRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 16384))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                check(track.state == AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    "Android could not initialize audio output."
                }
                if (requestId != generationId.get()) {
                    track.release()
                    return@Thread
                }
                activeTrack = track
                player = track
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                onStatus("Generating and playing speech · $outputRate Hz")

                val audio = tts.generateWithCallback(
                    text = text.trim(),
                    sid = speakerId.coerceAtLeast(0),
                    speed = speed.coerceIn(0.5f, 2.0f)
                ) { samples ->
                    if (requestId != generationId.get()) {
                        0
                    } else {
                        val pcm = ShortArray(samples.size) { index ->
                            (samples[index].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                        }
                        var offset = 0
                        var ok = true
                        while (offset < pcm.size && requestId == generationId.get()) {
                            val written = track.write(
                                pcm,
                                offset,
                                pcm.size - offset,
                                AudioTrack.WRITE_BLOCKING
                            )
                            if (written <= 0) {
                                ok = false
                                break
                            }
                            offset += written
                        }
                        if (ok && requestId == generationId.get()) 1 else 0
                    }
                }
                check(requestId == generationId.get()) { "Speech generation was stopped." }
                check(audio.samples.isNotEmpty()) { "Voice model generated no audio samples." }
                check(audio.sampleRate == outputRate) {
                    "Generated audio rate (${audio.sampleRate} Hz) does not match model rate ($outputRate Hz)."
                }
                val peak = audio.samples.maxOf { kotlin.math.abs(it) }
                check(peak > 0.0001f) { "Voice model generated silent audio (peak amplitude is zero)." }

                if (player === track) player = null
                try { track.stop() } catch (_: Exception) {}
                track.release()
                activeTrack = null
                if (requestId == generationId.get()) onStatus("Speech finished · audio was streamed to speaker.")
            } catch (error: Exception) {
                val failedTrack = activeTrack
                if (player === failedTrack) player = null
                try { failedTrack?.pause() } catch (_: Exception) {}
                try { failedTrack?.flush() } catch (_: Exception) {}
                try { failedTrack?.release() } catch (_: Exception) {}
                activeTrack = null
                if (requestId == generationId.get()) {
                    onStatus("Offline speech error: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                try { tts?.release() } catch (_: Exception) {}
            }
        }.start()
    }
}
