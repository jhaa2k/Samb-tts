package com.sambvoice.app

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
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
    private var voiceMessage by mutableStateOf("Starting your phone's speech engine…")
    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)
        tts = TextToSpeech(this, this)

        setContent {
            var input by remember { mutableStateOf(sharedText.ifBlank { "ମୁଇଁ ସମ୍ବଲପୁରୀ ଭାଷାରେ କଥା ହେମି।" }) }
            var mode by remember { mutableStateOf(ReadingMode.SAMBALPURI_TEXT) }
            var pronunciation by remember { mutableStateOf("") }
            LaunchedEffect(sharedText) {
                if (sharedText.isNotBlank()) {
                    input = sharedText
                    mode = if (sharedText.any { it.code in 0x0B00..0x0B7F }) ReadingMode.SAMBALPURI_TEXT
                    else ReadingMode.ENGLISH_SOUND
                    pronunciation = EnglishSoundGuide.convert(sharedText)
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("SAMBALPURI VOICE", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Text Reader", style = MaterialTheme.typography.headlineLarge)
                        Text("Type or paste any text. Press Read aloud to hear the original text, or choose the sound guide for English messages.",
                            style = MaterialTheme.typography.bodyMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mode == ReadingMode.SAMBALPURI_TEXT) {
                                Button(onClick = { mode = ReadingMode.SAMBALPURI_TEXT }, modifier = Modifier.weight(1f)) { Text("Read as typed") }
                            } else {
                                OutlinedButton(onClick = { mode = ReadingMode.SAMBALPURI_TEXT }, modifier = Modifier.weight(1f)) { Text("Read as typed") }
                            }
                            if (mode == ReadingMode.ENGLISH_SOUND) {
                                Button(onClick = { mode = ReadingMode.ENGLISH_SOUND; pronunciation = EnglishSoundGuide.convert(input) },
                                    modifier = Modifier.weight(1f)) { Text("Sambalpuri sound") }
                            } else {
                                OutlinedButton(onClick = { mode = ReadingMode.ENGLISH_SOUND; pronunciation = EnglishSoundGuide.convert(input) },
                                    modifier = Modifier.weight(1f)) { Text("Sambalpuri sound") }
                            }
                        }

                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                if (mode == ReadingMode.ENGLISH_SOUND) pronunciation = EnglishSoundGuide.convert(it)
                            },
                            modifier = Modifier.fillMaxWidth().height(190.dp),
                            label = { Text("Type or paste your text") },
                            placeholder = { Text("Write English or Sambalpuri here…") },
                            minLines = 5
                        )

                        if (mode == ReadingMode.ENGLISH_SOUND) {
                            Text("Pronunciation preview", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = pronunciation,
                                onValueChange = { pronunciation = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Editable sound guide") },
                                minLines = 3
                            )
                            Text("This is a starter word-based pronunciation helper, not a complete Sambalpuri phonetic model. Unknown words stay as typed.",
                                style = MaterialTheme.typography.bodySmall)
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
                            OutlinedButton(onClick = { tts?.stop(); voiceMessage = "Playback stopped." },
                                modifier = Modifier.weight(1f)) { Text("Stop") }
                        }

                        Text(voiceMessage, style = MaterialTheme.typography.bodySmall)
                        Text("Share from Instagram or another app", style = MaterialTheme.typography.titleMedium)
                        Text("Select text in the other app → Share → Sambalpuri Voice. The shared text will load here.",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Voice note", style = MaterialTheme.typography.titleMedium)
                        Text("Playback uses speech voices installed on your phone. If an Odia voice is unavailable, the app falls back to your phone's default voice so the Read button still works. Natural native Sambalpuri speech needs a dedicated trained voice model.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
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

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            voiceReady = false
            voiceMessage = "Speech engine failed to start. Check that a Text-to-speech engine is installed in Android settings."
            return
        }
        tts?.setLanguage(Locale.getDefault())
        tts?.setSpeechRate(0.92f)
        tts?.setPitch(1.0f)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { voiceMessage = "Speaking…" }
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread { voiceMessage = "Finished reading." }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { voiceMessage = "Could not read this text. Try another installed voice in Android TTS settings." }
            }
        })
        voiceReady = true
        voiceMessage = "Speech engine ready. The app will try an appropriate installed voice and fall back to your phone's default voice."
    }

    private fun speak(text: String) {
        val engine = tts
        if (text.isBlank()) {
            voiceMessage = "Please type or paste some text first."
            return
        }
        if (!voiceReady || engine == null) {
            voiceMessage = "Speech engine is not ready yet. Wait a moment and try again."
            return
        }
        val containsOdiaScript = text.any { it.code in 0x0B00..0x0B7F }
        val preferredLocale = if (containsOdiaScript) Locale("or", "IN") else Locale("en", "IN")
        val languageResult = engine.setLanguage(preferredLocale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
            voiceMessage = "Preferred language voice unavailable; trying your phone's default voice."
        } else {
            voiceMessage = if (containsOdiaScript)
                "Reading with installed Odia voice. Native Sambalpuri voice is not installed yet."
            else "Reading with installed English voice."
        }
        engine.stop()
        val chunks = splitForSpeech(text, TextToSpeech.getMaxSpeechInputLength())
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(chunk, queueMode, null, "sambalpuri-reader-$index")
            if (result == TextToSpeech.ERROR) {
                voiceMessage = "Your phone's speech engine rejected this text. Try a shorter passage or another TTS voice."
            }
        }
    }

    private fun splitForSpeech(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > limit) {
            var cut = remaining.lastIndexOf(' ', limit)
            if (cut < limit / 2) cut = limit
            result.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotBlank()) result.add(remaining)
        return result
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
        "hello" to "ହେଲୋ", "hi" to "ହାଏ", "hey" to "ହେ",
        "good" to "ଗୁଡ୍", "morning" to "ମର୍ନିଂ", "afternoon" to "ଆଫ୍ଟରନୁନ୍",
        "evening" to "ଇଭନିଂ", "night" to "ନାଇଟ୍", "how" to "ହାଉ",
        "are" to "ଆର୍", "you" to "ୟୁ", "what" to "ୱଟ୍", "is" to "ଇଜ୍",
        "your" to "ୟୋର୍", "name" to "ନେମ୍", "my" to "ମାଇ", "i" to "ଆଇ",
        "am" to "ଏମ୍", "fine" to "ଫାଇନ୍", "thanks" to "ଥ୍ୟାଙ୍କ୍ସ",
        "thank" to "ଥ୍ୟାଙ୍କ୍", "please" to "ପ୍ଲିଜ୍", "sorry" to "ସରି",
        "yes" to "ୟେସ୍", "no" to "ନୋ", "where" to "ୱେର୍", "when" to "ୱେନ୍",
        "why" to "ୱାଇ", "who" to "ହୁ", "doing" to "ଡୁଇଂ", "do" to "ଡୁ",
        "come" to "କମ୍", "going" to "ଗୋଇଂ", "go" to "ଗୋ",
        "today" to "ଟୁଡେ", "tomorrow" to "ଟୁମାରୋ", "love" to "ଲଭ୍",
        "friend" to "ଫ୍ରେଣ୍ଡ୍", "message" to "ମେସେଜ୍", "read" to "ରିଡ୍",
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
