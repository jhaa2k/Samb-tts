# Sambalpuri Voice Engine API

FastAPI gateway for OpenAI, ElevenLabs and the optional IndicF5 reference-guided speech engine. Provider credentials stay on the server.

## Run the lightweight API

```bash
cd engine
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app:app --host 0.0.0.0 --port 8000
```

Open `/docs` for the API schema and `/health` for provider readiness.

## Endpoints

- `GET /health` — provider configuration status; never returns secret values.
- `GET /v1/voices?provider=openai` — built-in OpenAI voice IDs.
- `GET /v1/voices?provider=elevenlabs` — voices in the configured ElevenLabs account.
- `GET /v1/voices?provider=indicf5` — reference speaker, when model access and reference files are configured.
- `POST /v1/synthesize` — returns MP3 for OpenAI/ElevenLabs and 24 kHz PCM WAV for IndicF5.

Existing `/v1/synthesize` request fields remain unchanged:

```json
{
  "text": "ନମସ୍କାର, ମୁଇଁ ସମ୍ବଲପୁରୀ ଭାଷାରେ କଥା ହେଉଛେଁ।",
  "provider": "indicf5",
  "voice": "reference",
  "speed": 1.0,
  "style": "Speak naturally and clearly."
}
```

IndicF5 currently accepts Odia among its supported languages. Sambalpuri is not listed as a separately supported or trained language, so correct Sambalpuri pronunciation is not guaranteed. Use Odia-script Sambalpuri text for the first tests; Roman-letter input needs a validated Sambalpuri-to-Odia transliteration layer and must not be silently treated as English.

## Enable optional IndicF5

1. Sign in to Hugging Face and accept the access conditions for [ai4bharat/IndicF5](https://huggingface.co/ai4bharat/IndicF5).
2. Create a Hugging Face access token with permission to read the model.
3. Install the optional dependencies in an environment with adequate RAM:

   ```bash
   pip install -r requirements-indicf5.txt
   ```

4. Add these server environment variables (never commit tokens):

   - `HF_TOKEN` — Hugging Face read token.
   - `INDICF5_MODEL_ID` — optional; defaults to `ai4bharat/IndicF5`.
   - `INDICF5_REF_AUDIO` — optional path; defaults to `reference/ref.wav`.
   - `INDICF5_REF_TEXT` — exact transcript of the reference recording.\n- `INDICF5_TRANSLITERATE_ROMAN` — defaults to `true`; converts Roman-letter input to Odia before inference.\n- `SAMBALPURI_LEXICON` — optional path to a custom JSON Roman-to-Odia word map.

5. Put the speaker-approved reference WAV at `engine/reference/ref.wav` (or configure an absolute path). Use a clean, single-speaker recording, ideally 5–10 seconds, 24 kHz, mono. The transcript must match the recording exactly. Do not use anyone's voice without explicit permission.\n6. Roman-letter input is converted to Odia script using optional IndicXlit plus `engine/sambalpuri_lexicon.json` overrides. The starter lexicon only covers a few sample words; native speakers should review and expand it. Set `INDICF5_TRANSLITERATE_ROMAN=false` to disable automatic conversion.

The reference audio is intentionally not included in this repository. Add it only after the speaker has provided informed consent. Keep private voice recordings out of public commits unless the speaker explicitly approves public distribution.

## Deployment and resource limits

The normal `requirements.txt` intentionally does not install PyTorch or IndicF5, so the existing lightweight Render deployment and current OpenAI/ElevenLabs adapters remain deployable. IndicF5 is a roughly 0.4B-parameter model and its PyTorch/Transformers runtime has substantial RAM and cold-start requirements. A small/free Render instance is not a reliable target for it; use a dedicated GPU/large-memory host or a separately managed inference worker. Do not enable the heavy optional dependency set on the free service and assume it will fit.

The Android app's existing endpoint contract is preserved. Confirm that the Android playback layer accepts WAV as well as MP3 before selecting IndicF5 in the app UI.

## Existing offline Android engine

The Android build packages Sherpa-ONNX and can download the Kitten Nano English model. That model is English-only, not an Odia or Sambalpuri voice.

## Model and voice limitations

IndicF5 supports Assamese, Bengali, Gujarati, Hindi, Kannada, Malayalam, Marathi, Odia, Punjabi, Tamil and Telugu. Its model card recommends Indic-Speak for broader language coverage. Neither claim establishes native Sambalpuri support. A native Sambalpuri voice requires representative Sambalpuri speech data, accurate transcripts, speaker consent, and evaluation by native speakers.

IndicF5 requires three inputs: synthesis text, reference audio, and the exact reference transcript. The model card requires explicit permission for any cloned voice. See [the official model card](https://huggingface.co/ai4bharat/IndicF5).

## Existing providers

Configure `OPENAI_API_KEY` and/or `ELEVENLABS_API_KEY` in the hosting dashboard. Do not put provider keys in the Android app, GitHub source files, or public issue comments. Provider usage may incur charges and is subject to account limits and terms.


## Balangiri (Balangir–Subarnapur) dialect profile

The active starter lexicon includes three documented Bolangir/Balangiri-specific forms:

| Meaning | Roman input | Odia-script form |
|---|---|---|
| no / not | `ni` | `ନି` |
| now | `ekhen` | `ଏଖେନ` |
| to run | `nardiba` | `ନରଡିବା` |

See [`balangiri_dialect_profile.json`](balangiri_dialect_profile.json) for regional contrasts and source notes. These are documented regional distinctions, not a complete Balangiri dictionary. The broader Sambalpuri lexicon remains the fallback for other words; IndicXlit supplies Odia-script transliteration, not dialect translation. A full and reliable Balangiri vocabulary, grammar and phrase bank needs native-speaker review, especially for village-level pronunciation and spelling variants.
