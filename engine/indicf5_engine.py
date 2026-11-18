"""Optional IndicF5 inference adapter.

The heavy model is loaded lazily and kept in memory for subsequent requests.
IndicF5 is an Odia-capable multilingual model; it is not a native Sambalpuri model.
"""
import io
import os
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
    model = AutoModel.from_pretrained(
        model_id,
        token=token,
        trust_remote_code=True,
    )
    if hasattr(model, "eval"):
        model.eval()
    return model


def synthesize_wav(text: str) -> bytes:
    reference_path = os.getenv("INDICF5_REF_AUDIO", "reference/ref.wav")
    reference_text = os.getenv("INDICF5_REF_TEXT", "").strip()
    if not os.path.isfile(reference_path):
        raise FileNotFoundError("IndicF5 reference audio was not found")
    if not reference_text:
        raise ValueError("Exact reference transcript is required")

    import torch

    model = _load_model()
    with torch.inference_mode():
        audio = model(
            text,
            ref_audio_path=reference_path,
            ref_text=reference_text,
        )

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
