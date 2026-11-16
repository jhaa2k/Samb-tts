# Sambalpuri Voice Engine API

This FastAPI service provides online TTS adapters for OpenAI and ElevenLabs. Provider keys stay on the server and are never embedded in the Android APK.

## Run locally

```bash
cd engine
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Add your own provider keys to .env before starting the server.
set -a && source .env && set +a
uvicorn app:app --host 0.0.0.0 --port 8000
```

Open `/docs` for the API schema and `/health` to check which providers are configured.

## Endpoints

- `GET /health` — provider configuration status (never returns secret values).
- `GET /v1/voices?provider=openai` — 13 built-in OpenAI voice IDs.
- `GET /v1/voices?provider=elevenlabs` — voices available to the configured ElevenLabs account.
- `POST /v1/synthesize` — returns generated MP3 audio.

Example request:

```json
{
  "text": "Hello. This is a voice sample.",
  "provider": "openai",
  "voice": "coral",
  "speed": 1.0,
  "style": "Speak naturally and clearly."
}
```

## Deploy

The repository includes a Render blueprint at the root. Create a new Render service from this repository and add `OPENAI_API_KEY` and/or `ELEVENLABS_API_KEY` as secret environment variables in the hosting dashboard. Then copy the deployed HTTPS service URL into the Android app's **Your hosted engine URL** field.

Do not put provider keys in the Android app, GitHub source files, or public issue comments. Provider usage may incur charges and is subject to each provider's account limits and terms.

## Offline Android engine

The Android build downloads the official Sherpa-ONNX 2.0.1 AAR during GitHub Actions and packages the native runtime. The app can download the Kitten Nano English model on demand. It provides eight English speakers and works offline after installation. The model is English-only; it is not an Odia or Sambalpuri model.

## Sambalpuri model status

There is no verified native Sambalpuri model configured in this project. Do not present English, Hindi, or Odia model output as native Sambalpuri. A real Sambalpuri voice requires consented native-speaker recordings, aligned transcripts, a trained model, and evaluation by Sambalpuri speakers.

## Voice-data ethics

- Obtain explicit informed consent from every speaker.
- Do not clone a person's voice without their permission.
- Keep model licences and speaker consent records with the dataset.
- Never scrape private chats or use private recordings.
