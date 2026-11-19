# Sambalpuri Vaani - engine

Pipeline: **normalize -> Odia-script transliteration -> IndicF5 (voice-conditioned) -> WAV/MP3**

## Honest limits
- There is **no Sambalpuri TTS model**. This uses Odia TTS (IndicF5) + a reference clip of a
  Sambalpuri speaker. Expect an approximation, not native Sambalpuri.
- Transliteration is approximate. `lexicon.py` is the biggest quality lever: have a native
  speaker review and extend it.
- Output quality depends heavily on the reference clip (clean, 5-10 s, 24 kHz mono).
- Use only voices you have explicit consent to use.

## Setup (Python 3.10)
```bash
cd engine
pip install -r requirements.txt
pip install git+https://github.com/ai4bharat/IndicF5.git
huggingface-cli login            # ai4bharat/IndicF5 is a gated model: accept terms first
pip install ai4bharat-transliteration   # optional; without it lexicon + rules are used
sudo apt install ffmpeg          # only for MP3 output
```
Put `reference/ref.wav` + `reference/ref.txt` (exact Odia transcript). See `reference/README.md`.

## Use
```bash
python cli.py "Doctor sir went to school at 10:30" -o out.mp3
python cli.py "Hello bhai, मेरा नाम राम है" --dry-run        # just show the Odia text
python cli.py -f story.txt --engine mms -o out.wav          # lighter fallback (fixed voice)
uvicorn app:app --port 8000                                 # API: POST /v1/synthesize {"text": "..."}
```

## Trade-offs
| Engine  | Size | Speed (CPU) | Voice | Notes |
|---------|------|-------------|-------|-------|
| indicf5 | ~351M params | slow, GPU recommended | cloned from your reference | best accent control |
| mms     | ~36M params | fast | one fixed voice | CC-BY-NC licence, no Sambalpuri voice |

Numbers are spoken as English words (Indian style: lakh/crore). Odia number words are a
possible later improvement.
