"""Command line:  python cli.py "Doctor sir went to school" -o out.mp3"""
import argparse
import sys
from pathlib import Path

from textproc import to_odia
from tts import DEFAULT_REF_AUDIO, DEFAULT_REF_TEXT, load_engine, speak


def main():
    ap = argparse.ArgumentParser(description="Sambalpuri-flavoured Odia TTS (approximate).")
    ap.add_argument("text", nargs="?", help="Text to speak (English / Hindi / Odia / mixed)")
    ap.add_argument("-f", "--file", help="Read text from a UTF-8 file instead")
    ap.add_argument("-o", "--out", default="output.wav", help="output .wav or .mp3")
    ap.add_argument("--engine", choices=["indicf5", "mms"], default="indicf5")
    ap.add_argument("--ref-audio", default=DEFAULT_REF_AUDIO)
    ap.add_argument("--ref-text", default=DEFAULT_REF_TEXT, help="Odia transcript or path to .txt")
    ap.add_argument("--no-xlit", action="store_true", help="skip IndicXlit, use lexicon + rules")
    ap.add_argument("--dry-run", action="store_true", help="only print the Odia text (no audio)")
    a = ap.parse_args()

    text = Path(a.file).read_text(encoding="utf-8") if a.file else a.text
    if not text:
        sys.exit("Give some text or --file.")

    if a.dry_run:
        print(to_odia(text, use_xlit=not a.no_xlit))
        return
    engine = load_engine(a.engine, a.ref_audio, a.ref_text)
    result = speak(text, a.out, engine, use_xlit=not a.no_xlit)
    print("Odia text :", result["odia_text"])
    print("Saved     :", result["file"])


if __name__ == "__main__":
    main()
