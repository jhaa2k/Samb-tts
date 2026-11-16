# Sambalpuri Voice

An Android-first Sambalpuri text-to-speech project.

## Current status

This repository is the clean project foundation:
- Android app written in Kotlin + Jetpack Compose.
- Reader screen with text input, speak, and stop controls.
- GitHub Actions workflow to build a debug APK.
- Python FastAPI voice-engine service scaffold.

**Important:** A genuinely natural Sambalpuri voice requires a dedicated, properly licensed Sambalpuri speech dataset and a trained/adapted TTS model. The current Android reader can only use an Odia system voice if the device has one installed; that is a temporary fallback, not perfect Sambalpuri pronunciation. The Python API deliberately reports that the model is not configured instead of pretending to generate audio.

## Build APK

1. Open the **Actions** tab.
2. Select **Build Android APK**.
3. Run the workflow (or push a commit).
4. Download the `app-debug` artifact from the completed run.

## Project layout

- `app/` — Android application.
- `engine/` — Python API scaffold and voice-data preparation notes.
- `.github/workflows/` — Android CI build.

## Voice-model roadmap

1. Collect consented, clean recordings from native Sambalpuri speakers.
2. Prepare matching transcripts in a consistent script and spelling convention.
3. Build a pronunciation lexicon for regional words and variants.
4. Train or adapt a multilingual neural TTS model.
5. Evaluate with native speakers across Western Odisha accents.
6. Export an optimized model for server inference first, then investigate on-device ONNX deployment.

Do not upload recordings without the speaker's informed permission. Keep speaker identity and licensing information with every dataset release.

## Development

Android: Kotlin, Jetpack Compose, Android TextToSpeech (temporary device-voice fallback).

Engine: Python, FastAPI. See [engine/README.md](engine/README.md).
