"""Optional IndicF5 inference adapter with Roman-to-Odia input support.

IndicF5 supports Odia, not a separately trained Sambalpuri model. Roman
transliteration is a convenience layer and must be checked by native speakers.
"""
import io
import json
import os
import re
from functools import lru_cache

import numpy as np
import soundfile as sf


@lru_cache(maxsize=1)
def _load_model():
    from transformers import AutoModel

    token = os.getenv("HF_TOKEN")
    if not token:
        raise RuntimeError("HF_TOKEN is not configured")
    model_id = os.getenv("INDICF5_MODEL_ID", "ai4bharat/IndicF5")
    model = AutoModel.from_pretrained(model_id, token=token, trust_remote_code=True)
    if hasattr(model, "eval"):
        model.eval()
    return model


@lru_cache(maxsize=1)
def _load_xlit():
    from ai4bharat.transliteration import XlitEngine
    return XlitEngine("or", beam_width=10, rescore=True)


@lru_cache(maxsize=1)
def _load_lexicon():
    path = os.getenv("SAMBALPURI_LEXICON", "sambalpuri_lexicon.json")
    if not os.path.isfile(path):
        return {}
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    return {str(k).lower(): str(v) for k, v in data.items()}


def _to_odia_text(text: str) -> str:
    # Already Odia (including Sambalpuri written in Odia script): keep as-is.
    if any("\u0B00" <= char <= "\u0B7F" for char in text):
        return text
    if os.getenv("INDICF5_TRANSLITERATE_ROMAN", "true").lower() not in {"1", "true", "yes"}:
        return text

    lexicon = _load_lexicon()
    xlit = _load_xlit()
    pieces = re.findall(r"[A-Za-z]+|[^A-Za-z]+", text)
    converted = []
    for piece in pieces:
        if not piece or not piece[0].isalpha() or not piece.isascii():
            converted.append(piece)
            continue
        word = piece.lower()
        if word in lexicon:
            converted.append(lexicon[word])
            continue
        result = xlit.translit_word(piece, topk=1)
        candidates = result.get("or", []) if isinstance(result, dict) else []
        converted.append(candidates[0] if candidates else piece)
    return "".join(converted)


def synthesize_wav(text: str) -> bytes:
    reference_path = os.getenv("INDICF5_REF_AUDIO", "reference/ref.wav")
    reference_text = os.getenv("INDICF5_REF_TEXT", "").strip()
    if not os.path.isfile(reference_path):
        raise FileNotFoundError("IndicF5 reference audio was not found")
    if not reference_text:
        raise ValueError("Exact reference transcript is required")

    import torch

    model = _load_model()
    odia_text = _to_odia_text(text)
    with torch.inference_mode():
        audio = model(odia_text, ref_audio_path=reference_path, ref_text=reference_text)

    if hasattr(audio, "detach"):
        audio = audio.detach().cpu().numpy()
    audio = np.asarray(audio)
    if audio.dtype == np.int16:
        audio = audio.astype(np.float32) / 32768.0
    else:
        audio = audio.astype(np.float32, copy=False)
    audio = np.squeeze(audio)
    if audio.ndim != 1 or audio.size == 0 or not np.isfinite(audio).all():
        raise RuntimeError("IndicF5 returned invalid audio")

    output = io.BytesIO()
    sf.write(output, audio, samplerate=24000, format="WAV", subtype="PCM_16")
    return output.getvalue()
