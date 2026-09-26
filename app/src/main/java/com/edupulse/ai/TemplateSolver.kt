package com.edupulse.ai

import com.edupulse.model.ProblemExtraction
import java.util.regex.Pattern

object TemplateSolver {
    /**
     * Regex fallback solver when LLM inference is unavailable or delayed.
     * Extracts parameters from recognized OCR text.
     */
    fun extractFromText(rawText: String): ProblemExtraction? {
        val lower = rawText.lowercase()

        // 1. Check for Projectile Motion keywords
        if (lower.contains("projectile") || lower.contains("fired") || lower.contains("cannon") || lower.contains("angle") || lower.contains("degree")) {
            val velocity = extractNumberBeforeUnit(rawText, listOf("m/s", "ms-1", "mps")) ?: 25.0
            val angle = extractNumberBeforeUnit(rawText, listOf("°", "degrees", "deg", "degree")) ?: 45.0
            return ProblemExtraction(
                topic = "projectile_motion",
                knowns = mapOf(
                    "u" to velocity,
                    "angle" to angle,
                    "g" to 9.8
                ),
                target = if (lower.contains("height")) "max_height" else if (lower.contains("time")) "time_of_flight" else "range",
                explanationEn = "Extracted projectile problem: launch velocity $velocity m/s at $angle° under gravity 9.8 m/s²."
            )
        }

        // 2. Check for Newton's 2nd Law keywords
        if (lower.contains("force") || lower.contains("newton") || lower.contains("mass") || lower.contains("accelerat")) {
            val force = extractNumberBeforeUnit(rawText, listOf("n", "newton", "newtons"))
            val mass = extractNumberBeforeUnit(rawText, listOf("kg", "kilogram", "g", "grams"))
            val acc = extractNumberBeforeUnit(rawText, listOf("m/s²", "m/s2", "ms-2"))
            val vel = extractNumberBeforeUnit(rawText, listOf("m/s", "ms-1"))

            if (force != null || (mass != null && (acc != null || vel != null))) {
                return ProblemExtraction(
                    topic = "newton_second_law",
                    knowns = mapOf(
                        "force" to force,
                        "mass" to mass,
                        "a" to acc,
                        "u" to vel
                    ),
                    target = if (acc == null) "acceleration" else if (force == null) "force" else "mass",
                    explanationEn = "Extracted Newton's 2nd law problem: mass ${mass ?: "m"} kg, force ${force ?: "F"} N."
                )
            }
        }

        // 3. Fallback to 1D Kinematics
        val velocity = extractNumberBeforeUnit(rawText, listOf("m/s", "ms-1")) ?: 10.0
        val acc = extractNumberBeforeUnit(rawText, listOf("m/s²", "m/s2", "ms-2")) ?: 2.0
        val time = extractNumberBeforeUnit(rawText, listOf("s", "sec", "seconds"))
        val dist = extractNumberBeforeUnit(rawText, listOf("m", "meter", "meters"))

        return ProblemExtraction(
            topic = "kinematics_1d",
            knowns = mapOf(
                "u" to velocity,
                "a" to acc,
                "t" to time,
                "s" to dist
            ),
            target = if (dist == null) "distance" else "final_velocity",
            explanationEn = "Extracted 1D kinematics problem: initial velocity $velocity m/s, acceleration $acc m/s²."
        )
    }

    private fun extractNumberBeforeUnit(text: String, units: List<String>): Double? {
        for (unit in units) {
            val pattern = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*" + Pattern.quote(unit))
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.toDoubleOrNull()
            }
        }
        return null
    }
}
