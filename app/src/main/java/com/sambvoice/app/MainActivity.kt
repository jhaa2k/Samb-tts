package com.sambvoice.app

import android.content.Intent
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class EngineMode { ONLINE, OFFLINE }
private data class VoiceProfile(
    val name: String,
    val style: String,
    val tone: String,
    val mode: EngineMode
)

private val voiceProfiles = listOf(
    VoiceProfile("Aarav", "Natural conversational", "Male · Warm", EngineMode.ONLINE),
    VoiceProfile("Ishita", "Clear conversational", "Female · Calm", EngineMode.ONLINE),
    VoiceProfile("Rudra", "Deep narrator", "Male · Deep", EngineMode.ONLINE),
    VoiceProfile("Meera", "Soft storyteller", "Female · Soft", EngineMode.ONLINE),
    VoiceProfile("Veer", "Confident presenter", "Male · Bold", EngineMode.ONLINE),
    VoiceProfile("Ananya", "Bright expressive", "Female · Bright", EngineMode.ONLINE),
    VoiceProfile("Classic", "Balanced standard", "Neutral", EngineMode.ONLINE),
    VoiceProfile("Studio", "Polished narration", "Neutral · Studio", EngineMode.ONLINE),
    VoiceProfile("Local Natural", "On-device natural", "Offline pack", EngineMode.OFFLINE),
    VoiceProfile("Local Deep", "Low-pitch narration", "Offline pack", EngineMode.OFFLINE),
    VoiceProfile("Local Soft", "Gentle reading", "Offline pack", EngineMode.OFFLINE),
    VoiceProfile("Local Clear", "Crisp pronunciation", "Offline pack", EngineMode.OFFLINE)
)

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)

        setContent {
            var input by remember { mutableStateOf(sharedText) }
            var engineMode by remember { mutableStateOf(EngineMode.ONLINE) }
            var selectedVoice by remember { mutableStateOf("Aarav") }
            var notice by remember { mutableStateOf("Voice engines are not connected yet.") }
            var showInfo by remember { mutableStateOf(false) }

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
                                Text("Online AI and downloadable offline voice engines — without Android system TTS.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }

                        SectionTitle("01  TEXT INPUT")
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            label = { Text("Your text") },
                            placeholder = { Text("Paste or type English, Odia or Sambalpuri…") },
                            minLines = 5
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { input = "" }, modifier = Modifier.weight(1f)) { Text("Clear text") }
                            OutlinedButton(onClick = { notice = "Text is ready. Connect a speech engine to generate audio." }, modifier = Modifier.weight(1f)) { Text("Prepare text") }
                        }

                        SectionTitle("02  SPEECH ENGINE")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeButton("ONLINE AI", engineMode == EngineMode.ONLINE, Modifier.weight(1f)) { engineMode = EngineMode.ONLINE }
                            ModeButton("OFFLINE AI", engineMode == EngineMode.OFFLINE, Modifier.weight(1f)) { engineMode = EngineMode.OFFLINE }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, colors.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (engineMode == EngineMode.ONLINE) "ONLINE ENGINE" else "OFFLINE VOICE PACKS", color = colors.primary, fontWeight = FontWeight.Bold)
                                Text(
                                    if (engineMode == EngineMode.ONLINE) "Cloud synthesis • Natural voice models • Requires an API provider and internet connection"
                                    else "On-device inference • Downloadable models • Works without internet after a compatible voice pack is installed",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                Text(if (engineMode == EngineMode.ONLINE) "Status: Not connected" else "Status: No offline model installed", color = Color(0xFFFFC56D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        SectionTitle(if (engineMode == EngineMode.ONLINE) "03  ONLINE VOICE PROFILES" else "03  OFFLINE VOICE PACKS")
                        Text(
                            if (engineMode == EngineMode.ONLINE) "Choose a voice character. Real voice previews become available after connecting a speech provider."
                            else "Choose an offline profile. A compatible downloadable model is required before playback.",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        voiceProfiles.filter { it.mode == engineMode }.forEach { profile ->
                            val selected = selectedVoice == profile.name
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedVoice = profile.name },
                                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF202A1A) else colors.surface),
                                border = BorderStroke(1.dp, if (selected) colors.primary else colors.surfaceVariant)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(profile.name, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        Text(profile.style, color = colors.onSurfaceVariant, fontSize = 13.sp)
                                        Text(profile.tone, color = colors.primary, fontSize = 11.sp)
                                    }
                                    Text(if (selected) "SELECTED" else "SELECT", color = if (selected) colors.primary else colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        SectionTitle("04  VOICE SETTINGS")
                        Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Pronunciation • Speed • Pitch • Emotion", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("These controls will be enabled by the connected voice model. Voice identity and accent depend on the selected model, not just pitch settings.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = { notice = "Cannot generate audio yet: connect an online AI provider or install a compatible offline model first." },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                        ) { Text("GENERATE SPEECH", fontWeight = FontWeight.Bold) }
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
                    text = { Text("This build contains the refreshed studio interface and voice profile selection. Cloud synthesis and offline model inference still need real, licensed voice models and provider configuration. It does not use Android system TTS.") },
                    confirmButton = { Button(onClick = { showInfo = false }) { Text("Got it") } }
                )
            }
        }
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
