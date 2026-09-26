package com.edupulse.app.diagram

import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.*

/**
 * Extracts a [PhysicsDiagram] from raw LLM response text + the user's original query.
 *
 * Strategy (in priority order):
 *  1. Explicit `[DIAGRAM:TYPE ...]` structured tag emitted by Gemma.
 *  2. Projectile heuristic (keyword detection + SI solver).
 *  3. Free-body heuristic (keyword detection + SI solver).
 *  4. Kinematics heuristic (any 2+ physics quantities found → full analytical solver).
 *
 * All quantities are converted to SI before solving so the renderer always receives
 * correct values regardless of input units (km/h, g, kN, min, cm, etc.).
 */
object DiagramExtractor {

    private val EXPLICIT_TAG_PATTERN: Pattern = Pattern.compile(
        "\\[DIAGRAM:(KINEMATICS|FREE_BODY|PROJECTILE|GRAPH|ATOM|PH|PUNNETT|CIRCUIT|PENDULUM|WAVE|REACTION|CELL|PYRAMID)(.*?)(?:\\]|\\[/DIAGRAM\\])",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    fun stripDiagramTags(text: String): String =
        text.replace(EXPLICIT_TAG_PATTERN.toRegex(), "").trim()

    fun normalizeForPhysics(raw: String): String {
        var text = raw.replace('%', ' ')
        val wordsToDigits = listOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
            "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
            "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
            "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
            "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",
            "eighty" to "80", "ninety" to "90", "hundred" to "100"
        )
        for ((word, digit) in wordsToDigits) {
            text = text.replace(Regex("""\b$word\b""", RegexOption.IGNORE_CASE), digit)
        }
        text = text.replace(Regex("""meters?\s*(?:per|/)\s*sec(?:ond)?s?(?:\s*squared?|\^2|²)?\b""", RegexOption.IGNORE_CASE), "m/s^2")
        text = text.replace(Regex("""metres?\s*(?:per|/)\s*sec(?:ond)?s?(?:\s*squared?|\^2|²)?\b""", RegexOption.IGNORE_CASE), "m/s^2")
        text = text.replace(Regex("""meters?\s*(?:per|/)\s*sec(?:ond)?s?\b""", RegexOption.IGNORE_CASE), "m/s")
        text = text.replace(Regex("""metres?\s*(?:per|/)\s*sec(?:ond)?s?\b""", RegexOption.IGNORE_CASE), "m/s")
        text = text.replace(Regex("""m\s*/\s*sec(?:ond)?s?\b""", RegexOption.IGNORE_CASE), "m/s")
        text = text.replace(Regex("""m\s*/\s*s\b""", RegexOption.IGNORE_CASE), "m/s")
        text = text.replace(Regex("""\bmps\b""", RegexOption.IGNORE_CASE), "m/s")
        text = text.replace(Regex("""\beconds?\b""", RegexOption.IGNORE_CASE), "seconds")
        text = text.replace(Regex("""kilometers?\s*(?:per|/)\s*hours?\b""", RegexOption.IGNORE_CASE), "km/h")
        text = text.replace(Regex("""kilometres?\s*(?:per|/)\s*hours?\b""", RegexOption.IGNORE_CASE), "km/h")
        text = text.replace(Regex("""km\s*/\s*h(?:r)?\b""", RegexOption.IGNORE_CASE), "km/h")
        return text
    }

