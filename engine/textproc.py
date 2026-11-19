"""
textproc.py - steps 1 and 2 of the pipeline.

  1. normalize_text()  : numbers, symbols, abbreviations -> spoken English words
  2. to_odia()         : any text -> Odia script (approximate Sambalpuri flavour)

Word lookup order for every Latin word:
  custom lexicon  ->  ai4bharat IndicXlit (lang "or")  ->  small rule-based fallback
"""
import re
import unicodedata
from functools import lru_cache

from lexicon import ABBREVIATIONS, LETTER_NAMES, LEXICON

# --------------------------------------------------------------------------
# 1. TEXT NORMALIZATION
# --------------------------------------------------------------------------
_DIGITS = {ord(c): str(i) for i, c in enumerate("०१२३४५६७८९")}
_DIGITS.update({ord(c): str(i) for i, c in enumerate("୦୧୨୩୪୫୬୭୮୯")})


def _int_to_words(n: int) -> str:
    """Indian-style English words (lakh/crore). Long digit strings are spelled out."""
    from num2words import num2words
    return num2words(n, lang="en_IN").replace("-", " ").replace(",", " ")


def _number_words(token: str) -> str:
    token = token.replace(",", "")
    if "." in token:
        whole, frac = token.split(".", 1)
        spoken_frac = " ".join(_int_to_words(int(d)) for d in frac)
        return f"{_int_to_words(int(whole or 0))} point {spoken_frac}"
    if len(token) > 9:  # phone numbers, IDs: digit by digit
        return " ".join(_int_to_words(int(d)) for d in token)
    return _int_to_words(int(token))


