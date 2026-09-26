package com.edupulse.app.diagram

sealed class PhysicsDiagram {

    data class Kinematics(
        val mass: String? = null,            // e.g. "5 kg"
        val initialVelocity: String? = null, // e.g. "10 m/s"
        val finalVelocity: String? = null,   // e.g. "0 m/s"
        val acceleration: String? = null,    // e.g. "-2.5 m/s²"
        val force: String? = null,           // e.g. "12.5 N"
        val distance: String? = null,        // e.g. "20 m"
        val time: String? = null,            // e.g. "4 s"
        val isDecelerating: Boolean = true
    ) : PhysicsDiagram() {
        val hasData: Boolean
            get() = initialVelocity != null || finalVelocity != null || distance != null || acceleration != null
    }

    data class FreeBody(
        val mass: String? = null,
        val normalForce: String? = null,
        val gravityForce: String? = null,
        val appliedForce: String? = null,
        val frictionForce: String? = null,
        val netForce: String? = null
    ) : PhysicsDiagram()

    data class Projectile(
        val angle: String? = null,
        val velocity: String? = null,
        val range: String? = null,
        val maxHeight: String? = null,
        val timeOfFlight: String? = null
    ) : PhysicsDiagram()

    // ── Mathematics: Interactive Function Grapher ──
    data class MathFunction(
        val equation: String,
        val a: Float = 1f,
        val b: Float = 0f,
        val c: Float = 0f,
        val funcType: String = "QUADRATIC", // "LINEAR", "QUADRATIC", "SIN"
        val vertexX: Float = 0f,
        val vertexY: Float = 0f,
        val root1: Float? = null,
        val root2: Float? = null,
        val yIntercept: Float = 0f
    ) : PhysicsDiagram()

    // ── Chemistry: Interactive Bohr Atomic Model ──
    data class ChemistryAtom(
        val element: String,
        val symbol: String,
        val atomicNumber: Int,
        val massNumber: Int,
        val kShell: Int = 2,
        val lShell: Int = 0,
        val mShell: Int = 0,
        val nShell: Int = 0
    ) : PhysicsDiagram()

    // ── Chemistry: Interactive Acid-Base pH Scale ──
    data class ChemistryPh(
        val substance: String,
        val ph: Float
    ) : PhysicsDiagram()

    // ── Biology: Interactive Punnett Square Genetics Simulator ──
    data class BiologyGenetics(
        val trait: String,
        val p1: String,
        val p2: String,
        val dominantTrait: String = "Dominant",
        val recessiveTrait: String = "Recessive"
    ) : PhysicsDiagram()

    // ── Physics: Interactive Electric Circuit & Ohm's Law ──
    data class ElectricCircuit(
        val voltage: Float = 12f,      // Volts (V)
        val resistance: Float = 4f,     // Ohms (Ω)
        val current: Float = 3f,        // Amperes (A) = V / R
        val power: Float = 36f          // Watts (W) = V * I
    ) : PhysicsDiagram()

    // ── Physics: Simple Pendulum Harmonic Motion ──
    data class HarmonicPendulum(
        val length: Float = 1.0f,       // meters (m)
        val gravity: Float = 9.8f,      // m/s²
        val period: Float = 2.0f,       // seconds T = 2π√(L/g)
        val frequency: Float = 0.5f     // Hz = 1 / T
    ) : PhysicsDiagram()

    // ── Physics: Wave Motion ──
    data class WaveMotion(
        val waveSpeed: Float = 340f,    // m/s
        val frequency: Float = 170f,    // Hz
        val wavelength: Float = 2.0f,   // m = v / f
        val amplitude: Float = 1.0f
    ) : PhysicsDiagram()

    // ── Chemistry: Molecular Reaction & Stoichiometry ──
    data class ChemistryReaction(
        val reactionName: String = "Chemical Reaction",
        val equation: String = "2H₂ + O₂ → 2H₂O",
        val reactants: List<String> = listOf("2H₂", "O₂"),
        val products: List<String> = listOf("2H₂O"),
        val reactionType: String = "Synthesis"
    ) : PhysicsDiagram()

    // ── Biology: Interactive Cell Structure & Organelles ──
    data class BiologyCell(
        val cellType: String = "Plant Cell", // "Plant Cell" or "Animal Cell"
        val keyOrganelles: List<String> = listOf("Nucleus", "Mitochondria", "Cell Membrane", "Cytoplasm", "Chloroplast", "Vacuole")
    ) : PhysicsDiagram()

    // ── Biology: Ecosystem Energy Pyramid ──
    data class EcosystemPyramid(
        val ecosystemName: String = "Terrestrial Food Chain",
        val primaryEnergy: Float = 10000f // Joules
    ) : PhysicsDiagram()
}