    fun extract(text: String, query: String = ""): PhysicsDiagram? {
        val normCombined = normalizeForPhysics("$query\n$text")
        extractFromTag(text, query)?.let { return it }
        extractMathHeuristic(normCombined, query)?.let { return it }
        extractChemistryAtomHeuristic(normCombined, query)?.let { return it }
        extractChemistryPhHeuristic(normCombined, query)?.let { return it }
        extractChemistryReactionHeuristic(normCombined, query)?.let { return it }
        extractBiologyGeneticsHeuristic(normCombined, query)?.let { return it }
        extractBiologyCellHeuristic(normCombined, query)?.let { return it }
        extractEcosystemPyramidHeuristic(normCombined, query)?.let { return it }
        extractElectricCircuitHeuristic(normCombined, query)?.let { return it }
        extractHarmonicPendulumHeuristic(normCombined, query)?.let { return it }
        extractWaveMotionHeuristic(normCombined, query)?.let { return it }
        extractProjectileHeuristic(normCombined, query)?.let { return it }
        extractKinematicsHeuristic(normCombined, query)?.let { return it }
        return extractFreeBodyHeuristic(normCombined, query)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SI unit converter
    // ─────────────────────────────────────────────────────────────────────────

    private fun siValue(raw: String): Float? {
        val trimmed = raw.trim()
        val numMatch = Regex("""^(-?[0-9]+(?:\.[0-9]*)?)""").find(trimmed) ?: return null
        val num = numMatch.value.toFloatOrNull() ?: return null
        val unit = trimmed.substring(numMatch.value.length).trim().lowercase()
        return when {
            unit.startsWith("km/h") || unit.startsWith("kmph") || unit.startsWith("kph") -> num / 3.6f
            unit.startsWith("mph")  -> num * 0.44704f
            unit.startsWith("m/s") || unit.contains("per sec") || unit.contains("/sec") || unit.startsWith("mps") -> num
            unit.startsWith("km/s") -> num * 1000f
            unit.startsWith("cm/s") -> num / 100f
            unit == "km" || unit.startsWith("kilomet") -> num * 1000f
            unit.startsWith("cm")   -> num / 100f
            unit.startsWith("mm")   -> num / 1000f
            unit == "m" || unit.startsWith("meter") || unit.startsWith("metre") -> num
            unit.startsWith("kn")   -> num * 1000f
            unit == "n" || unit.startsWith("newton") -> num
            unit.startsWith("kg")   -> num
            unit == "g" || unit.startsWith("gram")  -> num / 1000f
            unit.startsWith("min")  -> num * 60f
            unit == "h" || unit == "hr" || unit.startsWith("hour") -> num * 3600f
            unit == "s" || unit.startsWith("sec") || unit.startsWith("econd")   -> num
            unit == "°" || unit.startsWith("deg")   -> num
            else -> num
        }
    }

    private fun firstSI(text: String, patterns: List<Regex>): Float? {
        for (re in patterns) {
            val g = re.find(text)?.groupValues?.getOrNull(1) ?: continue
            return siValue(g) ?: continue
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regex pattern sets  — each set has progressively looser fallbacks
    // ─────────────────────────────────────────────────────────────────────────

    private const val N = """-?[0-9]+(?:\.[0-9]*)?"""
    private const val VEL_U = """(?:m/s|km/h|kmph|kph|mph|m\s*/\s*s|mps)"""
    private const val DIST_U = """(?:kilometers?|kilometres?|km|centimeters?|centimetres?|cm|millimeters?|millimetres?|mm|meters?|metres?|m\b)"""
    private const val TIME_U = """(?:minutes?|min|hours?|hr|h\b|seconds?|secs?|econds?|s\b)"""
    private const val MASS_U = """(?:kilograms?|kg|grams?|g\b)"""
    private const val ACCEL_U = """(?:m/s(?:\^2|2|²|\s*squared?)?|m\s*s\s*-\s*2|m\s*/\s*s(?:\^2|2|²))"""
    private const val FORCE_U = """(?:kilo\s*newtons?|kN|newtons?|N\b)"""

    private val MASS_PAT = listOf(
        Regex("""(?:mass|m)\s*(?:of\s+(?:the\s+)?(?:body|object|car|block|ball|particle|truck|train|ship|man|person|stone|brick))?\s*(?:of|is|[=:])?\s*($N\s*$MASS_U)""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*k?g)\s*(?:mass|body|object|car|block|ball|truck|train|stone)?""", RegexOption.IGNORE_CASE)
    )

    private val INIT_VEL_PAT = listOf(
        Regex("""(?:initial\s+velocity|initial\s+speed|launch\s+velocity|launch\s+speed|u|v_?0)\s*(?:of|is|[=:])?\s*($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:moves?|moving|travels?|travelling?|traveling?|runs?|running|drives?|driving|goes|going|launched?|thrown?|projected?|kicked?)\s+(?:at|with)?\s*(?:an?\s+(?:initial\s+)?(?:speed|velocity)\s+(?:of\s+)?)?($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:with|at)\s+(?:an?\s+)?(?:initial\s+)?(?:uniform\s+)?(?:speed|velocity)\s+(?:of\s+)?($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:speed|velocity)\s+(?:of|is|=)\s*($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|with)\s+($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*(?:km/h|kmph|kph|mph))""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*m/s)""", RegexOption.IGNORE_CASE)
    )

    private val FINAL_VEL_PAT = listOf(
        Regex("""(?:final\s+velocity|final\s+speed|v_?f?)\s*[=:]\s*($N\s*$VEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:slows?\s+down\s+to|decreases?\s+to|increases?\s+to|reaches?|becomes?)\s*($N\s*$VEL_U)""", RegexOption.IGNORE_CASE)
    )

    private val ACCEL_PAT = listOf(
        Regex("""(?:acceleration|retardation|deceleration)\s*[=:]\s*($N\s*$ACCEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?<![a-z])a\s*[=:]\s*($N\s*$ACCEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""accelerates?\s+(?:uniformly\s+)?(?:at\s+)?($N\s*$ACCEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""with\s+(?:a[n]?\s+)?(?:uniform\s+)?(?:acceleration|retardation)\s+(?:of\s+)?($N\s*$ACCEL_U)""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*m/s(?:\^2|2|²))""", RegexOption.IGNORE_CASE)
    )

    private val FORCE_PAT = listOf(
        Regex("""(?:force|F)\s*(?:applied|retarding|braking|net|of\s+friction)?\s*[=:]\s*($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE),
        Regex("""magnitude\s+of\s+(?:the\s+)?(?:net\s+)?force\s*(?:is\s+)?($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:net\s+)?force\s+(?:of\s+|is\s+)?($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*$FORCE_U)\s+(?:force|thrust|push|pull)""", RegexOption.IGNORE_CASE)
    )

    private val DISTANCE_PAT = listOf(
        Regex("""(?:distance|displacement|s|d)\s*(?:travelled|covered|before\s+stopping|of\s+stopping)?\s*[=:]\s*($N\s*$DIST_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:covers?|covering|travels?\s+a\s+distance\s+of|stops?\s+after(?:\s+(?:covering|travelling?))?|comes\s+to\s+rest\s+(?:after|in))\s+($N\s*(?:km|cm|m\b))""", RegexOption.IGNORE_CASE),
        Regex("""(?:over|in)\s+a\s+(?:total\s+)?distance\s+of\s+($N\s*(?:km|m\b|meters?))""", RegexOption.IGNORE_CASE),
        Regex("""stopping\s+distance\s*(?:is|=|of)?\s*($N\s*(?:km|m\b|meters?))""", RegexOption.IGNORE_CASE),
        Regex("""(?:within|over)\s+($N\s*(?:meters?|m\b))(?!\s*/)""", RegexOption.IGNORE_CASE)
    )

    private val TIME_PAT = listOf(
        Regex("""(?:time|t)\s*(?:taken|of\s+flight)?\s*[=:]\s*($N\s*$TIME_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:in|for|after|within)\s+($N\s*(?:s(?:ec(?:onds?)?)?\b|min(?:utes?)?|h(?:ours?|r)?))""", RegexOption.IGNORE_CASE)
    )

    private val ANGLE_PAT = listOf(
        Regex("""(?:angle|theta|θ)\s*[=:]\s*($N\s*(?:°|deg(?:rees?)?))""", RegexOption.IGNORE_CASE),
        Regex("""at\s+(?:an\s+)?angle\s+of\s+($N\s*(?:°|deg(?:rees?)?))""", RegexOption.IGNORE_CASE),
        Regex("""(?:at|with)\s+($N\s*(?:°|deg(?:rees?)?))""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*(?:°|deg(?:rees?)?)?)\s*(?:to\s+the\s+horizontal|above\s+(?:the\s+)?horizontal)""", RegexOption.IGNORE_CASE),
        Regex("""($N\s*degrees?)""", RegexOption.IGNORE_CASE)
    )

    private val NORMAL_FORCE_PAT = listOf(
        Regex("""(?:normal\s+(?:force|reaction)|Fn|R_N)\s*[=:]\s*($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE)
    )
    private val FRICTION_DIRECT_PAT = listOf(
        Regex("""(?:friction\s+force|force\s+of\s+friction|kinetic\s+friction|f_?k|f_?r)\s*[=:]\s*($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE),
        Regex("""friction\s+(?:force\s+)?(?:of|is|=)\s*($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE)
    )
    private val MU_PAT = listOf(
        Regex("""(?:coefficient\s+of\s+(?:kinetic\s+)?friction|μ_?k?|mu_?k?)\s*[=:]\s*($N)""", RegexOption.IGNORE_CASE)
    )
    private val APPLIED_FORCE_PAT = listOf(
        Regex("""(?:applied|pushing|pulling|external)\s+force\s*[=:]\s*($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE),
        Regex("""(?:applied|pushing|pulling)\s+force\s+(?:of|is)\s+($N\s*$FORCE_U)""", RegexOption.IGNORE_CASE)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Analytical Kinematics Solver
    // ─────────────────────────────────────────────────────────────────────────

    private data class KVars(
        var u: Float? = null,  // initial velocity m/s
        var v: Float? = null,  // final velocity m/s
        var a: Float? = null,  // acceleration m/s² (signed)
        var t: Float? = null,  // time s
        var s: Float? = null,  // distance m (always positive)
        var m: Float? = null,  // mass kg
        var f: Float? = null   // force magnitude N
    )

    private fun solve(k: KVars): KVars {
        var changed = true
        while (changed) {
            changed = false
            fun mark() { changed = true }

            // F = ma (using magnitude of a for F)
            if (k.f == null && k.m != null && k.a != null)                    { k.f = k.m!! * abs(k.a!!); mark() }
            if (k.a == null && k.m != null && k.f != null && k.m!! != 0f)     { k.a = k.f!! / k.m!!; mark() }
            if (k.m == null && k.a != null && k.f != null && k.a!! != 0f)     { k.m = k.f!! / abs(k.a!!); mark() }

            // v = u + at
            if (k.v == null && k.u != null && k.a != null && k.t != null)     { k.v = k.u!! + k.a!! * k.t!!; mark() }
            if (k.u == null && k.v != null && k.a != null && k.t != null)     { k.u = k.v!! - k.a!! * k.t!!; mark() }
            if (k.a == null && k.v != null && k.u != null && k.t != null && k.t!! != 0f) { k.a = (k.v!! - k.u!!) / k.t!!; mark() }
            if (k.t == null && k.a != null && k.a!! != 0f && k.v != null && k.u != null) { k.t = (k.v!! - k.u!!) / k.a!!; mark() }

            // s = ut + ½at²
            if (k.s == null && k.u != null && k.t != null && k.a != null)     { k.s = k.u!! * k.t!! + 0.5f * k.a!! * k.t!! * k.t!!; mark() }
            if (k.u == null && k.s != null && k.t != null && k.a != null && k.t!! != 0f) { k.u = (k.s!! - 0.5f * k.a!! * k.t!! * k.t!!) / k.t!!; mark() }

            // v² = u² + 2as
            if (k.v == null && k.u != null && k.a != null && k.s != null) {
                val v2 = k.u!! * k.u!! + 2f * k.a!! * k.s!!
                if (v2 >= 0f) { k.v = sqrt(v2); mark() }
            }
            if (k.u == null && k.v != null && k.a != null && k.s != null) {
                val u2 = k.v!! * k.v!! - 2f * k.a!! * k.s!!
                if (u2 >= 0f) { k.u = sqrt(u2); mark() }
            }
            if (k.s == null && k.u != null && k.v != null && k.a != null && k.a!! != 0f) {
                k.s = (k.v!! * k.v!! - k.u!! * k.u!!) / (2f * k.a!!); mark()
            }
            if (k.a == null && k.u != null && k.v != null && k.s != null && k.s!! != 0f) {
                k.a = (k.v!! * k.v!! - k.u!! * k.u!!) / (2f * k.s!!); mark()
            }

            // t = 2s / (u + v)  [average velocity]
            if (k.t == null && k.s != null && k.u != null && k.v != null) {
                val avg = k.u!! + k.v!!
                if (abs(avg) > 1e-6f) { k.t = 2f * k.s!! / avg; mark() }
            }
        }
        return k
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build PhysicsDiagram from solved vars
    // ─────────────────────────────────────────────────────────────────────────

    private fun kinematicsFromSolved(k: KVars, isDecel: Boolean): PhysicsDiagram.Kinematics {
        val u    = k.u ?: 10f
        val v    = k.v ?: if (isDecel) 0f else (u + (k.a ?: 2.5f) * (k.t ?: 4f))
        val aAbs = if (k.a != null) abs(k.a!!) else {
            if (k.t != null && k.t!! > 0f) abs(v - u) / k.t!! else 2.5f
        }
        val a    = if (isDecel) -aAbs else aAbs
        val t    = k.t ?: if (aAbs > 0f) abs(v - u) / aAbs else 4f
        val dist = k.s?.let { abs(it) } ?: abs(u * t + 0.5f * a * t * t)
        val m    = k.m ?: 5f
        val fMag = k.f?.let { abs(it) } ?: (m * aAbs)

        return PhysicsDiagram.Kinematics(
            mass            = "%.1f kg".format(Locale.US, m),
            initialVelocity = "%.2f m/s".format(Locale.US, u),
            finalVelocity   = "%.2f m/s".format(Locale.US, v),
            acceleration    = "%.3f m/s²".format(Locale.US, a),
            force           = "%.1f N".format(Locale.US, if (isDecel) -fMag else fMag),
            distance        = "%.2f m".format(Locale.US, dist),
            time            = "%.2f s".format(Locale.US, t),
            isDecelerating  = isDecel
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag extractor  [DIAGRAM:KINEMATICS | u=25 | v=0 | ...]
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractFromTag(text: String, query: String = ""): PhysicsDiagram? {
        val matcher = EXPLICIT_TAG_PATTERN.matcher(text)
        if (!matcher.find()) return null

        val type = matcher.group(1)?.uppercase() ?: return null
        val body = matcher.group(2) ?: ""

        val p = mutableMapOf<String, String>()
        body.split(Regex("[|,\n]")).forEach { pair ->
            val parts = pair.split(Regex("[:=]"), 2)
            if (parts.size == 2) p[parts[0].trim().lowercase()] = parts[1].trim()
        }
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }

        return when (type) {
            "KINEMATICS" -> {
                val k = KVars(
                    u = pf("u", "initial_velocity", "v0"),
                    v = pf("v", "final_velocity"),
                    a = pf("a", "acceleration"),
                    t = pf("t", "time"),
                    s = pf("s", "d", "distance"),
                    m = pf("mass", "m"),
                    f = pf("f", "force")
                )
                val isDecel = (k.v ?: 0f) < (k.u ?: 0f) ||
                    p["a"]?.startsWith("-") == true || p["f"]?.startsWith("-") == true
                if (k.a != null && isDecel) k.a = -abs(k.a!!)
                kinematicsFromSolved(solve(k), isDecel)
            }
            "FREE_BODY"  -> {
                val combined = normalizeForPhysics("$query\n$text")
                extractKinematicsHeuristic(combined, query) ?: buildKinematicsFromFreeBody(p, text, query)
            }
            "PROJECTILE" -> {
                if (p.isEmpty() || (!p.containsKey("angle") && !p.containsKey("theta") && !p.containsKey("v") && !p.containsKey("u"))) {
                    extractProjectileHeuristic(text, query) ?: buildProjectileFromMap(p, text, query)
                } else {
                    buildProjectileFromMap(p, text, query)
                }
            }
            "GRAPH"      -> buildMathGraph(p)
            "ATOM"       -> buildChemistryAtom(p)
            "PH"         -> buildChemistryPh(p)
            "PUNNETT"    -> buildBiologyGenetics(p)
            "CIRCUIT"    -> buildElectricCircuit(p)
            "PENDULUM"   -> buildHarmonicPendulum(p)
            "WAVE"       -> buildWaveMotion(p)
            "REACTION"   -> buildChemistryReaction(p)
            "CELL"       -> buildBiologyCell(p)
            "PYRAMID"    -> buildEcosystemPyramid(p)
            else         -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kinematics heuristic extractor
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractKinematicsHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = normalizeForPhysics("$query\n$text")

        // Strictly verify that the problem is actually about physical motion
        val isMotion = combined.contains(
            Regex("""\b(car|vehicle|truck|train|bus|bicycle|runner|athlete|speed|velocity|accelerat\w*|decelerat\w*|retard\w*|brak\w*|displacement|distance\s+travelled|distance\s+covered|stopping\s+distance|comes\s+to\s+rest|brought\s+to\s+rest|starts?\s+from\s+rest|initial\s+velocity|final\s+velocity|moving|moves?|stops?|stopping)\b""", RegexOption.IGNORE_CASE)
        )
        if (!isMotion) return null

        // --- Mass ---
        val mSI = firstSI(combined, MASS_PAT)

        // --- Initial velocity ---
        var uSI = firstSI(combined, INIT_VEL_PAT)
        if (uSI == null && combined.contains(
                Regex("""(from\s+rest|starts?\s+from\s+rest|initially\s+at\s+rest|stationary\b|at\s+rest\b)""",
                    RegexOption.IGNORE_CASE))) {
            uSI = 0f
        }

        // --- Final velocity ---
        var vSI = firstSI(combined, FINAL_VEL_PAT)
        val isBroughtToRest = combined.contains(
            Regex("""(brought\s+to\s+rest|comes\s+to\s+rest|before\s+stopping|(?<![a-z])stops?\b|v\s*=\s*0)""",
                RegexOption.IGNORE_CASE))
        if (vSI == null && isBroughtToRest) vSI = 0f

        // --- Acceleration ---
        val aSI = firstSI(combined, ACCEL_PAT)

        // --- Force ---
        val fSI = firstSI(combined, FORCE_PAT)

        // --- Distance ---
        val sSI = firstSI(combined, DISTANCE_PAT)

        // --- Time ---
        val tSI = firstSI(combined, TIME_PAT)

        val count = listOfNotNull(mSI, uSI, vSI, aSI, fSI, sSI, tSI).size
        if (count < 2) return null
        if (uSI == null && fSI == null && sSI == null && aSI == null) return null

        val isDecel = isBroughtToRest ||
            (vSI != null && uSI != null && vSI < uSI) ||
            combined.contains(Regex("""(decelerat|retard|brak|slow(?:ing)?\s+down)""", RegexOption.IGNORE_CASE))

        val aSigned: Float? = when {
            aSI != null && isDecel             -> -abs(aSI)
            aSI != null                        -> aSI
            fSI != null && mSI != null && mSI > 0f -> (if (isDecel) -fSI else fSI) / mSI
            else                               -> null
        }

        val k = KVars(u = uSI, v = vSI, a = aSigned, t = tSI, s = sSI, m = mSI, f = fSI)
        val solved = solve(k)

        if (solved.u == null && solved.v == null) return null
        return kinematicsFromSolved(solved, isDecel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Projectile heuristic extractor
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractProjectileHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"
        if (!combined.contains(
                Regex("""(projectile|launched?|kicked?|thrown?|fired|angle\s+of|trajectory|parabolic)""",
                    RegexOption.IGNORE_CASE))) return null

        val angleDeg = firstSI(combined, ANGLE_PAT) ?: 45f
        val v0 = firstSI(combined, INIT_VEL_PAT) ?: return null  // must have launch velocity
        val rangeSI  = firstSI(combined, listOf(
            Regex("""(?:range|horizontal\s+range)\s*[=:]\s*($N\s*(?:km|m\b))""", RegexOption.IGNORE_CASE)
        ))
        val heightSI = firstSI(combined, listOf(
            Regex("""(?:max(?:imum)?\s+height|height)\s*[=:]\s*($N\s*(?:km|m\b))""", RegexOption.IGNORE_CASE)
        ))
        val tFlightSI = firstSI(combined, listOf(
            Regex("""(?:time\s+of\s+flight|T)\s*[=:]\s*($N\s*s)""", RegexOption.IGNORE_CASE)
        ))
        return buildProjectileFromPhysics(angleDeg, v0, rangeSI, heightSI, tFlightSI)
    }

    private fun buildProjectileFromPhysics(
        angleDeg: Float, v0: Float,
        rangeM: Float? = null, heightM: Float? = null, tFlightS: Float? = null
    ): PhysicsDiagram.Projectile {
        val g = 9.8f
        val rad  = angleDeg * PI.toFloat() / 180f
        val sinA = sin(rad)
        val tFlight = tFlightS ?: (2f * v0 * sinA / g)
        val hMax    = heightM  ?: (v0 * v0 * sinA * sinA / (2f * g))
        val range   = rangeM   ?: (v0 * v0 * sin(2f * rad) / g)
        return PhysicsDiagram.Projectile(
            angle        = "%.1f°".format(Locale.US, angleDeg),
            velocity     = "%.1f m/s".format(Locale.US, v0),
            range        = "%.2f m".format(Locale.US, range),
            maxHeight    = "%.2f m".format(Locale.US, hMax),
            timeOfFlight = "%.2f s".format(Locale.US, tFlight)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Free-body heuristic extractor
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractFreeBodyHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"
        if (!combined.contains(
                Regex("""(free[-\s]body|normal\s+force|friction\s+force|coefficient\s+of\s+friction|rough\s+surface)""",
                    RegexOption.IGNORE_CASE))) return null

        val mSI = firstSI(combined, MASS_PAT) ?: return null
        val g   = 9.8f
        val w   = mSI * g

        val normal  = firstSI(combined, NORMAL_FORCE_PAT) ?: w
        val frictionDirect = firstSI(combined, FRICTION_DIRECT_PAT)
        val mu      = firstSI(combined, MU_PAT)
        val friction = frictionDirect ?: (if (mu != null) mu * normal else 0.3f * normal)
        val applied  = firstSI(combined, APPLIED_FORCE_PAT) ?: (friction + mSI * 2f)
        val net      = applied - friction

        return PhysicsDiagram.FreeBody(
            mass          = "%.1f kg".format(Locale.US, mSI),
            normalForce   = "%.1f N".format(Locale.US, normal),
            gravityForce  = "%.1f N".format(Locale.US, w),
            appliedForce  = "%.1f N".format(Locale.US, applied),
            frictionForce = "%.1f N".format(Locale.US, friction),
            netForce      = "%.1f N".format(Locale.US, net)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builders from param maps (used by tag extractor)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildKinematicsFromFreeBody(p: Map<String, String>, text: String = "", query: String = ""): PhysicsDiagram.Kinematics {
        val combined = normalizeForPhysics("$query\n$text")
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val m = pf("mass", "m") ?: firstSI(combined, MASS_PAT) ?: 10f
        val g = 9.8f
        val w = pf("gravity", "w", "mg") ?: (m * g)
        val normal = pf("normal", "fn", "n") ?: w
        val friction = pf("friction", "f_friction", "fk") ?: (0.3f * normal)
        val applied = pf("applied", "fa") ?: (friction + m * 2f)
        val net = pf("net", "fnet") ?: (applied - friction)

        val u = pf("u", "v0", "velocity", "speed") ?: firstSI(combined, INIT_VEL_PAT) ?: 10f
        val isDecel = combined.contains(Regex("""\b(stop\w*|brake\w*|braking|decelerat\w*|retard\w*)\b""", RegexOption.IGNORE_CASE))
        val v = if (isDecel) 0f else (pf("v", "final_velocity") ?: 0f)
        val aMag = abs(net / m).coerceAtLeast(0.5f)
        val a = if (isDecel) -aMag else aMag
        val t = pf("t", "time") ?: firstSI(combined, TIME_PAT) ?: (if (aMag > 0f) abs(v - u) / aMag else 4f)
        val s = pf("s", "d", "distance") ?: firstSI(combined, DISTANCE_PAT) ?: abs(u * t + 0.5f * a * t * t)

        return PhysicsDiagram.Kinematics(
            mass = "%.1f kg".format(Locale.US, m),
            initialVelocity = "%.2f m/s".format(Locale.US, u),
            finalVelocity = "%.2f m/s".format(Locale.US, v),
            acceleration = "%.3f m/s²".format(Locale.US, a),
            force = "%.1f N".format(Locale.US, if (isDecel) -net else net),
            distance = "%.2f m".format(Locale.US, s),
            time = "%.2f s".format(Locale.US, t),
            isDecelerating = isDecel
        )
    }

    private fun buildFreeBody(p: Map<String, String>): PhysicsDiagram.FreeBody {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val m       = pf("mass", "m") ?: 10f
        val g       = 9.8f
        val w       = pf("gravity", "w", "mg") ?: (m * g)
        val normal  = pf("normal", "fn", "n") ?: w
        val friction= pf("friction", "f_friction", "fk") ?: 0.3f * normal
        val applied = pf("applied", "fa") ?: (friction + m * 2f)
        val net     = pf("net", "fnet") ?: (applied - friction)
        return PhysicsDiagram.FreeBody(
            mass          = "%.1f kg".format(Locale.US, m),
            normalForce   = "%.1f N".format(Locale.US, normal),
            gravityForce  = "%.1f N".format(Locale.US, w),
            appliedForce  = "%.1f N".format(Locale.US, applied),
            frictionForce = "%.1f N".format(Locale.US, friction),
            netForce      = "%.1f N".format(Locale.US, net)
        )
    }

    private fun buildProjectileFromMap(p: Map<String, String>, text: String = "", query: String = ""): PhysicsDiagram.Projectile {
        val combined = "$query\n$text"
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val angleDeg = pf("angle", "theta") ?: firstSI(combined, ANGLE_PAT) ?: 45f
        val v0       = pf("u", "v", "velocity") ?: firstSI(combined, INIT_VEL_PAT) ?: 20f
        return buildProjectileFromPhysics(
            angleDeg = angleDeg, v0 = v0,
            rangeM   = pf("range", "r"),
            heightM  = pf("height", "h"),
            tFlightS = pf("time", "t")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mathematics: Function Grapher Builders & Heuristics
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMathGraph(p: Map<String, String>): PhysicsDiagram.MathFunction {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val eq = p["eq"] ?: p["equation"] ?: p["f(x)"] ?: "y = x² - 4"
        val a = pf("a") ?: 1f
        val b = pf("b") ?: 0f
        val c = pf("c") ?: -4f
        val type = p["type"]?.uppercase() ?: if (abs(a) > 1e-4f) "QUADRATIC" else "LINEAR"
        return computeMathFunction(eq, a, b, c, type)
    }

    private fun computeMathFunction(
        eq: String, a: Float, b: Float, c: Float, type: String
    ): PhysicsDiagram.MathFunction {
        val vx = if (abs(a) > 1e-4f) -b / (2f * a) else 0f
        val vy = if (abs(a) > 1e-4f) c - (b * b) / (4f * a) else c
        val disc = b * b - 4f * a * c
        val r1: Float?
        val r2: Float?
        if (abs(a) > 1e-4f) {
            if (disc >= 0f) {
                val sqrtD = sqrt(disc)
                r1 = (-b + sqrtD) / (2f * a)
                r2 = (-b - sqrtD) / (2f * a)
            } else {
                r1 = null; r2 = null
            }
        } else {
            r1 = if (abs(b) > 1e-4f) -c / b else null
            r2 = null
        }
        return PhysicsDiagram.MathFunction(
            equation = eq,
            a = a, b = b, c = c,
            funcType = type,
            vertexX = vx, vertexY = vy,
            root1 = r1, root2 = r2,
            yIntercept = c
        )
    }

    private fun extractMathHeuristic(text: String, query: String): PhysicsDiagram.MathFunction? {
        val combined = "$query\n$text"
        val isMath = combined.contains(Regex("""(graph|plot|parabola|quadratic|f\(x\)|vertex|roots|x\^2|x²)""", RegexOption.IGNORE_CASE))
        if (!isMath) return null

        // Try quadratic: y = ax^2 + bx + c
        val quadMatch = Regex("""(?:y|f\(x\))\s*[=:]\s*(-?[0-9.]*)\s*x(?:\^2|²)\s*([+-]\s*[0-9.]*\s*x)?\s*([+-]\s*[0-9.]+)?""", RegexOption.IGNORE_CASE)
            .find(combined)
        if (quadMatch != null) {
            val aStr = quadMatch.groupValues[1].replace(" ", "").trim()
            val a = when {
                aStr.isEmpty() || aStr == "+" -> 1f
                aStr == "-" -> -1f
                else -> aStr.toFloatOrNull() ?: 1f
            }
            val bStr = quadMatch.groupValues[2].replace(" ", "").trim()
            val b = if (bStr.isNotEmpty()) {
                val bClean = bStr.replace("x", "")
                when (bClean) {
                    "+" -> 1f
                    "-" -> -1f
                    else -> bClean.toFloatOrNull() ?: 0f
                }
            } else 0f
            val cStr = quadMatch.groupValues[3].replace(" ", "").trim()
            val c = if (cStr.isNotEmpty()) cStr.toFloatOrNull() ?: 0f else 0f
            val eq = "y = " + (if (a == 1f) "x²" else if (a == -1f) "-x²" else "${a}x²") +
                (if (b > 0) " + ${b}x" else if (b < 0) " - ${abs(b)}x" else "") +
                (if (c > 0) " + $c" else if (c < 0) " - ${abs(c)}" else "")
            return computeMathFunction(eq, a, b, c, "QUADRATIC")
        }

        // Try simpler quadratic: x^2 - 4
        val simpleQuad = Regex("""x(?:\^2|²)\s*([+-])\s*([0-9.]+)""", RegexOption.IGNORE_CASE).find(combined)
        if (simpleQuad != null) {
            val sign = if (simpleQuad.groupValues[1] == "-") -1f else 1f
            val cVal = (simpleQuad.groupValues[2].toFloatOrNull() ?: 4f) * sign
            return computeMathFunction("y = x² " + (if (cVal >= 0) "+ $cVal" else "- ${abs(cVal)}"), 1f, 0f, cVal, "QUADRATIC")
        }

        // Try linear: y = mx + c
        val linearMatch = Regex("""(?:y|f\(x\))\s*[=:]\s*(-?[0-9.]*)\s*x\s*([+-]\s*[0-9.]+)?""", RegexOption.IGNORE_CASE).find(combined)
        if (linearMatch != null) {
            val mStr = linearMatch.groupValues[1].replace(" ", "").trim()
            val m = when {
                mStr.isEmpty() || mStr == "+" -> 1f
                mStr == "-" -> -1f
                else -> mStr.toFloatOrNull() ?: 1f
            }
            val cStr = linearMatch.groupValues[2].replace(" ", "").trim()
            val c = if (cStr.isNotEmpty()) cStr.toFloatOrNull() ?: 0f else 0f
            return computeMathFunction("y = ${m}x " + (if (c >= 0) "+ $c" else "- ${abs(c)}"), 0f, m, c, "LINEAR")
        }

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chemistry: Bohr Atom Builders & Heuristics
    // ─────────────────────────────────────────────────────────────────────────

    private val ELEMENT_DATABASE = mapOf(
        "hydrogen" to Triple("H", 1, 1),
        "helium" to Triple("He", 2, 4),
        "lithium" to Triple("Li", 3, 7),
        "beryllium" to Triple("Be", 4, 9),
        "boron" to Triple("B", 5, 11),
        "carbon" to Triple("C", 6, 12),
        "nitrogen" to Triple("N", 7, 14),
        "oxygen" to Triple("O", 8, 16),
        "fluorine" to Triple("F", 9, 19),
        "neon" to Triple("Ne", 10, 20),
        "sodium" to Triple("Na", 11, 23),
        "magnesium" to Triple("Mg", 12, 24),
        "aluminium" to Triple("Al", 13, 27),
        "aluminum" to Triple("Al", 13, 27),
        "silicon" to Triple("Si", 14, 28),
        "phosphorus" to Triple("P", 15, 31),
        "sulfur" to Triple("S", 16, 32),
        "chlorine" to Triple("Cl", 17, 35),
        "argon" to Triple("Ar", 18, 40),
        "potassium" to Triple("K", 19, 39),
        "calcium" to Triple("Ca", 20, 40)
    )

    private fun buildChemistryAtom(p: Map<String, String>): PhysicsDiagram.ChemistryAtom {
        val name = (p["element"] ?: "Carbon").replaceFirstChar { it.uppercase() }
        val info = ELEMENT_DATABASE[name.lowercase()]
        val sym = p["symbol"] ?: info?.first ?: "C"
        val z = p["z"]?.toIntOrNull() ?: info?.second ?: 6
        val a = p["a"]?.toIntOrNull() ?: info?.third ?: (z * 2)
        val k = minOf(z, 2)
        val l = minOf(maxOf(z - 2, 0), 8)
        val m = minOf(maxOf(z - 10, 0), 8)
        val n = minOf(maxOf(z - 18, 0), 2)
        return PhysicsDiagram.ChemistryAtom(name, sym, z, a, k, l, m, n)
    }

    private fun extractChemistryAtomHeuristic(text: String, query: String): PhysicsDiagram.ChemistryAtom? {
        val combined = "$query\n$text".lowercase()
        val isAtomTopic = combined.contains(Regex("""(bohr|atomic\s+structure|electron\s+configuration|electron\s+shells?|atomic\s+number|valence\s+electrons?|nucleus)""", RegexOption.IGNORE_CASE))
        if (!isAtomTopic) return null

        for ((elemName, data) in ELEMENT_DATABASE) {
            if (combined.contains(Regex("""\b$elemName\b""", RegexOption.IGNORE_CASE))) {
                val (sym, z, a) = data
                val k = minOf(z, 2)
                val l = minOf(maxOf(z - 2, 0), 8)
                val m = minOf(maxOf(z - 10, 0), 8)
                val n = minOf(maxOf(z - 18, 0), 2)
                return PhysicsDiagram.ChemistryAtom(
                    element = elemName.replaceFirstChar { it.uppercase() },
                    symbol = sym,
                    atomicNumber = z,
                    massNumber = a,
                    kShell = k, lShell = l, mShell = m, nShell = n
                )
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chemistry: Acid-Base pH Scale Builders & Heuristics
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildChemistryPh(p: Map<String, String>): PhysicsDiagram.ChemistryPh {
        val sub = (p["substance"] ?: "Solution").replaceFirstChar { it.uppercase() }
        val ph = p["ph"]?.toFloatOrNull()?.coerceIn(0f, 14f) ?: 7.0f
        return PhysicsDiagram.ChemistryPh(sub, ph)
    }

    private fun extractChemistryPhHeuristic(text: String, query: String): PhysicsDiagram.ChemistryPh? {
        val combined = "$query\n$text"
        val isPhTopic = combined.contains(Regex("""(\bph\b|acidic|alkaline|neutral\s+solution|hydronium|hydroxide\s+ion)""", RegexOption.IGNORE_CASE))
        if (!isPhTopic) return null

        val phMatch = Regex("""\bph\s*(?:of|is|[=:])?\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(combined)
        val phVal = phMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        val subMatch = Regex("""(?:ph\s+of\s+([a-zA-Z\s]+?)(?:\s+is|\s*=\s*|\s*,|\s*\.|\s*has))""", RegexOption.IGNORE_CASE).find(combined)
        val substance = subMatch?.groupValues?.getOrNull(1)?.trim()?.replaceFirstChar { it.uppercase() }
            ?: when {
                combined.contains("lemon", true) -> "Lemon Juice"
                combined.contains("coffee", true) -> "Black Coffee"
                combined.contains("water", true) -> "Pure Water"
                combined.contains("blood", true) -> "Human Blood"
                combined.contains("bleach", true) -> "Household Bleach"
                combined.contains("soap", true) -> "Soapy Water"
                combined.contains("hcl", true) || combined.contains("hydrochloric", true) -> "Hydrochloric Acid (HCl)"
                combined.contains("naoh", true) || combined.contains("sodium hydroxide", true) -> "Sodium Hydroxide (NaOH)"
                else -> "Tested Solution"
            }

        val resolvedPh = phVal ?: when {
            substance.contains("Lemon", true) -> 2.2f
            substance.contains("Coffee", true) -> 5.0f
            substance.contains("Water", true) -> 7.0f
            substance.contains("Blood", true) -> 7.4f
            substance.contains("Bleach", true) -> 12.5f
            substance.contains("Soap", true) -> 10.0f
            substance.contains("HCl", true) -> 1.0f
            substance.contains("NaOH", true) -> 13.5f
            else -> 7.0f
        }

        return PhysicsDiagram.ChemistryPh(substance, resolvedPh.coerceIn(0f, 14f))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Biology: Punnett Square Genetics Builders & Heuristics
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildBiologyGenetics(p: Map<String, String>): PhysicsDiagram.BiologyGenetics {
        val trait = p["trait"] ?: "Plant Height"
        val p1 = p["p1"] ?: "Bb"
        val p2 = p["p2"] ?: "Bb"
        val dom = p["dominant"] ?: "Dominant"
        val rec = p["recessive"] ?: "Recessive"
        return PhysicsDiagram.BiologyGenetics(trait, p1, p2, dom, rec)
    }

    private fun extractBiologyGeneticsHeuristic(text: String, query: String): PhysicsDiagram.BiologyGenetics? {
        val combined = "$query\n$text"
        val isGenetics = combined.contains(Regex("""(punnett|genetics|monohybrid|heterozygous|homozygous|alleles?|phenotype|genotype|mendel)""", RegexOption.IGNORE_CASE))
        if (!isGenetics) return null

        val crossMatch = Regex("""\b([A-Za-z]{2})\s*(?:x|×|and)\s*([A-Za-z]{2})\b""").find(combined)
        val p1 = crossMatch?.groupValues?.getOrNull(1) ?: "Bb"
        val p2 = crossMatch?.groupValues?.getOrNull(2) ?: "Bb"

        val trait = when {
            combined.contains("height", true) || combined.contains("tall", true) -> "Plant Height (Tall / Dwarf)"
            combined.contains("eye", true) -> "Eye Color (Brown / Blue)"
            combined.contains("seed", true) -> "Seed Shape (Round / Wrinkled)"
            combined.contains("flower", true) || combined.contains("color", true) -> "Flower Color (Purple / White)"
            else -> "Inherited Trait"
        }

        val dom = when {
            combined.contains("tall", true) -> "Tall"
            combined.contains("brown", true) -> "Brown Eyes"
            combined.contains("round", true) -> "Round Seed"
            combined.contains("purple", true) -> "Purple"
            else -> "Dominant Trait"
        }

        val rec = when {
            combined.contains("dwarf", true) || combined.contains("short", true) -> "Dwarf"
            combined.contains("blue", true) -> "Blue Eyes"
            combined.contains("wrinkled", true) -> "Wrinkled Seed"
            combined.contains("white", true) -> "White"
            else -> "Recessive Trait"
        }

        return PhysicsDiagram.BiologyGenetics(trait, p1, p2, dom, rec)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Physics: Electric Circuit & Ohm's Law
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildElectricCircuit(p: Map<String, String>): PhysicsDiagram.ElectricCircuit {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val v = pf("voltage", "v") ?: 12f
        val r = pf("resistance", "r") ?: 4f
        val i = pf("current", "i") ?: if (r > 0f) v / r else 0f
        val power = pf("power", "p") ?: (v * i)
        return PhysicsDiagram.ElectricCircuit(v, r, i, power)
    }

    private fun extractElectricCircuitHeuristic(text: String, query: String): PhysicsDiagram.ElectricCircuit? {
        val combined = "$query\n$text"
        val isCircuit = combined.contains(Regex("""(circuit|resistor|resistance|ohm|voltage|potential\s+difference|current|ampere|amps?\b|ohm's\s+law|v\s*=\s*i\s*r)""", RegexOption.IGNORE_CASE))
        if (!isCircuit) return null

        val vMatch = Regex("""\b($N)\s*(?:v\b|volts?)\b""", RegexOption.IGNORE_CASE).find(combined)
        val vVal = vMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        val rMatch = Regex("""\b($N)\s*(?:Ω|ohms?)\b""", RegexOption.IGNORE_CASE).find(combined)
        val rVal = rMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        val iMatch = Regex("""\b($N)\s*(?:a\b|amps?|amperes?)\b""", RegexOption.IGNORE_CASE).find(combined)
        val iVal = iMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        if (vVal == null && rVal == null && iVal == null && !combined.contains("ohm", true) && !combined.contains("circuit", true)) {
            return null
        }

        val v = vVal ?: if (iVal != null && rVal != null) iVal * rVal else 12f
        val r = rVal ?: if (vVal != null && iVal != null && iVal > 0f) vVal / iVal else 4f
        val i = iVal ?: if (r > 0f) v / r else 3f
        val p = v * i

        return PhysicsDiagram.ElectricCircuit(
            voltage = v,
            resistance = r,
            current = i,
            power = p
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Physics: Harmonic Pendulum
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildHarmonicPendulum(p: Map<String, String>): PhysicsDiagram.HarmonicPendulum {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val l = pf("length", "l") ?: 1.0f
        val g = pf("gravity", "g") ?: 9.8f
        val t = pf("period", "t") ?: (2f * PI.toFloat() * sqrt(l / g))
        val f = pf("frequency", "f") ?: (1f / t)
        return PhysicsDiagram.HarmonicPendulum(l, g, t, f)
    }

    private fun extractHarmonicPendulumHeuristic(text: String, query: String): PhysicsDiagram.HarmonicPendulum? {
        val combined = "$query\n$text"
        val isPendulum = combined.contains(Regex("""(simple\s+pendulum|pendulum|oscillat\w*|harmonic\s+motion|bob\b)""", RegexOption.IGNORE_CASE))
        if (!isPendulum) return null

        val lMatch = Regex("""(?:length|l)\s*[=:]\s*($N)\s*(?:m\b|cm\b|meters?)""", RegexOption.IGNORE_CASE).find(combined)
            ?: Regex("""\b($N)\s*(?:m\b|meters?)\s*(?:long|length|pendulum)""", RegexOption.IGNORE_CASE).find(combined)
        val rawL = lMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        val l = if (lMatch?.value?.contains("cm", true) == true) rawL / 100f else rawL

        val gMatch = Regex("""(?:gravity|g)\s*[=:]\s*($N)""", RegexOption.IGNORE_CASE).find(combined)
        val g = gMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 9.8f

        val period = 2f * PI.toFloat() * sqrt(l / g)
        val freq = 1f / period

        return PhysicsDiagram.HarmonicPendulum(
            length = l,
            gravity = g,
            period = period,
            frequency = freq
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Physics: Wave Motion
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildWaveMotion(p: Map<String, String>): PhysicsDiagram.WaveMotion {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val v = pf("v", "speed", "velocity") ?: 340f
        val f = pf("f", "freq", "frequency") ?: 170f
        val lambda = pf("lambda", "wavelength", "l") ?: (v / f)
        val amp = pf("amp", "amplitude", "a") ?: 1.0f
        return PhysicsDiagram.WaveMotion(v, f, lambda, amp)
    }

    private fun extractWaveMotionHeuristic(text: String, query: String): PhysicsDiagram.WaveMotion? {
        val combined = "$query\n$text"
        val isWave = combined.contains(Regex("""(wavelength|wave\s+speed|wave\s+velocity|frequency\s+of\s+wave|transverse\s+wave|sound\s+wave|v\s*=\s*f\s*[λ\\]|v\s*=\s*f\s*lambda)""", RegexOption.IGNORE_CASE))
        if (!isWave) return null

        val fMatch = Regex("""\b($N)\s*(?:hz|khz|mhz|hertz)\b""", RegexOption.IGNORE_CASE).find(combined)
        val fVal = fMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let {
            if (fMatch.value.contains("khz", true)) it * 1000f
            else if (fMatch.value.contains("mhz", true)) it * 1000000f
            else it
        }

        val lMatch = Regex("""\b($N)\s*(?:m\b|cm\b|mm\b|nm\b|meters?)\s*(?:wavelength|lambda|[λ\\])?""", RegexOption.IGNORE_CASE).find(combined)
        val lVal = lMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        val vMatch = Regex("""\b($N)\s*(?:m/s|km/s)\b""", RegexOption.IGNORE_CASE).find(combined)
        val vVal = vMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

        val v = vVal ?: if (fVal != null && lVal != null) fVal * lVal else 340f
        val f = fVal ?: if (vVal != null && lVal != null && lVal > 0f) vVal / lVal else 170f
        val lambda = lVal ?: if (f > 0f) v / f else 2.0f

        return PhysicsDiagram.WaveMotion(
            waveSpeed = v,
            frequency = f,
            wavelength = lambda,
            amplitude = 1.0f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chemistry: Chemical Reaction & Stoichiometry
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildChemistryReaction(p: Map<String, String>): PhysicsDiagram.ChemistryReaction {
        val name = p["name"] ?: "Chemical Reaction"
        val eq = p["eq"] ?: p["equation"] ?: "2H₂ + O₂ → 2H₂O"
        val type = p["type"] ?: "Synthesis"
        val parts = eq.split(Regex("[→=]|->"))
        val reactants = if (parts.isNotEmpty()) parts[0].split("+").map { it.trim() }.filter { it.isNotEmpty() } else listOf("2H₂", "O₂")
        val products = if (parts.size > 1) parts[1].split("+").map { it.trim() }.filter { it.isNotEmpty() } else listOf("2H₂O")
        return PhysicsDiagram.ChemistryReaction(name, eq, reactants, products, type)
    }

    private fun extractChemistryReactionHeuristic(text: String, query: String): PhysicsDiagram.ChemistryReaction? {
        val combined = "$query\n$text"
        val isReaction = combined.contains(Regex("""(chemical\s+reaction|reaction|balanced\s+equation|stoichiometry|reactants?|products?|synthesis|decomposition|combustion)""", RegexOption.IGNORE_CASE))
        if (!isReaction) return null

        val eqMatch = Regex("""([0-9A-Za-z\(\)\s\+]+)\s*(?:→|->|=)\s*([0-9A-Za-z\(\)\s\+]+)""").find(combined)
        if (eqMatch == null) return null

        val lhs = eqMatch.groupValues[1].trim()
        val rhs = eqMatch.groupValues[2].trim()
        if (lhs.length < 2 || rhs.length < 2) return null

        val reactants = lhs.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        val products = rhs.split("+").map { it.trim() }.filter { it.isNotEmpty() }

        val type = when {
            reactants.size > 1 && products.size == 1 -> "Synthesis"
            reactants.size == 1 && products.size > 1 -> "Decomposition"
            combined.contains("combustion", true) || (lhs.contains("O2", true) && rhs.contains("CO2", true)) -> "Combustion"
            combined.contains("neutraliz", true) || (lhs.contains("HCl", true) || lhs.contains("H2SO4", true)) -> "Neutralization"
            else -> "Chemical Reaction"
        }

        val name = when {
            combined.contains("water", true) || (lhs.contains("H2", true) && lhs.contains("O2", true)) -> "Formation of Water"
            combined.contains("photosynthesis", true) -> "Photosynthesis"
            combined.contains("ammonia", true) || lhs.contains("N2", true) -> "Haber-Bosch Ammonia Synthesis"
            combined.contains("methane", true) || lhs.contains("CH4", true) -> "Combustion of Methane"
            combined.contains("calcium", true) -> "Decomposition of Calcium Carbonate"
            else -> "$type Reaction"
        }

        return PhysicsDiagram.ChemistryReaction(
            reactionName = name,
            equation = "$lhs → $rhs",
            reactants = reactants,
            products = products,
            reactionType = type
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Biology: Cell Structure & Organelles
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildBiologyCell(p: Map<String, String>): PhysicsDiagram.BiologyCell {
        val type = p["type"] ?: "Plant Cell"
        val organelles = if (type.contains("Plant", true)) {
            listOf("Cell Wall", "Cell Membrane", "Nucleus", "Mitochondria", "Chloroplast", "Large Central Vacuole", "Cytoplasm")
        } else {
            listOf("Cell Membrane", "Nucleus", "Mitochondria", "Ribosomes", "Endoplasmic Reticulum", "Small Vacuoles", "Cytoplasm")
        }
        return PhysicsDiagram.BiologyCell(type, organelles)
    }

    private fun extractBiologyCellHeuristic(text: String, query: String): PhysicsDiagram.BiologyCell? {
        val combined = "$query\n$text"
        val isCell = combined.contains(Regex("""(cell\s+structure|plant\s+cell|animal\s+cell|organelles?|nucleus|mitochondria|chloroplast|vacuole|cytoplasm|eukaryotic)""", RegexOption.IGNORE_CASE))
        if (!isCell) return null

        val isPlant = combined.contains(Regex("""(plant\s+cell|chloroplast|cell\s+wall|photosynthesis|plant)""", RegexOption.IGNORE_CASE))
        val type = if (isPlant) "Plant Cell" else "Animal Cell"

        val organelles = if (isPlant) {
            listOf("Cell Wall", "Cell Membrane", "Nucleus", "Mitochondria", "Chloroplast", "Large Central Vacuole", "Cytoplasm")
        } else {
            listOf("Cell Membrane", "Nucleus", "Mitochondria", "Ribosomes", "Endoplasmic Reticulum", "Small Vacuoles", "Cytoplasm")
        }

        return PhysicsDiagram.BiologyCell(cellType = type, keyOrganelles = organelles)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Biology: Ecosystem Energy Pyramid
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildEcosystemPyramid(p: Map<String, String>): PhysicsDiagram.EcosystemPyramid {
        val name = p["name"] ?: "Terrestrial Food Chain"
        val energy = p["energy"]?.toFloatOrNull() ?: 10000f
        return PhysicsDiagram.EcosystemPyramid(name, energy)
    }

    private fun extractEcosystemPyramidHeuristic(text: String, query: String): PhysicsDiagram.EcosystemPyramid? {
        val combined = "$query\n$text"
        val isPyramid = combined.contains(Regex("""(energy\s+pyramid|trophic\s+level|food\s+chain|food\s+web|10%\s+rule|ten\s+percent\s+rule|primary\s+producer|primary\s+consumer|apex\s+predator)""", RegexOption.IGNORE_CASE))
        if (!isPyramid) return null

        val energyMatch = Regex("""\b([0-9]+(?:\.[0-9]+)?)\s*(?:j|joules?|kj|kilojoules?)\b""", RegexOption.IGNORE_CASE).find(combined)
        val rawEnergy = energyMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 10000f
        val energy = if (energyMatch?.value?.contains("kj", true) == true) rawEnergy * 1000f else rawEnergy

        val name = when {
            combined.contains("ocean", true) || combined.contains("marine", true) || combined.contains("aquatic", true) -> "Marine Aquatic Ecosystem"
            combined.contains("forest", true) -> "Forest Ecosystem"
            combined.contains("grassland", true) -> "Grassland Food Chain"
            else -> "Terrestrial Energy Pyramid"
        }

        return PhysicsDiagram.EcosystemPyramid(
            ecosystemName = name,
            primaryEnergy = energy.coerceIn(100f, 1000000f)
        )
    }
}