def normalize_text(text: str) -> str:
    text = unicodedata.normalize("NFC", text).translate(_DIGITS)

    for pattern, repl in ABBREVIATIONS:
        text = re.sub(pattern, repl, text, flags=re.IGNORECASE)

    text = re.sub(r"(?<=\d):(?=\d)", " ", text)  # 10:30 -> 10 30

    # currency + symbols
    text = re.sub(r"₹\s*(\d[\d,]*(?:\.\d+)?)", r"\1 rupees", text)
    text = text.replace("%", " percent ").replace("&", " and ")
    text = text.replace("@", " at ").replace("+", " plus ")
    text = re.sub(r"(?<=\w)\s*=\s*(?=\w)", " equals ", text)

    # ordinals (1st, 2nd ...) then plain numbers
    from num2words import num2words
    text = re.sub(
        r"\b(\d+)(?:st|nd|rd|th)\b",
        lambda m: num2words(int(m.group(1)), to="ordinal").replace("-", " "),
        text, flags=re.IGNORECASE,
    )
    text = re.sub(r"\d[\d,]*(?:\.\d+)?", lambda m: " " + _number_words(m.group()) + " ", text)

    # stray symbols -> pause; keep sentence punctuation
    text = re.sub(r"[\"“”‘’()\[\]{}<>*_#~^|\\/]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


# --------------------------------------------------------------------------
# 2. TRANSLITERATION
# --------------------------------------------------------------------------
_LATIN = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")


def _deva_to_odia(text: str) -> str:
    """Devanagari and Odia blocks are aligned in Unicode (offset 0x200).
    Good enough for Hindi words; unassigned Odia slots are dropped."""
    out = []
    for ch in text:
        cp = ord(ch)
        if 0x0900 <= cp <= 0x097F and cp not in (0x0964, 0x0965):
            mapped = chr(cp + 0x200)
            if unicodedata.name(mapped, None):
                out.append(mapped)
        else:
            out.append(ch)
    return "".join(out)


@lru_cache(maxsize=1)
def _xlit_engine():
    """IndicXlit is heavy (fairseq). If missing we silently use the rule fallback."""
    try:
        from ai4bharat.transliteration import XlitEngine
        return XlitEngine("or", beam_width=10, src_script_type="en")
    except Exception as exc:  # noqa: BLE001
        print(f"[textproc] IndicXlit unavailable ({exc.__class__.__name__}); using rule fallback.")
        return None


# ---- tiny rule-based Roman -> Odia fallback (used when xlit is not installed)
_PRE = [("sch", "sk"), ("tion", "shan"), ("sion", "shan"), ("ck", "k"),
        ("qu", "kw"), ("x", "ks"), ("c(?=[eiy])", "s"), ("c", "k")]
_CONS = {"ksh": "କ୍ଷ", "kh": "ଖ", "gh": "ଘ", "ch": "ଚ", "jh": "ଝ", "th": "ଥ",
         "dh": "ଧ", "ph": "ଫ", "bh": "ଭ", "sh": "ଶ", "ng": "ଙ୍ଗ",
         "b": "ବ", "d": "ଡ", "f": "ଫ", "g": "ଗ", "h": "ହ", "j": "ଜ", "k": "କ",
         "l": "ଲ", "m": "ମ", "n": "ନ", "p": "ପ", "r": "ର", "s": "ସ", "t": "ଟ",
         "v": "ଭ", "w": "ୱ", "y": "ୟ", "z": "ଜ"}
# vowel -> (independent form, matra)
_VOW = {"aa": ("ଆ", "ା"), "ee": ("ଈ", "ୀ"), "oo": ("ଊ", "ୂ"), "ai": ("ଐ", "ୈ"),
        "ay": ("ଏ", "େ"), "au": ("ଔ", "ୌ"), "aw": ("ଔ", "ୌ"), "ou": ("ଆଉ", "ୌ"),
        "ow": ("ଆଉ", "ୌ"), "oa": ("ଓ", "ୋ"), "ea": ("ଈ", "ୀ"), "ie": ("ଈ", "ୀ"),
        "a": ("ଆ", "ା"), "e": ("ଏ", "େ"), "i": ("ଇ", "ି"), "o": ("ଓ", "ୋ"),
        "u": ("ଉ", "ୁ")}
_HALANT = "୍"


def _rule_translit(word: str) -> str:
    w = word.lower().replace("'", "")
    for pat, rep in _PRE:
        w = re.sub(pat, rep, w)
    w = re.sub(r"([bdfghjklmnprstvwz])\1", r"\1", w)          # ll -> l
    if len(w) > 3 and w.endswith("e") and w[-2] not in "aeiou" and re.search("[aeiou]", w[:-1]):
        w = w[:-1]                                            # silent final e
    keys = sorted(list(_CONS) + list(_VOW), key=len, reverse=True)

    out, after_cons, i = [], False, 0
    while i < len(w):
        key = next((k for k in keys if w.startswith(k, i)), None)
        if key is None:
            i += 1
            continue
        i += len(key)
        if key in _CONS:
            if after_cons:
                out.append(_HALANT)
            out.append(_CONS[key])
            after_cons = True
        else:
            if key == "y" and after_cons:
                continue
            out.append(_VOW[key][1] if after_cons else _VOW[key][0])
            after_cons = False
    return "".join(out)


def _word_to_odia(word: str, use_xlit: bool) -> str:
    low = word.lower()
    if low in LEXICON:
        return LEXICON[low]
    # plural / possessive: "schools" -> "ସ୍କୁଲ" + "ସ୍"
    if low.endswith("s") and low[:-1] in LEXICON:
        return LEXICON[low[:-1]] + "ସ୍"
    if "'" in low and low.split("'")[0] in LEXICON:
        return LEXICON[low.split("'")[0]]
    # acronyms: TV, ATM, PM  -> letter names
    if word.isalpha() and word.isupper() and 2 <= len(word) <= 5:
        return " ".join(LETTER_NAMES[c] for c in low)
    if use_xlit:
        eng = _xlit_engine()
        if eng is not None:
            try:
                cands = eng.translit_word(low, topk=1).get("or", [])
                if cands:
                    return cands[0]
            except Exception:  # noqa: BLE001
                pass
    return _rule_translit(low)


def to_odia(text: str, use_xlit: bool = True, normalize: bool = True) -> str:
    """Full text -> Odia-script string, ready for the TTS model."""
    if normalize:
        text = normalize_text(text)
    text = _deva_to_odia(text)
    return _LATIN.sub(lambda m: _word_to_odia(m.group(), use_xlit), text)
