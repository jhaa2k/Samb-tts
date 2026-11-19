package com.sambvoice.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

/**
 * Free, fully offline Odia voice: Meta MMS-TTS (Odia) running on Sherpa-ONNX.
 *
 * Honest limits:
 *  - One fixed Odia voice. It is NOT a Sambalpuri voice and cannot clone one.
 *    The Sambalpuri flavour comes only from SambTextProcessor (lexicon + spelling rules).
 *  - Model licence is CC-BY-NC-4.0 (non-commercial use only).
 *  - The pack is hosted by a third party (iTantra) on Hugging Face; if that link ever
 *    disappears, change [packUrl] or import any Sherpa-ONNX VITS Odia model by hand.
 */
class OfflineOdiaEngine(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "voice-models")
    private val modelDir = File(root, "odia-mms-vits")
    private val packUrl = "https://huggingface.co/datasets/Mr66/itantra-packs/resolve/main/or-tts.itpack"

    @Volatile private var player: AudioTrack? = null
    private val generationId = AtomicInteger(0)

    private fun findModel(dir: File): File? =
        dir.takeIf { it.isDirectory }?.walkTopDown()?.firstOrNull { it.isFile && it.extension == "onnx" && it.length() > 0L }

    private fun findTokens(dir: File): File? =
        dir.takeIf { it.isDirectory }?.walkTopDown()?.firstOrNull { it.isFile && it.name == "tokens.txt" && it.length() > 0L }

    fun isInstalled(): Boolean = findModel(modelDir) != null && findTokens(modelDir) != null

    /** Downloads the .itpack (a plain zip, ~114 MB) and unpacks it. Run off the UI thread. */
    fun install(onStatus: (String) -> Unit) {
        Thread {
            root.mkdirs()
            val archive = File(root, "odia-tts.itpack")
            val staging = File(root, "odia-installing")
            try {
                onStatus("Downloading Odia voice pack (~114 MB)…")
                val connection = (URL(packUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 180000
                    instanceFollowRedirects = true
                }
                try {
                    val code = connection.responseCode
                    check(code in 200..299) { "Odia voice pack download failed (HTTP $code)." }
                    val total = connection.contentLengthLong
                    var done = 0L
                    var lastReport = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(archive).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n <= 0) break
                                output.write(buffer, 0, n)
                                done += n
                                if (done - lastReport > 8L * 1024 * 1024) {
                                    lastReport = done
                                    val mb = done / (1024 * 1024)
                                    onStatus("Downloading Odia voice pack… $mb" + if (total > 0) " / ${total / (1024 * 1024)} MB" else " MB")
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                onStatus("Installing Odia voice files…")
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()
                val stagingPath = staging.canonicalPath + File.separator
                ZipInputStream(archive.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(staging, entry.name).canonicalFile
                        require(target.path.startsWith(stagingPath)) { "Unsafe voice pack entry." }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out -> zip.copyTo(out) }
                        }
                        entry = zip.nextEntry
                    }
                }
                check(findModel(staging) != null) { "Voice pack has no .onnx model file." }
                check(findTokens(staging) != null) { "Voice pack has no tokens.txt." }

                if (modelDir.exists()) modelDir.deleteRecursively()
                val ok = staging.renameTo(modelDir) || staging.copyRecursively(modelDir, overwrite = true)
                check(ok) { "Could not install Odia model files." }
                check(isInstalled()) { "Installed Odia voice pack failed validation." }
                onStatus("Offline Odia voice installed. Tap Test to hear a sample.")
            } catch (error: Exception) {
                onStatus("Odia voice install failed: ${error.message ?: "unknown error"}")
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

    /** [text] may be English / Hindi / Odia / mixed; it is converted to Odia script first. */
    fun speak(
        text: String,
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
                val model = findModel(modelDir)
                val tokens = findTokens(modelDir)
                if (model == null || tokens == null) {
                    onStatus("Odia voice is not installed. Open Offline and download it first.")
                    return@Thread
                }
                if (text.isBlank()) {
                    onStatus("Enter text before generating speech.")
                    return@Thread
                }
                val odia = SambTextProcessor.toOdia(text)
                if (odia.isBlank()) {
                    onStatus("Nothing speakable after text cleanup.")
                    return@Thread
                }

                onStatus("Loading offline Odia voice…")
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            tokens = tokens.absolutePath
                        ),
                        numThreads = numThreads.coerceIn(1, 4),
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1
                )
                tts = OfflineTts(config = config)
                val outputRate = tts.sampleRate()
                check(outputRate in 8000..48000) { "Odia model returned invalid sample rate: $outputRate Hz." }
                val minBuffer = AudioTrack.getMinBufferSize(
                    outputRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
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
                onStatus("Speaking Odia · $outputRate Hz")

                val audio = tts.generateWithCallback(
                    text = odia,
                    sid = 0,
                    speed = speed.coerceIn(0.5f, 2.0f)
                ) { samples ->
                    if (requestId != generationId.get()) {
                        0
                    } else {
                        val pcm = ShortArray(samples.size) { i ->
                            (samples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                        }
                        var offset = 0
                        var ok = true
                        while (offset < pcm.size && requestId == generationId.get()) {
                            val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                            if (written <= 0) { ok = false; break }
                            offset += written
                        }
                        if (ok && requestId == generationId.get()) 1 else 0
                    }
                }
                check(requestId == generationId.get()) { "Speech generation was stopped." }
                check(audio.samples.isNotEmpty()) { "Odia model generated no audio samples." }

                if (player === track) player = null
                try { track.stop() } catch (_: Exception) {}
                track.release()
                activeTrack = null
                if (requestId == generationId.get()) onStatus("Odia speech finished.")
            } catch (error: Exception) {
                val failed = activeTrack
                if (player === failed) player = null
                try { failed?.pause() } catch (_: Exception) {}
                try { failed?.flush() } catch (_: Exception) {}
                try { failed?.release() } catch (_: Exception) {}
                if (requestId == generationId.get()) {
                    onStatus("Odia speech error: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                try { tts?.release() } catch (_: Exception) {}
            }
        }.start()
    }
}
