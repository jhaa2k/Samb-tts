package com.sambvoice.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var voiceReady = false
    private var voiceMessage by mutableStateOf("Checking available device voice…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        setContent {
            var input by mutableStateOf("ମୁଇଁ ସମ୍ବଲପୁରୀ ଭାଷାରେ କଥା ହେମି।")
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("SAMBALPURI VOICE", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Text Reader", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Paste or type Sambalpuri text and listen. Native Sambalpuri voice support is being developed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                            label = { Text("Your text") },
                            placeholder = { Text("ଏଠାରେ ଲେଖନ୍ତୁ…") },
                            minLines = 6
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { speak(input) },
                                enabled = input.isNotBlank() && voiceReady,
                                modifier = Modifier.weight(1f)
                            ) { Text("Read aloud") }
                            OutlinedButton(
                                onClick = { tts?.stop() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }
                        Text(voiceMessage, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Voice quality notice",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "This first build uses your phone’s installed Odia voice only when available. Odia system speech is not the same as native Sambalpuri. Accurate regional pronunciation needs a dedicated Sambalpuri voice model and verified speaker recordings.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Sambalpuri AI voice engine · Early development",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            voiceReady = false
            voiceMessage = "Text-to-speech could not start on this device."
            return
        }
        val result = tts?.setLanguage(Locale("or", "IN")) ?: LANG_NOT_SUPPORTED
        voiceReady = result != LANG_MISSING_DATA && result != LANG_NOT_SUPPORTED
        voiceMessage = if (voiceReady) {
            "Temporary device voice available (Odia). Native Sambalpuri voice is not connected yet."
        } else {
            "No compatible Odia voice is installed. Install a supported voice in Android text-to-speech settings."
        }
    }

    private fun speak(text: String) {
        if (text.isNotBlank() && voiceReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sambalpuri-reader")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
