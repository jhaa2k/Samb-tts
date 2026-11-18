import os
from typing import Literal

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

app = FastAPI(
    title="Sambalpuri Voice Engine",
    version="0.3.0",
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
    provider: Literal["openai", "elevenlabs", "indicf5"]
    voice: str = Field(min_length=1, max_length=160)
    speed: float = Field(default=1.0, ge=0.7, le=1.3)
    style: str = Field(default="Speak naturally and clearly.", max_length=500)


def indicf5_status():
    reference_path = os.getenv("INDICF5_REF_AUDIO", "reference/ref.wav")
    return {
        "enabled": bool(os.getenv("HF_TOKEN")),
        "model": os.getenv("INDICF5_MODEL_ID", "ai4bharat/IndicF5"),
        "reference_audio_present": os.path.isfile(reference_path),
        "reference_transcript_present": bool(os.getenv("INDICF5_REF_TEXT", "").strip()),
        "sample_rate": 24000,
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "providers": {
            "openai": bool(os.getenv("OPENAI_API_KEY")),
            "elevenlabs": bool(os.getenv("ELEVENLABS_API_KEY")),
            "indicf5": indicf5_status(),
        },
        "offline_runtime": "Android Sherpa-ONNX module",
        "native_sambalpuri_model": False,
        "note": "IndicF5 supports Odia, not a separately trained Sambalpuri language model.",
    }


@app.get("/v1/voices")
async def list_voices(provider: Literal["openai", "elevenlabs", "indicf5"]):
    if provider == "indicf5":
        status = indicf5_status()
        if not status["enabled"]:
            raise HTTPException(503, "HF_TOKEN is not configured. Accept the IndicF5 model conditions and add a Hugging Face access token.")
        if not status["reference_audio_present"] or not status["reference_transcript_present"]:
            raise HTTPException(503, "IndicF5 reference audio or its exact transcript is missing.")
        return {
            "provider": "indicf5",
            "voices": [{"id": "reference", "name": "Reference speaker", "style": "Voice guided by your consented reference recording"}],
            "format": "wav",
            "sample_rate": 24000,
        }

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

    if request.provider == "indicf5":
        status = indicf5_status()
        if not status["enabled"]:
            raise HTTPException(503, "HF_TOKEN is missing. Accept the IndicF5 model conditions on Hugging Face and configure the token on the server.")
        if not status["reference_audio_present"]:
            raise HTTPException(503, "IndicF5 reference audio is missing. Add your consented 5–10 second recording as engine/reference/ref.wav.")
        if not status["reference_transcript_present"]:
            raise HTTPException(503, "INDICF5_REF_TEXT is missing. It must exactly match the words spoken in the reference recording.")
        try:
            import asyncio
            from indicf5_engine import synthesize_wav

            audio_bytes = await asyncio.to_thread(synthesize_wav, request.text)
        except ImportError as exc:
            raise HTTPException(503, "IndicF5 runtime is not installed. Install engine/requirements-indicf5.txt on a machine with sufficient RAM.") from exc
        except Exception as exc:
            # Keep private paths, access tokens and upstream internals out of API responses.
            raise HTTPException(502, f"IndicF5 generation failed: {type(exc).__name__}. Check model access, reference audio and server logs.") from exc
        return Response(
            content=audio_bytes,
            media_type="audio/wav",
            headers={"Cache-Control": "no-store", "X-Audio-Sample-Rate": "24000"},
        )

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
