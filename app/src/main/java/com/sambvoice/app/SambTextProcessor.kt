package com.sambvoice.app

import java.text.Normalizer

/**
 * Kotlin port of engine/textproc.py (offline, no model needed).
 *
 *  1. normalize()  : numbers, symbols, abbreviations -> spoken English words
 *  2. toOdia()     : text -> Odia script (approximate Sambalpuri flavour)
 *
 * Lookup per Latin word: custom lexicon -> small rule-based fallback.
 * (IndicXlit is not available on the phone; the Python engine can still use it.)
 */
object SambTextProcessor {

    // ------------------------------------------------------------ normalize
    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen"
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun below100(n: Int): String =
        if (n < 20) ones[n] else tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""

    private fun below1000(n: Int): String {
        val h = n / 100
        val r = n % 100
        val sb = StringBuilder()
        if (h > 0) sb.append(ones[h]).append(" hundred")
        if (r > 0) {
            if (h > 0) sb.append(" and ")
            sb.append(below100(r))
        }
        return sb.toString()
    }

    /** Indian-style English words (thousand / lakh / crore). */
    internal fun intToWords(n: Long): String {
        if (n == 0L) return "zero"
        val parts = mutableListOf<String>()
        var rest = n
        val crore = rest / 10_000_000L; rest %= 10_000_000L
        val lakh = rest / 100_000L; rest %= 100_000L
        val thousand = rest / 1000L; rest %= 1000L
        if (crore > 0) parts += intToWords(crore) + " crore"
        if (lakh > 0) parts += below100(lakh.toInt()) + " lakh"
        if (thousand > 0) parts += below100(thousand.toInt()) + " thousand"
        if (rest > 0) {
            val s = below1000(rest.toInt())
            parts += if (parts.isNotEmpty() && rest < 100) "and $s" else s
        }
        return parts.joinToString(" ")
    }

    private fun digitsToWords(digits: String): String =
        digits.filter { it.isDigit() }.map { ones[it - '0'] }.joinToString(" ")

    private fun numberWords(token: String): String {
        val t = token.replace(",", "")
        if ('.' in t) {
            val whole = t.substringBefore('.')
            val frac = t.substringAfter('.')
            val wholeWords = whole.toLongOrNull()?.takeIf { whole.length <= 9 }
                ?.let { intToWords(it) } ?: if (whole.isEmpty()) "zero" else digitsToWords(whole)
            return "$wholeWords point ${digitsToWords(frac)}"
        }
        if (t.length > 9) return digitsToWords(t)          // phone numbers, IDs
        return intToWords(t.toLong())
    }

