package com.edupulse.app.ocr

/**
 * Dynamic OCR correction logic — direct Kotlin port of the Python
 * pipeline's confusion-matrix approach. No hardcoded word replacements.
 */
object OcrCorrection {

    /** Characters that handwriting OCR commonly swaps with digits. */
    private val CHAR_TO_DIGIT = mapOf(
        'o' to '0', 'O' to '0',
        'l' to '1', 'I' to '1', 'i' to '1',
        'z' to '2', 'Z' to '2',
        'u' to '4', 'U' to '4',
        's' to '5', 'S' to '5',
        'b' to '6',
        'B' to '8',
        'q' to '9'
    )

    private val UNITS = setOf(
        "N", "kg", "g", "mg", "m/s", "m/s²", "mol", "L", "cm", "mm", "km", "m", "s",
        "seconds", "second", "sec", "secs", "meters", "metres", "hours", "hour", "hr", "hrs",
        "minutes", "minute", "min", "mins", "Hz", "Pa", "J", "W", "kJ", "kW", "kPa", "atm", "mL"
    )

    private val unitsLookupLower = UNITS.map { it.lowercase() }.toSet()

    // Sorted longest-first so "m/s²" matches before "m" or "s"
    private val unitsSorted = UNITS.sortedByDescending { it.length }
    private val unitSuffixRegex = Regex(
        "^(.+?)(" + unitsSorted.joinToString("|") { Regex.escape(it) } + ")$"
    )

    /**
     * Try to convert a prefix string into digits using the confusion matrix.
     * Returns the digit string if every char maps, or null otherwise.
     */
    private fun tryDigitRecovery(prefix: String): String? {
        val digits = StringBuilder()
        for (ch in prefix) {
            when {
                ch.isDigit() -> digits.append(ch)
                ch in CHAR_TO_DIGIT -> digits.append(CHAR_TO_DIGIT[ch])
                else -> return null
            }
        }
        return if (digits.isNotEmpty()) digits.toString() else null
    }

    /**
     * Dynamically recover digit+unit tokens corrupted by OCR.
     *
     * Handles both attached units (e.g. "skg" → "5 kg", "l5N" → "15 N")
     * and space-separated number+unit pairs (e.g. "io m/s" → "10 m/s", "u seconds" → "4 seconds").
     * Also checks domain-specific OKF confusion mapping (e.g. "isn" → "15N").
     *
     * Returns (cleanedText, listOfFixes).
     */
    fun recoverDigitsNearUnits(
        text: String,
        extraConfusions: Map<String, String> = emptyMap()
    ): Pair<String, List<Map<String, String>>> {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val fixes = mutableListOf<Map<String, String>>()
        val out = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]

            // 0. Check OKF knowledge confusion dictionary (e.g. "isn" -> "15N")
            val knownFix = extraConfusions[tok] ?: extraConfusions[tok.lowercase()]
            if (knownFix != null) {
                fixes.add(mapOf("original" to tok, "fixed" to knownFix))
                out.add(knownFix)
                i++
                continue
            }

            // 1. Check attached unit suffix (e.g. "iom/s" -> "10 m/s", "5kg" -> "5 kg")
            val match = unitSuffixRegex.find(tok)
            if (match != null) {
                val prefix = match.groupValues[1]
                val unit = match.groupValues[2]
                val recovered = tryDigitRecovery(prefix)
                if (recovered != null && recovered != prefix) {
                    val fixed = "$recovered $unit"
                    fixes.add(mapOf("original" to tok, "fixed" to fixed))
                    out.add(fixed)
                    i++
                    continue
                }
            }

            // 2. Check separated token pair: token[i] followed by token[i+1] which is a unit
            // e.g. "io" followed by "m/s" -> "10", or "u" followed by "seconds" -> "4"
            if (i + 1 < tokens.size) {
                val nextTokClean = tokens[i + 1].trimEnd('.', ',', ';')
                if (nextTokClean.lowercase() in unitsLookupLower || nextTokClean in UNITS) {
                    val recovered = tryDigitRecovery(tok)
                    if (recovered != null && recovered != tok) {
                        fixes.add(mapOf("original" to "$tok ${tokens[i + 1]}", "fixed" to "$recovered ${tokens[i + 1]}"))
                        out.add(recovered)
                        i++
                        continue
                    }
                }
            }

            out.add(tok)
            i++
        }
        return Pair(out.joinToString(" "), fixes)
    }

    /**
     * Pattern-based cleanup for common OCR unit format corruptions.
     */
    fun fixUnitOcrErrors(text: String): String {
        var result = text
        // Standardize acceleration units (standalone or glued e.g. 5mls2)
        result = result.replace(Regex("(?<=[0-9a-zA-Z])m[l1i]s2\\b", RegexOption.IGNORE_CASE), " m/s²")
        result = result.replace(Regex("\\bm[l1i]s2\\b", RegexOption.IGNORE_CASE), "m/s²")
        result = result.replace(Regex("\\bms[-_]?2\\b", RegexOption.IGNORE_CASE), "m/s²")
        result = result.replace(Regex("\\bm/s2\\b", RegexOption.IGNORE_CASE), "m/s²")

        // Standardize velocity units (standalone or glued e.g. lomls -> lo m/s)
        result = result.replace(Regex("(?<=[0-9a-zA-Z])m[l1i]s\\b", RegexOption.IGNORE_CASE), " m/s")
        result = result.replace(Regex("\\bm[l1i]s\\b", RegexOption.IGNORE_CASE), "m/s")
        result = result.replace(Regex("\\bms[-_]?1\\b", RegexOption.IGNORE_CASE), "m/s")
        return result
    }
}
