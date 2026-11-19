"""
FastAPI wrapper (replaces the old OpenAI/ElevenLabs gateway).

  uvicorn app:app --host 0.0.0.0 --port 8000

POST /v1/synthesize {"text": "..."}  -> audio/mpeg (or audio/wav if ffmpeg is missing)
The old Android client's extra fields (provider/voice/speed/style) are accepted and ignored.
"""
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from starlette.background import BackgroundTask

from textproc import to_odia
from tts import load_engine, speak

ENGINE_NAME = os.getenv("SAMB_ENGINE", "indicf5")
app = FastAPI(title="Sambalpuri Vaani", version="1.0.0")
_engine = None


def get_engine():
    global _engine
    if _engine is None:
        try:
            _engine = load_engine(ENGINE_NAME)
        except (FileNotFoundError, ValueError) as exc:
            raise HTTPException(503, str(exc)) from exc
    return _engine


class Req(BaseModel):
    text: str = Field(min_length=1, max_length=4000)
    use_xlit: bool = True
    provider: str | None = None   # ignored (backwards compatibility)
    voice: str | None = None      # ignored
    speed: float | None = None    # ignored
    style: str | None = None      # ignored


@app.get("/health")
def health():
    return {"status": "ok", "engine": ENGINE_NAME, "model_loaded": _engine is not None,
            "native_sambalpuri_model": False,
            "note": "Odia TTS + reference voice; approximates, not true Sambalpuri."}


@app.post("/v1/transliterate")
def transliterate(req: Req):
    return {"odia_text": to_odia(req.text, use_xlit=req.use_xlit)}


@app.post("/v1/synthesize")
def synthesize(req: Req):
    engine = get_engine()
    tmp = Path(tempfile.mkdtemp()) / "out.mp3"
    result = speak(req.text, str(tmp), engine, use_xlit=req.use_xlit)
    f = Path(result["file"])
    media = "audio/mpeg" if f.suffix == ".mp3" else "audio/wav"
    return FileResponse(f, media_type=media, headers={"Cache-Control": "no-store"},
                        background=BackgroundTask(lambda: f.unlink(missing_ok=True)))
