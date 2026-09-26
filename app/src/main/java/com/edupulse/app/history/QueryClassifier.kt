package com.edupulse.app.history

import com.edupulse.app.diagram.PhysicsDiagram

enum class QuerySubject(val displayName: String, val badgeText: String, val emoji: String) {
    PROJECTILE("Projectile Motion", "Projectile", "🚀"),
    KINEMATICS("1D Kinematics", "Motion", "🚗"),
    FORCES("Newton's Laws & Forces", "Forces", "⚖️"),
    PHYSICS("General Physics", "Physics", "⚛️"),
    MATH("Mathematics", "Math", "📐"),
    CHEMISTRY("Chemistry", "Chemistry", "🧪"),
    BIOLOGY("Biology", "Biology", "🧬"),
    GENERAL("General Query", "General", "💬");

    companion object {
        fun fromName(name: String?): QuerySubject {
            if (name == null) return GENERAL
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: GENERAL
        }
    }
}

object QueryClassifier {

    private val CASUAL_GREETINGS = setOf(
        "hi", "hello", "hey", "hola", "namaste", "sup", "yo",
        "good morning", "good afternoon", "good evening", "good night",
        "thanks", "thank you", "ok", "okay", "bye", "goodbye",
        "who are you", "what are you", "what can you do", "help", "help me",
        "how are you", "what's up", "whats up", "tell me a joke",
        "test", "testing", "are you there", "can you hear me"
    )

    /**
     * Classifies a user's question into its academic subject or marks it as GENERAL
     * if the query is casual, conversational, or vague.
     */
    fun classify(question: String, diagram: PhysicsDiagram?): QuerySubject {
        // 1. If an interactive physics diagram was extracted, prioritize that domain
        when (diagram) {
            is PhysicsDiagram.Projectile -> return QuerySubject.PROJECTILE
            is PhysicsDiagram.Kinematics -> return QuerySubject.KINEMATICS
            is PhysicsDiagram.FreeBody -> return QuerySubject.FORCES
            is PhysicsDiagram.ElectricCircuit -> return QuerySubject.PHYSICS
            is PhysicsDiagram.HarmonicPendulum -> return QuerySubject.PHYSICS
            is PhysicsDiagram.WaveMotion -> return QuerySubject.PHYSICS
            is PhysicsDiagram.MathFunction -> return QuerySubject.MATH
            is PhysicsDiagram.ChemistryAtom -> return QuerySubject.CHEMISTRY
            is PhysicsDiagram.ChemistryPh -> return QuerySubject.CHEMISTRY
            is PhysicsDiagram.ChemistryReaction -> return QuerySubject.CHEMISTRY
            is PhysicsDiagram.BiologyCell -> return QuerySubject.BIOLOGY
            is PhysicsDiagram.BiologyGenetics -> return QuerySubject.BIOLOGY
            is PhysicsDiagram.EcosystemPyramid -> return QuerySubject.BIOLOGY
            else -> Unit
        }

        val text = question.trim().lowercase()

        // 2. Check for casual greetings or vague conversational inputs
        if (text.length < 4 || CASUAL_GREETINGS.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return QuerySubject.GENERAL
        }

        // 3. Projectile Motion
        if (text.contains("projectile") || text.contains("trajectory") || text.contains("cannon") ||
            (text.contains("angle") && (text.contains("fired") || text.contains("thrown") || text.contains("launch")))) {
            return QuerySubject.PROJECTILE
        }

        // 4. 1D Kinematics
        if (text.contains("velocity") || text.contains("acceleration") || text.contains("deceleration") ||
            text.contains("braking") || text.contains("speed") || text.contains("m/s") || text.contains("m/s²") ||
            text.contains("stopping distance") || text.contains("displacement")) {
            return QuerySubject.KINEMATICS
        }

        // 5. Forces & Newton's Laws
        if (text.contains("newton") || text.contains("friction") || text.contains("normal force") ||
            text.contains("gravity") || text.contains("tension") || text.contains("pulley") ||
            (text.contains("force") && text.contains("mass")) || text.contains("inclined plane")) {
            return QuerySubject.FORCES
        }

        // 6. General Physics
        if (text.contains("energy") || text.contains("momentum") || text.contains("joule") ||
            text.contains("watt") || text.contains("power") || text.contains("work done") ||
            text.contains("optics") || text.contains("refraction") || text.contains("lens") ||
            text.contains("ohm") || text.contains("current") || text.contains("voltage") ||
            text.contains("resistor") || text.contains("frequency") || text.contains("wavelength") ||
            text.contains("thermodynamics") || text.contains("pressure")) {
            return QuerySubject.PHYSICS
        }

        // 7. Chemistry
        if (text.contains("mole") || text.contains("molarity") || text.contains("reaction") ||
            text.contains("acid") || text.contains("base") || text.contains("ph ") ||
            text.contains("stp") || text.contains("titration") || text.contains("element") ||
            text.contains("atom") || text.contains("compound") || text.contains("catalyst") ||
            text.contains("chemical") || text.contains("avogadro")) {
            return QuerySubject.CHEMISTRY
        }

        // 8. Mathematics
        if (text.contains("solve for") || text.contains("equation") || text.contains("derivative") ||
            text.contains("integral") || text.contains("matrix") || text.contains("triangle") ||
            text.contains("polynomial") || text.contains("algebra") || text.contains("logarithm") ||
            text.contains("calculus") || text.contains("quadratic") || text.contains("geometry")) {
            return QuerySubject.MATH
        }

        // 9. Numeric / Formula check
        val hasDigits = text.any { it.isDigit() }
        val hasMathOp = text.contains("=") || text.contains("+") || text.contains("×") || text.contains("/")
        if (hasDigits && hasMathOp) {
            return QuerySubject.MATH
        }

        // 10. Fallback: if no domain cues or formulas found, mark as GENERAL
        return QuerySubject.GENERAL
    }
}
