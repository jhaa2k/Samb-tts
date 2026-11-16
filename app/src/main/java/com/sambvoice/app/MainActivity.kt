package com.sambvoice.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private data class VoiceProfile(
    val name: String,
    val style: String,
    val tone: String,
    val mode: EngineMode,
    val voiceId: String = "",
    val speakerId: Int = 0
)

private val voiceProfiles = listOf(
    VoiceProfile("Alloy", "Balanced and versatile", "OpenAI voice", EngineMode.ONLINE, "alloy"),
    VoiceProfile("Ash", "Clear conversational", "OpenAI voice", EngineMode.ONLINE, "ash"),
    VoiceProfile("Ballad", "Expressive narration", "OpenAI voice", EngineMode.ONLINE, "ballad"),
    VoiceProfile("Coral", "Warm and friendly", "OpenAI voice", EngineMode.ONLINE, "coral"),
    VoiceProfile("Echo", "Smooth delivery", "OpenAI voice", EngineMode.ONLINE, "echo"),
    VoiceProfile("Fable", "Storytelling", "OpenAI voice", EngineMode.ONLINE, "fable"),
    VoiceProfile("Nova", "Bright and lively", "OpenAI voice", EngineMode.ONLINE, "nova"),
    VoiceProfile("Onyx", "Deep narration", "OpenAI voice", EngineMode.ONLINE, "onyx"),
    VoiceProfile("Sage", "Measured and calm", "OpenAI voice", EngineMode.ONLINE, "sage"),
    VoiceProfile("Shimmer", "Soft and clear", "OpenAI voice", EngineMode.ONLINE, "shimmer"),
    VoiceProfile("Verse", "Expressive", "OpenAI voice", EngineMode.ONLINE, "verse"),
    VoiceProfile("Marin", "Natural studio voice", "OpenAI voice", EngineMode.ONLINE, "marin"),
    VoiceProfile("Cedar", "Natural studio voice", "OpenAI voice", EngineMode.ONLINE, "cedar"),
    VoiceProfile("Expr 2 Male", "English neural voice", "Offline · Male", EngineMode.OFFLINE, speakerId = 0),
    VoiceProfile("Expr 2 Female", "English neural voice", "Offline · Female", EngineMode.OFFLINE, speakerId = 1),
    VoiceProfile("Expr 3 Male", "English neural voice", "Offline · Male", EngineMode.OFFLINE, speakerId = 2),
    VoiceProfile("Expr 3 Female", "English neural voice", "Offline · Female", EngineMode.OFFLINE, speakerId = 3),
    VoiceProfile("Expr 4 Male", "English neural voice", "Offline · Male", EngineMode.OFFLINE, speakerId = 4),
    VoiceProfile("Expr 4 Female", "English neural voice", "Offline · Female", EngineMode.OFFLINE, speakerId = 5),
    VoiceProfile("Expr 5 Male", "English neural voice", "Offline · Male", EngineMode.OFFLINE, speakerId = 6),
    VoiceProfile("Expr 5 Female", "English neural voice", "Offline · Female", EngineMode.OFFLINE, speakerId = 7)
)

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf("")
    private lateinit var offlineEngine: OfflineKittenEngine
    private var onlinePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)
        offlineEngine = OfflineKittenEngine(this)

        setContent {
            var input by remember { mutableStateOf(sharedText) }
            LaunchedEffect(sharedText) { if (sharedText.isNotBlank()) input = sharedText }
            var engineMode by remember { mutableStateOf(EngineMode.ONLINE) }
            var selectedVoice by remember { mutableStateOf("coral") }
            var provider by remember { mutableStateOf("openai") }
            var elevenVoiceId by remember { mutableStateOf("") }
            var serverUrl by remember { mutableStateOf("") }
            var notice by remember { mutableStateOf("Online provider needs a hosted engine URL and server API key.") }
            var showInfo by remember { mutableStateOf(false) }
            var offlineInstalled by remember { mutableStateOf(offlineEngine.isInstalled()) }
            var installing by remember { mutableStateOf(false) }

            val colors = darkColorScheme(
                primary = Color(0xFFB8F36B),
                onPrimary = Color(0xFF17210B),
                secondary = Color(0xFF9CA6B5),
                background = Color(0xFF0B0D10),
                surface = Color(0xFF15191F),
                surfaceVariant = Color(0xFF202630),
                onSurface = Color(0xFFF1F3F5),
                onSurfaceVariant = Color(0xFFADB5C0)
            )

            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("SAMBALPURI VOICE", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Text("Voice Studio", color = colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { showInfo = true }) { Text("About") }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18221A)), border = BorderStroke(1.dp, Color(0xFF34472A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("YOUR VOICE WORKSPACE", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                                Text("Write naturally. Choose a voice. Make it yours.", color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("Cloud AI voices plus a downloadable on-device neural English engine. No Android system TTS.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }

                        SectionTitle("01  TEXT INPUT")
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            label = { Text("Your text") },
                            placeholder = { Text("Type or paste English, Odia or Sambalpuri…") },
                            minLines = 5
                        )
                        OutlinedButton(onClick = { input = "" }, modifier = Modifier.fillMaxWidth()) { Text("Clear text") }

                        SectionTitle("02  SPEECH ENGINE")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeButton("ONLINE AI", engineMode == EngineMode.ONLINE, Modifier.weight(1f)) { engineMode = EngineMode.ONLINE }
                            ModeButton("OFFLINE AI", engineMode == EngineMode.OFFLINE, Modifier.weight(1f)) { engineMode = EngineMode.OFFLINE }
                        }

                        if (engineMode == EngineMode.ONLINE) {
                            Card(colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, colors.surfaceVariant)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("ONLINE PROVIDER", color = colors.primary, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModeButton("OpenAI", provider == "openai", Modifier.weight(1f)) { provider = "openai" }
                                        ModeButton("ElevenLabs", provider == "elevenlabs", Modifier.weight(1f)) { provider = "elevenlabs" }
                                    }
                                    OutlinedTextField(
                                        value = serverUrl,
                                        onValueChange = { serverUrl = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Your hosted engine URL") },
                                        placeholder = { Text("https://your-engine.example.com") },
                                        singleLine = true
                                    )
                                    if (provider == "elevenlabs") {
                                        OutlinedTextField(
                                            value = elevenVoiceId,
                                            onValueChange = { elevenVoiceId = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("ElevenLabs voice ID") },
                                            singleLine = true
                                        )
                                    }
                                    Text("API keys must be configured on your server. Never paste secret keys into this app or commit them to GitHub.", color = colors.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                            SectionTitle("03  ONLINE VOICE LIBRARY")
                            Text("13 OpenAI voice IDs are available through the configured backend. ElevenLabs uses a voice ID from your account.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                            voiceProfiles.filter { it.mode == EngineMode.ONLINE }.forEach { profile ->
                                val selected = selectedVoice == profile.voiceId
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { selectedVoice = profile.voiceId },
                                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF202A1A) else colors.surface),
                                    border = BorderStroke(1.dp, if (selected) colors.primary else colors.surfaceVariant)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(profile.name, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                            Text(profile.style, color = colors.onSurfaceVariant, fontSize = 13.sp)
                                        }
                                        Text(if (selected) "SELECTED" else "SELECT", color = if (selected) colors.primary else colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Card(colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, colors.surfaceVariant)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("SHERPA-ONNX OFFLINE ENGINE", color = colors.primary, fontWeight = FontWeight.Bold)
                                    Text("Kitten Nano English neural model • 8 distinct speakers • Runs locally after model download.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                                    Text(if (offlineInstalled) "Installed • 8 offline voices ready" else "Not installed • Download required", color = if (offlineInstalled) colors.primary else Color(0xFFFFC56D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Button(
                                        onClick = {
                                            if (!installing && !offlineInstalled) {
                                                installing = true
                                                Thread {
                                                    offlineEngine.install { status ->
                                                        runOnUiThread {
                                                            notice = status
                                                            offlineInstalled = offlineEngine.isInstalled()
                                                            if (offlineInstalled || status.startsWith("Offline model install failed")) installing = false
                                                        }
                                                    }
                                                }.start()
                                            }
                                        },
                                        enabled = !installing && !offlineInstalled,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (installing) "INSTALLING VOICE PACK…" else if (offlineInstalled) "VOICE PACK INSTALLED" else "DOWNLOAD OFFLINE VOICE PACK") }
                                }
                            }
                            SectionTitle("03  OFFLINE VOICE LIBRARY")
                            Text("Eight English speakers from one downloaded neural model. This pack does not speak Sambalpuri or Odia.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                            voiceProfiles.filter { it.mode == EngineMode.OFFLINE }.forEach { profile ->
                                val selected = selectedVoice == profile.name
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { selectedVoice = profile.name },
                                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF202A1A) else colors.surface),
                                    border = BorderStroke(1.dp, if (selected) colors.primary else colors.surfaceVariant)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(profile.name, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(profile.style, color = colors.onSurfaceVariant, fontSize = 12.sp)
                                            Text(profile.tone, color = colors.primary, fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                if (offlineInstalled) offlineEngine.speak(
                                                    "Hello! This is a sample of the selected offline neural voice.",
                                                    profile.speakerId
                                                ) { status -> runOnUiThread { notice = status } }
                                                else notice = "Download the offline voice pack first."
                                            },
                                            enabled = offlineInstalled
                                        ) { Text("Preview") }
                                    }
                                }
                            }
                        }

                        SectionTitle("04  GENERATION")
                        Button(
                            onClick = {
                                if (engineMode == EngineMode.OFFLINE) {
                                    val profile = voiceProfiles.firstOrNull { it.mode == EngineMode.OFFLINE && it.name == selectedVoice }
                                    if (profile == null) notice = "Select an offline voice first."
                                    else offlineEngine.speak(input, profile.speakerId) { status -> runOnUiThread { notice = status } }
                                } else {
                                    val voice = if (provider == "openai") selectedVoice else elevenVoiceId.trim()
                                    if (voice.isBlank()) notice = "Enter a valid ElevenLabs voice ID."
                                    else speakOnline(input, provider, voice, serverUrl.trim()) { status -> runOnUiThread { notice = status } }
                                }
                            },
                            enabled = input.isNotBlank() && (engineMode == EngineMode.ONLINE || offlineInstalled),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                        ) { Text("GENERATE SPEECH", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                onlinePlayer?.stop()
                                onlinePlayer?.release()
                                onlinePlayer = null
                                notice = "Playback stopped."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("STOP PLAYBACK") }
                        Text(notice, color = colors.onSurfaceVariant, fontSize = 12.sp)
                        Text("SHARE TEXT", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Use Share → Sambalpuri Voice from Instagram or another app to load selected text here.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("SAMBALPURI VOICE  •  VOICE STUDIO", color = colors.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
            }

            if (showInfo) {
                AlertDialog(
                    onDismissRequest = { showInfo = false },
                    title = { Text("About Voice Studio") },
                    text = { Text("Offline mode uses Sherpa-ONNX with an English-only Kitten Nano model (8 speakers). Online mode connects to your hosted FastAPI gateway, which requires your own OpenAI or ElevenLabs server API key. No Android system TTS is used. Native Sambalpuri speech still needs a dedicated trained model.") },
                    confirmButton = { Button(onClick = { showInfo = false }) { Text("Got it") } }
                )
            }
        }
    }

    private fun speakOnline(text: String, provider: String, voice: String, baseUrl: String, onStatus: (String) -> Unit) {
        Thread {
            try {
                if (baseUrl.isBlank()) {
                    onStatus("Add your deployed engine URL first. Online provider keys are configured on that server.")
                    return@Thread
                }
                onStatus("Connecting to $provider…")
                val endpoint = baseUrl.trimEnd('/') + "/v1/synthesize"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject()
                    .put("text", text)
                    .put("provider", provider)
                    .put("voice", voice)
                    .put("speed", 1.0)
                    .put("style", "Speak naturally and clearly.")
                    .toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Engine HTTP $code: ${error.take(240)}")
                }
                val audioFile = File(cacheDir, "voice-output.mp3")
                connection.inputStream.use { input -> audioFile.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                runOnUiThread {
                    onlinePlayer?.release()
                    onlinePlayer = MediaPlayer().apply {
                        setDataSource(audioFile.absolutePath)
                        setOnPreparedListener {
                            it.start()
                            onStatus("Playing online $provider voice: $voice")
                        }
                        setOnCompletionListener {
                            it.release()
                            onlinePlayer = null
                            onStatus("Online speech finished.")
                        }
                        setOnErrorListener { player, _, _ ->
                            player.release()
                            onlinePlayer = null
                            onStatus("Could not play the generated audio.")
                            true
                        }
                        prepareAsync()
                    }
                }
            } catch (error: Exception) {
                onStatus("Online speech failed: ${error.message ?: "check server URL, provider key and network"}")
            }
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
        onlinePlayer?.release()
        onlinePlayer = null
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun SectionTitle(value: String) {
    Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@androidx.compose.runtime.Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
