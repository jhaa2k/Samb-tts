# Sambalpuri Voice

An Android-first AI text-to-speech studio with online provider adapters and an offline neural English voice pack.

## Implemented modules

### Android
- Kotlin + Jetpack Compose Voice Studio.
- No Android system TextToSpeech dependency or fallback.
- Text input and Android Share target for text received from other apps.
- Online mode UI for OpenAI and ElevenLabs through the project's hosted API gateway.
- Offline mode powered by Sherpa-ONNX.
- Downloadable Kitten Nano English model with eight selectable speaker IDs, the required English pronunciation lexicon, and chunk-by-chunk AudioTrack playback during synthesis.
- MP3 playback for online generated speech.

### Online engine
The FastAPI gateway supports:
- OpenAI speech synthesis with 13 documented built-in voice IDs.
- ElevenLabs account voice listing and synthesis.
- Server-side API keys only.
- MP3 response playback in the Android app.

The online service is not automatically hosted. Deploy the included `render.yaml` blueprint, set provider keys as secret environment variables, then paste the service HTTPS URL into the app. Provider use can incur charges.

### Offline engine
- Sherpa-ONNX 2.0.1 Android runtime is downloaded and checksum-verified by GitHub Actions.
- Kitten Nano English voice pack and the separate `lexicon-en-us.txt` pronunciation dictionary are downloaded from official Sherpa-ONNX release assets when the user taps **Download Offline Voice Pack**.
- Speech samples are sent to Android `AudioTrack` from Sherpa-ONNX `generateWithCallback`, rather than waiting for the entire utterance to finish before playback.
- Eight English speakers are available after the pack is installed.
- Model files are downloaded separately and are not bundled into the APK.

**Language limitation:** The installed offline Kitten model is English-only. The online providers also do not guarantee native Sambalpuri pronunciation. This repository does not yet contain a trained Sambalpuri or Odia voice model. Do not label the English model as Sambalpuri.

## Online engine setup

See [engine/README.md](engine/README.md). Provider credentials must be configured on the server:
- `OPENAI_API_KEY`
- `ELEVENLABS_API_KEY`

Never commit real keys to this repository or embed them in the Android APK.

## Build APK

1. Open the **Actions** tab.
2. Select **Build Android APK**.
3. Wait for the build to complete successfully.
4. Download the `sambalpuri-voice-debug` artifact.

The workflow downloads the official Sherpa-ONNX AAR, verifies its SHA-256 checksum, then builds the Android APK.

## Project layout

- `app/` — Android application and offline TTS runtime integration.
- `engine/` — FastAPI gateway for online providers.
- `render.yaml` — hosted API service blueprint.
- `.github/workflows/` — Android CI build.

## Native Sambalpuri voice roadmap

A genuine native Sambalpuri model still requires:
1. Consented recordings from native Sambalpuri speakers.
2. Accurate aligned transcripts covering regional vocabulary and conversational speech.
3. A pronunciation lexicon and phoneme/grapheme-to-phoneme rules.
4. Model training or adaptation and a properly documented licence.
5. Evaluation by native speakers across Western Odisha dialects.

Never use a person's voice recordings for cloning without explicit informed consent.
