# Sambalpuri voice engine

This is the Python API boundary for the future neural TTS model.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

Open `/docs` for the API schema and call `/health` to check the service.

## Why synthesis currently returns 503

There is not yet a verified Sambalpuri speech model in this repository. Returning an Odia or Hindi voice as if it were native Sambalpuri would be misleading. Add the trained model, its license, model card, inference adapter, and evaluation results before enabling `/v1/synthesize`.

## Data collection requirements

- Obtain explicit informed consent from every speaker.
- Record clean speech in a quiet room with a consistent microphone distance.
- Keep original audio lossless (WAV, mono, 22.05 kHz or model-native rate).
- Store one utterance per file and maintain exact transcripts.
- Record dialect/region and speaker metadata only with permission.
- Use train/validation/test splits by speaker to avoid leakage.
- Include colloquial phrases, questions, numbers, names, code-switching, and common chat vocabulary.
- Have native Sambalpuri reviewers verify spelling, transcript alignment, and generated speech.

Never scrape private chats or use someone's voice without permission.
