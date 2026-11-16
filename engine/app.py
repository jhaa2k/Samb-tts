import os
from typing import Literal

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

app = FastAPI(
    title="Sambalpuri Voice Engine",
    version="0.2.0",
    description="Online speech provider gateway. API credentials stay on the server.",
)

OPENAI_VOICES = [
    {"id": "alloy", "name": "Alloy", "style": "Balanced"},
    {"id": "ash", "name": "Ash", "style": "Clear, conversational"},
    {"id": "ballad", "name": "Ballad", "style": "Expressive"},
    {"id": "coral", "name": "Coral", "style": "Warm"},
    {"id": "echo", "name": "Echo", "style": "Smooth"},
    {"id": "fable", "name": "Fable", "style": "Narration"},
    {"id": "nova", "name": "Nova", "style": "Bright"},
    {"id": "onyx", "name": "Onyx", "style": "Deep"},
    {"id": "sage", "name": "Sage", "style": "Measured"},
    {"id": "shimmer", "name": "Shimmer", "style": "Soft"},
    {"id": "verse", "name": "Verse", "style": "Expressive"},
    {"id": "marin", "name": "Marin", "style": "Natural"},
    {"id": "cedar", "name": "Cedar", "style": "Natural"},
]


class SynthesisRequest(BaseModel):
    text: str = Field(min_length=1, max_length=4000)
    provider: Literal["openai", "elevenlabs"]
    voice: str = Field(min_length=1, max_length=160)
    speed: float = Field(default=1.0, ge=0.7, le=1.3)
    style: str = Field(default="Speak naturally and clearly.", max_length=500)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "providers": {
            "openai": bool(os.getenv("OPENAI_API_KEY")),
            "elevenlabs": bool(os.getenv("ELEVENLABS_API_KEY")),
        },
        "offline_runtime": "Android Sherpa-ONNX module",
        "native_sambalpuri_model": False,
    }


@app.get("/v1/voices")
async def list_voices(provider: Literal["openai", "elevenlabs"]):
    if provider == "openai":
        if not os.getenv("OPENAI_API_KEY"):
            raise HTTPException(503, "OPENAI_API_KEY is not configured on the server.")
        return {"provider": "openai", "voices": OPENAI_VOICES}

    key = os.getenv("ELEVENLABS_API_KEY")
    if not key:
        raise HTTPException(503, "ELEVENLABS_API_KEY is not configured on the server.")
    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.get(
            "https://api.elevenlabs.io/v2/voices",
            headers={"xi-api-key": key},
            params={"page_size": 100},
        )
    if response.status_code >= 400:
        raise HTTPException(response.status_code, "ElevenLabs voice catalogue request failed.")
    data = response.json()
    voices = [
        {
            "id": item["voice_id"],
            "name": item["name"],
            "style": "Provider voice",
            "preview_url": item.get("preview_url"),
        }
        for item in data.get("voices", [])
    ]
    return {"provider": "elevenlabs", "voices": voices}


@app.post("/v1/synthesize")
async def synthesize(request: SynthesisRequest):
    if not request.text.strip():
        raise HTTPException(400, "Text cannot be empty.")

    if request.provider == "openai":
        key = os.getenv("OPENAI_API_KEY")
        if not key:
            raise HTTPException(503, "OPENAI_API_KEY is not configured on the server.")
        payload = {
            "model": "gpt-4o-mini-tts",
            "voice": request.voice,
            "input": request.text,
            "instructions": request.style,
            "speed": request.speed,
            "response_format": "mp3",
        }
        url = "https://api.openai.com/v1/audio/speech"
        headers = {"Authorization": f"Bearer {key}"}
    else:
        key = os.getenv("ELEVENLABS_API_KEY")
        if not key:
            raise HTTPException(503, "ELEVENLABS_API_KEY is not configured on the server.")
        payload = {
            "text": request.text,
            "model_id": "eleven_multilingual_v2",
            "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75,
                "style": 0.2,
                "use_speaker_boost": True,
                "speed": request.speed,
            },
        }
        url = f"https://api.elevenlabs.io/v1/text-to-speech/{request.voice}"
        headers = {"xi-api-key": key}

    async with httpx.AsyncClient(timeout=120) as client:
        response = await client.post(url, headers=headers, json=payload)
    if response.status_code >= 400:
        detail = "Speech provider rejected the request. Check API key, voice ID, quota and text length."
        raise HTTPException(response.status_code, detail)

    return Response(
        content=response.content,
        media_type="audio/mpeg",
        headers={"Cache-Control": "no-store"},
    )
