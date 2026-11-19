"""
lexicon.py - hand-written English -> Odia-script pronunciation dictionary.

This is the MOST IMPORTANT file for accent quality. A native Sambalpuri
speaker should review and extend it. Spelling here = how the word should
SOUND when read by the Odia TTS (e.g. English "school" -> "ସ୍କୁଲ").

Add words freely: keys are lower-case English, values are Odia script.
"""

# ---- common English words as a Sambalpuri/Odia speaker would say them ------
LEXICON = {
    # places / institutions
    "school": "ସ୍କୁଲ", "college": "କଲେଜ", "hospital": "ହସ୍ପିଟାଲ",
    "office": "ଅଫିସ", "bank": "ବ୍ୟାଙ୍କ", "market": "ମାର୍କେଟ",
    "hotel": "ହୋଟେଲ", "station": "ଷ୍ଟେସନ", "police": "ପୁଲିସ",
    "road": "ରୋଡ୍", "class": "କ୍ଲାସ", "government": "ଗଭର୍ଣ୍ଣମେଣ୍ଟ",
    # people / jobs
    "doctor": "ଡାକ୍ତର", "teacher": "ଟିଚର", "student": "ଷ୍ଟୁଡେଣ୍ଟ",
    "mister": "ମିଷ୍ଟର", "missus": "ମିସେସ୍", "madam": "ମ୍ୟାଡାମ୍",
    "sir": "ସାର୍", "professor": "ପ୍ରଫେସର",
    # things / tech
    "mobile": "ମୋବାଇଲ", "phone": "ଫୋନ୍", "computer": "କମ୍ପ୍ୟୁଟର",
    "internet": "ଇଣ୍ଟରନେଟ୍", "video": "ଭିଡିଓ", "audio": "ଅଡିଓ",
    "bus": "ବସ୍", "train": "ଟ୍ରେନ୍", "car": "କାର୍", "bike": "ବାଇକ୍",
    "ticket": "ଟିକେଟ", "book": "ବୁକ୍", "pen": "ପେନ୍", "paper": "ପେପର",
    "water": "ୱାଟର", "news": "ନ୍ୟୁଜ୍", "app": "ଆପ୍", "text": "ଟେକ୍ସଟ୍",
    "voice": "ଭଏସ୍", "speech": "ସ୍ପିଚ୍", "time": "ଟାଇମ୍",
    # exams / politics
    "exam": "ଏଗ୍ଜାମ୍", "result": "ରେଜଲ୍ଟ", "vote": "ଭୋଟ୍",
    "election": "ଇଲେକ୍ସନ", "minister": "ମିନିଷ୍ଟର",
    # greetings / function words
    "hello": "ହେଲୋ", "hi": "ହାଇ", "thank": "ଥାଙ୍କ", "thanks": "ଥାଙ୍କ୍ସ",
    "please": "ପ୍ଲିଜ୍", "sorry": "ସରି", "ok": "ଓକେ", "okay": "ଓକେ",
    "yes": "ୟେସ୍", "no": "ନୋ", "good": "ଗୁଡ୍", "morning": "ମର୍ନିଂ",
    "night": "ନାଇଟ୍", "welcome": "ୱେଲକମ୍", "and": "ଏଣ୍ଡ", "the": "ଦି",
    "is": "ଇଜ୍", "this": "ଦିସ୍", "that": "ଦ୍ୟାଟ୍", "a": "ଏ",
    # symbols / numbers-as-words (used by the normalizer)
    "rupees": "ରୁପିଜ୍", "percent": "ପର୍ସେଣ୍ଟ", "point": "ପଏଣ୍ଟ",
    "plus": "ପ୍ଲସ୍", "minus": "ମାଇନସ୍", "at": "ଆଟ୍", "dot": "ଡଟ୍",
    "zero": "ଜିରୋ", "one": "ୱାନ୍", "two": "ଟୁ", "three": "ଥ୍ରି",
    "four": "ଫୋର୍", "five": "ଫାଇଭ୍", "six": "ସିକ୍ସ", "seven": "ସେଭେନ୍",
    "eight": "ଏଟ୍", "nine": "ନାଇନ୍", "ten": "ଟେନ୍", "eleven": "ଇଲେଭେନ୍",
    "twelve": "ଟ୍ୱେଲଭ୍", "thirteen": "ଥର୍ଟିନ୍", "fourteen": "ଫୋର୍ଟିନ୍",
    "fifteen": "ଫିଫ୍ଟିନ୍", "sixteen": "ସିକ୍ସଟିନ୍", "seventeen": "ସେଭେନ୍ଟିନ୍",
    "eighteen": "ଏଟିନ୍", "nineteen": "ନାଇନଟିନ୍", "twenty": "ଟ୍ୱେଣ୍ଟି",
    "thirty": "ଥର୍ଟି", "forty": "ଫୋର୍ଟି", "fifty": "ଫିଫ୍ଟି",
    "sixty": "ସିକ୍ସଟି", "seventy": "ସେଭେଣ୍ଟି", "eighty": "ଏଟି",
    "ninety": "ନାଇନଟି", "hundred": "ହଣ୍ଡ୍ରେଡ୍", "thousand": "ଥାଉଜେଣ୍ଡ୍",
    "kilometre": "କିଲୋମିଟର", "kilo": "କିଲୋ",
    "lakh": "ଲକ୍ଷ", "crore": "କୋଟି",
    "first": "ଫର୍ଷ୍ଟ", "second": "ସେକେଣ୍ଡ", "third": "ଥର୍ଡ",
}

# ---- how English letter NAMES sound (for acronyms: TV, ATM, PM ...) --------
LETTER_NAMES = {
    "a": "ଏ", "b": "ବି", "c": "ସି", "d": "ଡି", "e": "ଇ", "f": "ଏଫ୍",
    "g": "ଜି", "h": "ଏଚ୍", "i": "ଆଇ", "j": "ଜେ", "k": "କେ", "l": "ଏଲ୍",
    "m": "ଏମ୍", "n": "ଏନ୍", "o": "ଓ", "p": "ପି", "q": "କ୍ୟୁ", "r": "ଆର୍",
    "s": "ଏସ୍", "t": "ଟି", "u": "ୟୁ", "v": "ଭି", "w": "ଡବଲ୍ୟୁ",
    "x": "ଏକ୍ସ", "y": "ୱାଇ", "z": "ଜେଡ୍",
}

# ---- abbreviations expanded in the normalization step (regex -> English) ---
ABBREVIATIONS = [
    (r"\bDr\.?(?=\s)", "doctor"),
    (r"\bMr\.?(?=\s)", "mister"),
    (r"\bMrs\.?(?=\s)", "missus"),
    (r"\bProf\.?(?=\s)", "professor"),
    (r"\bRs\.?(?=\s*\d)", "rupees"),
    (r"(?<=\d)\s*km\b", " kilometre"),
    (r"(?<=\d)\s*kg\b", " kilo"),
    (r"\betc\.?", "et cetera"),
    (r"\bvs\.?(?=\s)", "versus"),
]
