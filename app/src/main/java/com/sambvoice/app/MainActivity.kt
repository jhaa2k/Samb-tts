package com.sambvoice.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private enum class EngineMode { ONLINE, OFFLINE }
private enum class AppScreen(val label: String) { STUDIO("Studio"), VOICES("Voices"), OFFLINE("Offline"), SETTINGS("Settings") }
private data class VoiceProfile(val name: String, val style: String, val mode: EngineMode, val voiceId: String = "", val speakerId: Int = 0)

private val onlineVoices = listOf(
    VoiceProfile("Alloy", "Balanced · versatile", EngineMode.ONLINE, "alloy"),
    VoiceProfile("Ash", "Clear · conversational", EngineMode.ONLINE, "ash"),
    VoiceProfile("Ballad", "Expressive · narration", EngineMode.ONLINE, "ballad"),
    VoiceProfile("Coral", "Warm · friendly", EngineMode.ONLINE, "coral"),
    VoiceProfile("Echo", "Smooth · measured", EngineMode.ONLINE, "echo"),
    VoiceProfile("Fable", "Storytelling", EngineMode.ONLINE, "fable"),
    VoiceProfile("Nova", "Bright · lively", EngineMode.ONLINE, "nova"),
    VoiceProfile("Onyx", "Deep · narration", EngineMode.ONLINE, "onyx"),
    VoiceProfile("Sage", "Calm · measured", EngineMode.ONLINE, "sage"),
    VoiceProfile("Shimmer", "Soft · clear", EngineMode.ONLINE, "shimmer"),
    VoiceProfile("Verse", "Expressive", EngineMode.ONLINE, "verse"),
    VoiceProfile("Marin", "Natural · studio", EngineMode.ONLINE, "marin"),
    VoiceProfile("Cedar", "Natural · studio", EngineMode.ONLINE, "cedar")
)
private val offlineVoices = (0..7).map { id ->
    VoiceProfile("Speaker ${id + 1}", "Kitten Nano · English speaker", EngineMode.OFFLINE, speakerId = id)
}

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf("")
    private lateinit var offlineEngine: OfflineKittenEngine
    private var onlinePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)
        offlineEngine = OfflineKittenEngine(this)

        setContent {
            var screen by remember { mutableStateOf(AppScreen.STUDIO) }
            val prefs = remember { getSharedPreferences("voice_settings", MODE_PRIVATE) }
            var input by remember { mutableStateOf(sharedText) }
            var provider by remember { mutableStateOf(if (prefs.getString("default_engine", "offline") == "offline") "offline" else (prefs.getString("default_provider", "openai") ?: "openai")) }
            var selectedVoice by remember { mutableStateOf(prefs.getString("selected_voice", "coral") ?: "coral") }
            var elevenVoiceId by remember { mutableStateOf(prefs.getString("eleven_voice_id", "") ?: "") }
            var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
            var notice by remember { mutableStateOf("Ready") }
            var offlineInstalled by remember { mutableStateOf(offlineEngine.isInstalled()) }
            var installing by remember { mutableStateOf(false) }
            var speechSpeed by remember { mutableStateOf(prefs.getFloat("speech_speed", 1.0f)) }
            var outputVolume by remember { mutableStateOf(prefs.getFloat("output_volume", 1.0f)) }
            var offlineThreads by remember { mutableStateOf(prefs.getInt("offline_threads", 2).coerceIn(1, 4)) }
            var defaultEngine by remember { mutableStateOf(prefs.getString("default_engine", "offline") ?: "offline") }
            var autoStop by remember { mutableStateOf(prefs.getBoolean("stop_before_generate", true)) }

            LaunchedEffect(sharedText) { if (sharedText.isNotBlank()) input = sharedText }

            val scheme = darkColorScheme(
                primary = Color(0xFFB8F36B),
                onPrimary = Color(0xFF17210B),
                secondary = Color(0xFF9CA6B5),
                background = Color(0xFF0B0D10),
                surface = Color(0xFF15191F),
                surfaceVariant = Color(0xFF202630),
                onSurface = Color(0xFFF1F3F5),
                onSurfaceVariant = Color(0xFFADB5C0)
            )

            MaterialTheme(colorScheme = scheme) {
                Scaffold(
                    containerColor = scheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF11151A), tonalElevation = 0.dp) {
                            AppScreen.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = screen == item,
                                    onClick = { screen = item },
                                    icon = {
                                        val navIcon = when (item) {
                                            AppScreen.STUDIO -> Icons.Outlined.Edit
                                            AppScreen.VOICES -> Icons.Outlined.RecordVoiceOver
                                            AppScreen.OFFLINE -> Icons.Outlined.Download
                                            AppScreen.SETTINGS -> Icons.Outlined.Settings
                                        }
                                        Icon(
                                            imageVector = navIcon,
                                            contentDescription = item.label,
                                            tint = if (screen == item) scheme.primary else scheme.onSurfaceVariant
                                        )
                                    },
                                    label = { Text(item.label, fontSize = 11.sp) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                ) { inset ->
                    Column(
                        Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("SAMBALPURI VOICE", color = scheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                                Text(screen.label, color = scheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("VOICE STUDIO", color = scheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                        Spacer(Modifier.height(12.dp))

                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (screen) {
                                AppScreen.STUDIO -> {
                                    InfoCard("CREATE SPEECH", "Write or paste text, select a voice in Voices, then generate. Shared text from other apps appears here.")
                                    OutlinedTextField(
                                        value = input, onValueChange = { input = it },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
                                        label = { Text("Text to speech") },
                                        placeholder = { Text("Type English, Odia or Sambalpuri text…") },
                                        minLines = 5
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { input = "" }, modifier = Modifier.weight(1f)) { Text("Clear") }
                                        OutlinedButton(onClick = { screen = AppScreen.VOICES }, modifier = Modifier.weight(1f)) { Text("Choose voice") }
                                    }
                                    Text("ENGINE", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModeButton("ONLINE AI", provider != "offline", Modifier.weight(1f)) { provider = "openai"; if (selectedVoice !in onlineVoices.map { it.voiceId }) selectedVoice = "coral" }
                                        ModeButton("OFFLINE AI", provider == "offline", Modifier.weight(1f)) { provider = "offline"; selectedVoice = offlineVoices.first().name }
                                    }
                                    Button(
                                        onClick = {
                                            if (input.isBlank()) notice = "Enter some text first."
                                            else if (provider == "offline") {
                                                val voice = offlineVoices.firstOrNull { it.name == selectedVoice } ?: offlineVoices.first()
                                                if (!offlineInstalled) notice = "Install the offline voice pack from Offline screen."
                                                else {
                                                    if (autoStop) stopPlayback()
                                                    offlineEngine.speak(input, voice.speakerId, speechSpeed, outputVolume, offlineThreads) { s -> runOnUiThread { notice = s } }
                                                }
                                            } else {
                                                val voice = if (provider == "elevenlabs") elevenVoiceId.trim() else selectedVoice
                                                if (voice.isBlank()) notice = "Enter an ElevenLabs voice ID in Voices."
                                                else speakOnline(input, provider, voice, serverUrl, speechSpeed, outputVolume) { s -> runOnUiThread { notice = s } }
                                            }
                                        },
                                        enabled = input.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
                                    ) { Text("GENERATE SPEECH", fontWeight = FontWeight.Bold) }
                                    OutlinedButton(onClick = { stopPlayback(); notice = "Playback stopped." }, modifier = Modifier.fillMaxWidth()) { Text("STOP PLAYBACK") }
                                    StatusCard(notice)
                                }

                                AppScreen.VOICES -> {
                                    if (provider == "offline") {
                                        InfoCard("OFFLINE VOICES", "These voices run on your phone. Download the voice pack from Offline before testing.")
                                        offlineVoices.forEach { voice ->
                                            VoiceRow(voice.name, voice.style, selectedVoice == voice.name, "Preview") {
                                                selectedVoice = voice.name
                                                if (offlineInstalled) {
                                                    stopPlayback()
                                                    offlineEngine.speak("Hello. This is an offline voice test.", voice.speakerId, speechSpeed, outputVolume, offlineThreads) { s -> runOnUiThread { notice = s } }
                                                } else notice = "Download the offline voice pack first."
                                            }
                                        }
                                    } else {
                                    InfoCard("ONLINE VOICE LIBRARY", "Online previews require a deployed speech server URL and a valid provider API key.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModeButton("OpenAI", provider == "openai", Modifier.weight(1f)) { provider = "openai" }
                                        ModeButton("ElevenLabs", provider == "elevenlabs", Modifier.weight(1f)) { provider = "elevenlabs" }
                                    }
                                    if (provider == "openai") {
                                        onlineVoices.forEach { voice ->
                                            VoiceRow(voice.name, voice.style, selectedVoice == voice.voiceId, "Preview") {
                                                selectedVoice = voice.voiceId
                                                prefs.edit().putString("selected_voice", voice.voiceId).apply()
                                                speakOnline("Hello! This is a short preview of the selected AI voice.", "openai", voice.voiceId, serverUrl) { s -> runOnUiThread { notice = s } }
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(value = elevenVoiceId, onValueChange = { elevenVoiceId = it; prefs.edit().putString("eleven_voice_id", it).apply() }, modifier = Modifier.fillMaxWidth(), label = { Text("ElevenLabs voice ID") }, singleLine = true)
                                        Button(onClick = {
                                            if (elevenVoiceId.isBlank()) notice = "Enter a voice ID first."
                                            else speakOnline("Hello! This is your selected ElevenLabs voice.", "elevenlabs", elevenVoiceId.trim(), serverUrl) { s -> runOnUiThread { notice = s } }
                                        }, modifier = Modifier.fillMaxWidth(), enabled = elevenVoiceId.isNotBlank()) { Text("PREVIEW VOICE") }
                                    }
                                    StatusCard(notice)
                                    }
                                }

                                AppScreen.OFFLINE -> {
                                    InfoCard("ON-DEVICE NEURAL ENGINE", "Sherpa-ONNX · Kitten Nano · English only. The model downloads once and runs locally. It is not Android System TTS.")
                                    StatusCard(if (offlineInstalled) "Voice pack installed · offline ready" else "Voice pack not installed")
                                    Button(
                                        onClick = {
                                            if (!installing && !offlineInstalled) {
                                                installing = true
                                                Thread {
                                                    offlineEngine.install { s ->
                                                        runOnUiThread {
                                                            notice = s
                                                            offlineInstalled = offlineEngine.isInstalled()
                                                            if (offlineInstalled || s.startsWith("Offline model install failed")) installing = false
                                                        }
                                                    }
                                                }.start()
                                            }
                                        },
                                        enabled = !installing && !offlineInstalled,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (installing) "DOWNLOADING MODEL…" else if (offlineInstalled) "VOICE PACK INSTALLED" else "DOWNLOAD OFFLINE VOICE PACK") }
                                    Text("OFFLINE SPEAKERS", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    offlineVoices.forEach { voice ->
                                        VoiceRow(voice.name, voice.style, selectedVoice == voice.name, "Preview") {
                                            provider = "offline"
                                            selectedVoice = voice.name
                                            if (offlineInstalled) {
                                                stopPlayback()
                                                offlineEngine.speak("Hello! This is an offline voice sample.", voice.speakerId, speechSpeed, outputVolume, offlineThreads) { s -> runOnUiThread { notice = s } }
                                            } else notice = "Download the voice pack first."
                                        }
                                    }
                                    Text("Language note: these are English speaker IDs, not native Sambalpuri or Odia voices.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                    StatusCard(notice)
                                }

                                AppScreen.SETTINGS -> {
                                    InfoCard("CONNECTION", "Your API keys stay on the hosted server. Do not paste secret keys into this app or commit them to GitHub.")
                                    OutlinedTextField(
                                        value = serverUrl, onValueChange = { serverUrl = it.trim(); prefs.edit().putString("server_url", serverUrl).apply() },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Hosted engine HTTPS URL") },
                                        placeholder = { Text("https://your-engine.example.com") },
                                        singleLine = true
                                    )
                                    Text("Online providers: OpenAI and ElevenLabs. Configure OPENAI_API_KEY and ELEVENLABS_API_KEY on your server.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                    Divider(color = scheme.surfaceVariant)
                                    Text("DEFAULT ENGINE", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModeButton("ONLINE", defaultEngine == "online", Modifier.weight(1f)) { defaultEngine = "online"; provider = "openai"; prefs.edit().putString("default_engine", "online").putString("default_provider", "openai").apply() }
                                        ModeButton("OFFLINE", defaultEngine == "offline", Modifier.weight(1f)) { defaultEngine = "offline"; provider = "offline"; selectedVoice = offlineVoices.first().name; prefs.edit().putString("selected_voice", selectedVoice).putString("default_engine", "offline").apply() }
                                    }
                                    Text("ONLINE PROVIDER", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModeButton("OPENAI", provider == "openai", Modifier.weight(1f)) { provider = "openai"; prefs.edit().putString("default_provider", "openai").apply() }
                                        ModeButton("ELEVENLABS", provider == "elevenlabs", Modifier.weight(1f)) { provider = "elevenlabs"; prefs.edit().putString("default_provider", "elevenlabs").apply() }
                                    }
                                    Text("SPEECH SPEED · " + "%.2f".format(speechSpeed) + "×", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(value = speechSpeed, onValueChange = { speechSpeed = it; prefs.edit().putFloat("speech_speed", it).apply() }, valueRange = 0.5f..2.0f)
                                    Text("OUTPUT VOLUME · " + (outputVolume * 100).toInt() + "%", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(value = outputVolume, onValueChange = { outputVolume = it; prefs.edit().putFloat("output_volume", it).apply() }, valueRange = 0f..1f)
                                    Text("OFFLINE CPU THREADS · " + offlineThreads, color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(value = offlineThreads.toFloat(), onValueChange = { offlineThreads = it.toInt().coerceIn(1, 4); prefs.edit().putInt("offline_threads", offlineThreads).apply() }, valueRange = 1f..4f, steps = 2)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) { Text("Stop previous audio", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("Stop active playback before generating new speech", color = scheme.onSurfaceVariant, fontSize = 11.sp) }
                                        Switch(checked = autoStop, onCheckedChange = { autoStop = it; prefs.edit().putBoolean("stop_before_generate", it).apply() })
                                    }
                                    OutlinedButton(onClick = {
                                        speechSpeed = 1f; outputVolume = 1f; offlineThreads = 2; defaultEngine = "offline"; provider = "offline"; autoStop = true
                                        prefs.edit().putFloat("speech_speed", 1f).putFloat("output_volume", 1f).putInt("offline_threads", 2).putString("default_engine", "offline").putBoolean("stop_before_generate", true).apply()
                                        notice = "Settings reset to defaults."
                                    }, modifier = Modifier.fillMaxWidth()) { Text("RESET SETTINGS") }
                                    Divider(color = scheme.surfaceVariant)
                                    Text("AUDIO ENGINE NOTES", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Text("Offline sample rate is fixed by the Kitten Nano model (24 kHz). Pitch control and native Sambalpuri pronunciation are not supported by this English-only model.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("ABOUT", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Text("Sambalpuri Voice · 0.2.0", color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("No Android System TTS fallback. Offline Kitten Nano is English-only. Native Sambalpuri speech needs a dedicated trained model.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                    OutlinedButton(onClick = { stopPlayback(); notice = "Playback stopped." }, modifier = Modifier.fillMaxWidth()) { Text("STOP ALL PLAYBACK") }
                                    StatusCard(notice)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        onlinePlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        onlinePlayer = null
        if (::offlineEngine.isInitialized) offlineEngine.stop()
    }

    private fun speakOnline(text: String, provider: String, voice: String, baseUrl: String, speed: Float = 1.0f, volume: Float = 1.0f, onStatus: (String) -> Unit) {
        Thread {
            try {
                if (baseUrl.isBlank()) { onStatus("Set your hosted engine URL in Settings first."); return@Thread }
                onStatus("Connecting to $provider…")
                val connection = (URL(baseUrl.trim().trimEnd('/') + "/v1/synthesize").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().put("text", text).put("provider", provider).put("voice", voice).put("speed", speed.coerceIn(0.5f, 2.0f).toDouble()).put("style", "Speak naturally and clearly.").toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Engine HTTP $code: ${error.take(200)}")
                }
                val audioFile = File(cacheDir, "voice-output.mp3")
                connection.inputStream.use { input -> audioFile.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                runOnUiThread {
                    stopPlayback()
                    onlinePlayer = MediaPlayer().apply {
                        setDataSource(audioFile.absolutePath)
                        setOnPreparedListener { it.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f)); it.start(); onStatus("Playing $provider · $voice") }
                        setOnCompletionListener { it.release(); onlinePlayer = null; onStatus("Speech finished.") }
                        setOnErrorListener { player, _, _ -> player.release(); onlinePlayer = null; onStatus("Audio playback failed."); true }
                        prepareAsync()
                    }
                }
            } catch (e: Exception) { onStatus("Online speech failed: ${e.message ?: "check URL, API key and network"}") }
        }.start()
    }

    private fun receiveSharedText(source: Intent?) {
        if (source?.action == Intent.ACTION_SEND && source.type?.startsWith("text/") == true) {
            sharedText = source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedText(intent)
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18221A)), border = BorderStroke(1.dp, Color(0xFF34472A))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color(0xFFB8F36B), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(body, color = Color(0xFFF1F3F5), fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusCard(value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171D23))) {
        Text(value, Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFADB5C0), fontSize = 12.sp)
    }
}

@Composable
private fun VoiceRow(name: String, detail: String, selected: Boolean, actionLabel: String, onAction: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onAction() },
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF202A1A) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(actionLabel, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)) { Text(label, fontSize = 11.sp) }
    else OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)) { Text(label, fontSize = 11.sp) }
}