    private val digitMap: Map<Char, Char> = buildMap {
        "०१२३४५६७८९".forEachIndexed { i, c -> put(c, '0' + i) }
        "୦୧୨୩୪୫୬୭୮୯".forEachIndexed { i, c -> put(c, '0' + i) }
    }
    private val abbreviations: List<Pair<Regex, String>> =
        SambLexicon.ABBREVIATIONS.map { (p, r) -> Regex(p, RegexOption.IGNORE_CASE) to r }
    private val timeColon = Regex("""(?<=\d):(?=\d)""")
    private val rupeeSign = Regex("""₹\s*(\d[\d,]*(?:\.\d+)?)""")
    private val equalsSign = Regex("""(?<=\w)\s*=\s*(?=\w)""")
    private val ordinal = Regex("""\b(\d+)(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)
    private val number = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val strayChars = Regex("""["“”‘’()\[\]{}<>*_#~^|\\/]""")
    private val spaces = Regex("""\s+""")

    fun normalize(input: String): String {
        var text = Normalizer.normalize(input, Normalizer.Form.NFC)
            .map { digitMap[it] ?: it }.joinToString("")

        for ((regex, repl) in abbreviations) text = regex.replace(text) { repl }
        text = timeColon.replace(text, " ")
        text = rupeeSign.replace(text) { it.groupValues[1] + " rupees" }
        text = text.replace("%", " percent ").replace("&", " and ")
            .replace("@", " at ").replace("+", " plus ")
        text = equalsSign.replace(text, " equals ")
        text = ordinal.replace(text) { numberWords(it.groupValues[1]) }   // ordinals read as plain numbers
        text = number.replace(text) { " " + numberWords(it.value) + " " }
        text = strayChars.replace(text, " ")
        return spaces.replace(text, " ").trim()
    }

    // -------------------------------------------------------- transliterate
    private val latinWord = Regex("""[A-Za-z]+(?:'[A-Za-z]+)?""")

    /** Devanagari and Odia blocks are aligned in Unicode (offset 0x200). */
    private fun devaToOdia(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in 0x0900..0x097F && cp != 0x0964 && cp != 0x0965) {
                val mapped = cp + 0x200
                if (Character.getType(mapped) != Character.UNASSIGNED.toInt()) sb.appendCodePoint(mapped)
            } else sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private val pre = listOf(
        "sch" to "sk", "tion" to "shan", "sion" to "shan", "ck" to "k", "qu" to "kw",
        "x" to "ks", "c(?=[eiy])" to "s", "c" to "k"
    ).map { (p, r) -> Regex(p) to r }

    private val cons = mapOf(
        "ksh" to "କ୍ଷ", "kh" to "ଖ", "gh" to "ଘ", "ch" to "ଚ", "jh" to "ଝ", "th" to "ଥ",
        "dh" to "ଧ", "ph" to "ଫ", "bh" to "ଭ", "sh" to "ଶ", "ng" to "ଙ୍ଗ",
        "b" to "ବ", "d" to "ଡ", "f" to "ଫ", "g" to "ଗ", "h" to "ହ", "j" to "ଜ", "k" to "କ",
        "l" to "ଲ", "m" to "ମ", "n" to "ନ", "p" to "ପ", "r" to "ର", "s" to "ସ", "t" to "ଟ",
        "v" to "ଭ", "w" to "ୱ", "y" to "ୟ", "z" to "ଜ"
    )

    /** vowel -> (independent form, matra) */
    private val vow = mapOf(
        "aa" to ("ଆ" to "ା"), "ee" to ("ଈ" to "ୀ"), "oo" to ("ଊ" to "ୂ"), "ai" to ("ଐ" to "ୈ"),
        "ay" to ("ଏ" to "େ"), "au" to ("ଔ" to "ୌ"), "aw" to ("ଔ" to "ୌ"), "ou" to ("ଆଉ" to "ୌ"),
        "ow" to ("ଆଉ" to "ୌ"), "oa" to ("ଓ" to "ୋ"), "ea" to ("ଈ" to "ୀ"), "ie" to ("ଈ" to "ୀ"),
        "a" to ("ଆ" to "ା"), "e" to ("ଏ" to "େ"), "i" to ("ଇ" to "ି"), "o" to ("ଓ" to "ୋ"),
        "u" to ("ଉ" to "ୁ")
    )
    private const val HALANT = "୍"
    private val patternKeys: List<String> = (cons.keys + vow.keys).sortedByDescending { it.length }
    private val doubleCons = Regex("([bdfghjklmnprstvwz])\\1")
    private val anyVowel = Regex("[aeiou]")

    private fun ruleTranslit(word: String): String {
        var w = word.lowercase().replace("'", "")
        for ((regex, rep) in pre) w = regex.replace(w, rep)
        w = doubleCons.replace(w) { it.groupValues[1] }
        if (w.length > 3 && w.endsWith("e") && w[w.length - 2] !in "aeiou" &&
            anyVowel.containsMatchIn(w.dropLast(1))
        ) w = w.dropLast(1)                                         // silent final e

        val out = StringBuilder()
        var afterCons = false
        var i = 0
        while (i < w.length) {
            val key = patternKeys.firstOrNull { w.startsWith(it, i) }
            if (key == null) { i++; continue }
            i += key.length
            val c = cons[key]
            if (c != null) {
                if (afterCons) out.append(HALANT)
                out.append(c)
                afterCons = true
            } else {
                val (independent, matra) = vow.getValue(key)
                out.append(if (afterCons) matra else independent)
                afterCons = false
            }
        }
        return out.toString()
    }

    private fun wordToOdia(word: String): String {
        val low = word.lowercase()
        SambLexicon.WORDS[low]?.let { return it }
        if (low.endsWith("s")) SambLexicon.WORDS[low.dropLast(1)]?.let { return it + "ସ୍" }
        if ('\'' in low) SambLexicon.WORDS[low.substringBefore('\'')]?.let { return it }
        if (word.length in 2..5 && word.all { it.isLetter() && it.isUpperCase() }) {
            return low.mapNotNull { SambLexicon.LETTERS[it] }.joinToString(" ")
        }
        return ruleTranslit(low)
    }

    /** Full text -> Odia-script string, ready for the TTS model. */
    fun toOdia(text: String, normalizeFirst: Boolean = true): String {
        var t = if (normalizeFirst) normalize(text) else text
        t = devaToOdia(t)
        return latinWord.replace(t) { wordToOdia(it.value) }
    }
}
