# Sambalpuri Voice

An Android-first, free text-to-speech studio. Everything below runs without paid APIs: an offline English voice pack, an offline Odia voice with Sambalpuri-flavoured spelling of English words, and a self-hosted Python engine (IndicF5) for voice-conditioned Odia.

## Implemented modules

### Android
- Kotlin + Jetpack Compose Voice Studio.
- No Android system TextToSpeech dependency or fallback.
- Text input and Android Share target for text received from other apps.
- Online mode UI that calls a server running the Python engine in `engine/` (provider/voice pickers are ignored by the new engine).
- Offline mode powered by Sherpa-ONNX.
- Downloadable Kitten Nano English model with eight selectable speaker IDs, the required English pronunciation lexicon, and chunk-by-chunk AudioTrack playback during synthesis.
- MP3 playback for online generated speech.

### Python engine (`engine/`)
Pipeline: text normalization -> Odia-script transliteration (lexicon + rules, optional IndicXlit) -> IndicF5 conditioned on your reference clip -> WAV/MP3.
- Free and self-hosted; needs a Hugging Face account to accept the gated IndicF5 terms. See [engine/README.md](engine/README.md).
- Optional FastAPI server (`POST /v1/synthesize`). The old OpenAI/ElevenLabs gateway was removed, so the app's Online mode needs a server running this engine and its provider/voice pickers are ignored.
- IndicF5 is heavy; use a GPU machine or Google Colab free tier. It is not suitable for free web hosting tiers.

### Offline engine
- **Odia offline mode (new):** Sherpa-ONNX + Meta MMS Odia voice (~114 MB, CC-BY-NC, non-commercial). `SambTextProcessor` (a Kotlin port of `engine/textproc.py`) normalizes numbers/symbols and respells English words in Odia script using `SambLexicon` plus spelling rules. The voice is one standard Odia voice, not a Sambalpuri speaker. The pack is downloaded from a third-party Hugging Face dataset (`Mr66/itantra-packs`).
- Sherpa-ONNX 2.0.1 Android runtime is downloaded and checksum-verified by GitHub Actions.
- Kitten Nano English voice pack and the separate `lexicon-en-us.txt` pronunciation dictionary are downloaded from official Sherpa-ONNX release assets when the user taps **Download Offline Voice Pack**.
- Speech samples are sent to Android `AudioTrack` from Sherpa-ONNX `generateWithCallback`, rather than waiting for the entire utterance to finish before playback.
- Eight English speakers are available after the pack is installed.
- Model files are downloaded separately and are not bundled into the APK.

**Language limitation:** The Kitten model is English-only. The Odia modes approximate Sambalpuri through spelling and, for the Python engine, a reference voice. There is no trained native Sambalpuri model; do not label any output as native Sambalpuri.

## Build APK

1. Open the **Actions** tab.
2. Select **Build Android APK**.
3. Wait for the build to complete successfully.
4. Download the `sambalpuri-voice-debug` artifact.

The workflow downloads the official Sherpa-ONNX AAR, verifies its SHA-256 checksum, then builds the Android APK.

## Project layout

- `app/` — Android application and offline TTS runtime integration.
- `engine/` — Python Odia/Sambalpuri-flavour TTS pipeline and optional FastAPI server.
- `render.yaml` — optional hosting blueprint (needs a paid, high-RAM instance for IndicF5).
- `.github/workflows/` — Android CI build.

## Native Sambalpuri voice roadmap

A genuine native Sambalpuri model still requires:
1. Consented recordings from native Sambalpuri speakers.
2. Accurate aligned transcripts covering regional vocabulary and conversational speech.
3. A pronunciation lexicon and phoneme/grapheme-to-phoneme rules.
4. Model training or adaptation and a properly documented licence.
5. Evaluation by native speakers across Western Odisha dialects.

Never use a person's voice recordings for cloning without explicit informed consent.
