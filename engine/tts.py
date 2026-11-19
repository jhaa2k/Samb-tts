"""
tts.py - steps 3 and 4 of the pipeline: synthesis + saving WAV/MP3.

Engines
  indicf5 : ai4bharat/IndicF5 (351M params, voice-conditioned by a reference clip)
            -> this is the one that can imitate a Sambalpuri speaker's voice.
  mms     : facebook/mms-tts-ory (lighter, Odia, but ONE fixed voice, no cloning,
            licence CC-BY-NC). Fallback only.
"""
import os
import re
import shutil
import subprocess
from pathlib import Path

import numpy as np
import soundfile as sf

from textproc import to_odia

HERE = Path(__file__).parent
DEFAULT_REF_AUDIO = os.getenv("SAMB_REF_AUDIO", str(HERE / "reference" / "ref.wav"))
DEFAULT_REF_TEXT = os.getenv("SAMB_REF_TEXT", str(HERE / "reference" / "ref.txt"))


# ---------------------------------------------------------------- engines ---
class IndicF5Engine:
    sample_rate = 24000

    def __init__(self, ref_audio: str, ref_text: str):
        if not Path(ref_audio).is_file():
            raise FileNotFoundError(
                f"Reference audio not found: {ref_audio}\n"
                "Put a clean 5-10 s Sambalpuri recording (24 kHz mono WAV) there, and its "
                "exact Odia transcript in reference/ref.txt. Only use a voice you have "
                "consent to use.")
        if Path(ref_text).is_file():
            ref_text = Path(ref_text).read_text(encoding="utf-8").strip()
        if not ref_text:
            raise ValueError("Reference transcript (Odia script) is empty.")
        from transformers import AutoModel  # gated model: run `huggingface-cli login` first
        self.model = AutoModel.from_pretrained("ai4bharat/IndicF5", trust_remote_code=True)
        try:
            import torch
            if torch.cuda.is_available():
                self.model = self.model.to("cuda")
        except Exception:  # noqa: BLE001
            pass
        self.ref_audio, self.ref_text = ref_audio, ref_text

    def synth(self, odia_text: str) -> np.ndarray:
        audio = np.asarray(self.model(odia_text, ref_audio_path=self.ref_audio,
                                      ref_text=self.ref_text))
        if audio.dtype == np.int16:
            audio = audio.astype(np.float32) / 32768.0
        return audio.astype(np.float32)


class MMSEngine:
    def __init__(self):
        from transformers import AutoTokenizer, VitsModel
        self.tok = AutoTokenizer.from_pretrained("facebook/mms-tts-ory")
        self.model = VitsModel.from_pretrained("facebook/mms-tts-ory")
        self.sample_rate = self.model.config.sampling_rate

    def synth(self, odia_text: str) -> np.ndarray:
        import torch
        inputs = self.tok(odia_text, return_tensors="pt")
        with torch.no_grad():
            wav = self.model(**inputs).waveform
        return wav.squeeze().cpu().numpy().astype(np.float32)


def load_engine(name: str = "indicf5", ref_audio: str = DEFAULT_REF_AUDIO,
                ref_text: str = DEFAULT_REF_TEXT):
    if name == "indicf5":
        return IndicF5Engine(ref_audio, ref_text)
    if name == "mms":
        return MMSEngine()
    raise ValueError(f"Unknown engine '{name}' (use 'indicf5' or 'mms').")


# ------------------------------------------------------------ chunk + save ---
def chunk_text(text: str, max_chars: int = 180) -> list[str]:
    """Split on sentence ends, then commas, so long inputs stay within model limits."""
    parts = []
    for sent in re.split(r"(?<=[।.!?])\s+", text):
        while len(sent) > max_chars:
            cut = sent.rfind(",", 0, max_chars)
            cut = cut if cut > 40 else sent.rfind(" ", 0, max_chars)
            parts.append(sent[:cut + 1].strip())
            sent = sent[cut + 1:]
        if sent.strip():
            parts.append(sent.strip())
    return parts


def save_audio(wav: np.ndarray, sr: int, out_path: str) -> str:
    """Save WAV; if the path ends with .mp3 convert with ffmpeg (must be installed)."""
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.suffix.lower() != ".mp3":
        sf.write(out, wav, sr)
        return str(out)
    if not shutil.which("ffmpeg"):
        wav_path = out.with_suffix(".wav")
        sf.write(wav_path, wav, sr)
        print("[tts] ffmpeg not found - saved WAV instead of MP3.")
        return str(wav_path)
    tmp = out.with_suffix(".tmp.wav")
    sf.write(tmp, wav, sr)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(tmp),
                    "-codec:a", "libmp3lame", "-q:a", "2", str(out)], check=True)
    tmp.unlink(missing_ok=True)
    return str(out)


def speak(text: str, out_path: str, engine=None, use_xlit: bool = True) -> dict:
    """Run the full pipeline. `engine` is reused between calls (model load is slow)."""
    odia = to_odia(text, use_xlit=use_xlit)             # steps 1 + 2
    engine = engine or load_engine()
    pause = np.zeros(int(0.25 * engine.sample_rate), dtype=np.float32)
    pieces = []
    for chunk in chunk_text(odia):                      # step 3
        pieces += [engine.synth(chunk), pause]
    saved = save_audio(np.concatenate(pieces), engine.sample_rate, out_path)  # step 4
    return {"odia_text": odia, "file": saved}
