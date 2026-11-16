from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(
    title="Sambalpuri Voice Engine",
    version="0.1.0",
    description="API boundary for the dedicated Sambalpuri neural TTS model.",
)


class SynthesisRequest(BaseModel):
    text: str = Field(min_length=1, max_length=5000)
    voice: str = "native-default"
    speed: float = Field(default=1.0, ge=0.7, le=1.3)


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": False}


@app.post("/v1/synthesize")
def synthesize(request: SynthesisRequest):
    # Do not substitute an unrelated Odia/Hindi voice and label it Sambalpuri.
    # A trained and licensed Sambalpuri model must be configured before this
    # endpoint can return audio.
    raise HTTPException(
        status_code=503,
        detail=(
            "Sambalpuri voice model is not configured yet. "
            "Add a trained model and inference adapter before enabling synthesis."
        ),
    )
