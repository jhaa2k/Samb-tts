package com.sambvoice.app

import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private enum class ReadingMode { SAMBALPURI_TEXT, ENGLISH_SOUND }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var voiceReady = false
    private var voiceMessage by mutableStateOf("Checking available Odia device voice…")
    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)
        tts = TextToSpeech(this, this)

        setContent {
            var input by remember { mutableStateOf(sharedText.ifBlank { "ମୁଇଁ ସମ୍ବଲପୁରୀ ଭାଷାରେ କଥା ହେମି।" }) }
            var mode by remember { mutableStateOf(ReadingMode.SAMBALPURI_TEXT) }
            var pronunciation by remember { mutableStateOf("") }
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("SAMBALPURI VOICE", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Text Reader", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Read Sambalpuri text or prepare an English message for Sambalpuri-style pronunciation.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mode == ReadingMode.SAMBALPURI_TEXT) {
                                Button(onClick = { mode = ReadingMode.SAMBALPURI_TEXT; pronunciation = "" },
                                    modifier = Modifier.weight(1f)) { Text("Sambalpuri") }
                            } else {
                                OutlinedButton(onClick = { mode = ReadingMode.SAMBALPURI_TEXT; pronunciation = "" },
                                    modifier = Modifier.weight(1f)) { Text("Sambalpuri") }
                            }
                            if (mode == ReadingMode.ENGLISH_SOUND) {
                                Button(onClick = { mode = ReadingMode.ENGLISH_SOUND; pronunciation = EnglishSoundGuide.convert(input) },
                                    modifier = Modifier.weight(1f)) { Text("English → Sound") }
                            } else {
                                OutlinedButton(onClick = { mode = ReadingMode.ENGLISH_SOUND; pronunciation = EnglishSoundGuide.convert(input) },
                                    modifier = Modifier.weight(1f)) { Text("English → Sound") }
                            }
                        }

                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                if (mode == ReadingMode.ENGLISH_SOUND) pronunciation = EnglishSoundGuide.convert(it)
                            },
                            modifier = Modifier.fillMaxWidth().height(190.dp),
                            label = { Text(if (mode == ReadingMode.ENGLISH_SOUND) "English message" else "Sambalpuri text") },
                            placeholder = { Text("Type or paste text here…") },
                            minLines = 5
                        )

                        if (mode == ReadingMode.ENGLISH_SOUND) {
                            Text("Sambalpuri-style sound guide", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = pronunciation,
                                onValueChange = { pronunciation = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Pronunciation preview (editable)") },
                                minLines = 3
                            )
                            Text(
                                "This starter sound guide recognises common English words and phrases. Unknown words may need manual correction. A trained native Sambalpuri voice is required for fully natural pronunciation.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val textToRead = if (mode == ReadingMode.ENGLISH_SOUND) pronunciation else input
                                    speak(textToRead)
                                },
                                enabled = (if (mode == ReadingMode.ENGLISH_SOUND) pronunciation else input).isNotBlank() && voiceReady,
                                modifier = Modifier.weight(1f)
                            ) { Text("Read aloud") }
                            OutlinedButton(onClick = { tts?.stop() }, modifier = Modifier.weight(1f)) {
                                Text("Stop")
                            }
                        }

                        Text(voiceMessage, style = MaterialTheme.typography.bodySmall)
                        Text("Share text from other apps", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use Share → Sambalpuri Voice from an app that supports text sharing. The selected text will appear here.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Voice status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This build uses the Odia voice installed on your phone as a temporary playback engine. It is not yet a trained native Sambalpuri voice.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun receiveSharedText(source: Intent?) {
        if (source?.action == Intent.ACTION_SEND && source.type == "text/plain") {
            sharedText = source.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedText(intent)
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
            "Odia device voice available. Native Sambalpuri voice is not connected yet."
        } else {
            "No compatible Odia voice is installed. Install one in Android text-to-speech settings."
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

private object EnglishSoundGuide {
    private val words = mapOf(
        "hello" to "ହେଲୋ",
        "hi" to "ହାଏ",
        "hey" to "ହେ",
        "good" to "ଗୁଡ୍",
        "morning" to "ମର୍ନିଂ",
        "afternoon" to "ଆଫ୍ଟରନୁନ୍",
        "evening" to "ଇଭନିଂ",
        "night" to "ନାଇଟ୍",
        "how" to "ହାଉ",
        "are" to "ଆର୍",
        "you" to "ୟୁ",
        "what" to "ୱଟ୍",
        "is" to "ଇଜ୍",
        "your" to "ୟୋର୍",
        "name" to "ନେମ୍",
        "my" to "ମାଇ",
        "i" to "ଆଇ",
        "am" to "ଏମ୍",
        "fine" to "ଫାଇନ୍",
        "thanks" to "ଥ୍ୟାଙ୍କ୍ସ",
        "thank" to "ଥ୍ୟାଙ୍କ୍",
        "please" to "ପ୍ଲିଜ୍",
        "sorry" to "ସରି",
        "yes" to "ୟେସ୍",
        "no" to "ନୋ",
        "where" to "ୱେର୍",
        "when" to "ୱେନ୍",
        "why" to "ୱାଇ",
        "who" to "ହୁ",
        "doing" to "ଡୁଇଂ",
        "do" to "ଡୁ",
        "come" to "କମ୍",
        "going" to "ଗୋଇଂ",
        "go" to "ଗୋ",
        "today" to "ଟୁଡେ",
        "tomorrow" to "ଟୁମାରୋ",
        "love" to "ଲଭ୍",
        "friend" to "ଫ୍ରେଣ୍ଡ୍",
        "message" to "ମେସେଜ୍",
        "read" to "ରିଡ୍",
        "instagram" to "ଇନଷ୍ଟାଗ୍ରାମ୍"
    )

    fun convert(text: String): String {
        val tokenRegex = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[^A-Za-z]+")
        return tokenRegex.findAll(text).joinToString("") { match ->
            val token = match.value
            if (token.firstOrNull()?.isLetter() == true) {
                words[token.lowercase(Locale.ROOT)] ?: token
            } else token
        }
    }
}
