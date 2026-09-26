package com.edupulse.solver

import com.edupulse.model.SolverResult
import kotlin.math.*

object KinematicSolvers {
    private const val G = 9.8

    /**
     * Topic 1: 1D Kinematics (kinematics_1d)
     * Equations:
     *   v = u + at
     *   s = ut + 0.5 * a * t²
     *   v² = u² + 2as
     */
    fun solve1DKinematics(u: Double, a: Double, t: Double? = null, s: Double? = null): SolverResult {
        return if (t != null) {
            val v = u + (a * t)
            val dist = (u * t) + (0.5 * a * t * t)
            SolverResult(
                calculatedValue = v,
                unit = "m/s",
                stepByStepFormula = listOf(
                    "v = u + at",
                    "v = $u + ($a × $t) = $v m/s",
                    "s = ut + ½at² = ($u × $t) + ½($a)($t)² = $dist m"
                ),
                isVerified = true
            )
        } else if (s != null) {
            val vSquared = u * u + 2 * a * s
            if (vSquared < 0) {
                throw IllegalArgumentException("Invalid motion parameters resulting in negative v²")
            }
            val v = sqrt(vSquared)
            SolverResult(
                calculatedValue = v,
                unit = "m/s",
                stepByStepFormula = listOf(
                    "v² = u² + 2as",
                    "v = √($u² + 2×$a×$s) = ${"%.2f".format(v)} m/s"
                ),
                isVerified = true
            )
        } else {
            throw IllegalArgumentException("Insufficient variables for 1D kinematics (need t or s)")
        }
    }

    /**
     * Topic 2: 2D Projectile Motion (projectile_motion)
     * Equations:
     *   T = 2u sin(θ) / g
     *   H = u² sin²(θ) / (2g)
     *   R = u² sin(2θ) / g
     */
    fun solveProjectile(
        u: Double,
        angleDegrees: Double,
        target: String = "range",
        g: Double = G
    ): SolverResult {
        val thetaRad = Math.toRadians(angleDegrees)
        val sinTheta = sin(thetaRad)
        val sin2Theta = sin(2 * thetaRad)

        val timeOfFlight = (2 * u * sinTheta) / g
        val maxHeight = (u * u * sinTheta * sinTheta) / (2 * g)
        val horizontalRange = (u * u * sin2Theta) / g

        return when (target.lowercase()) {
            "max_height", "height", "h" -> SolverResult(
                calculatedValue = maxHeight,
                unit = "m",
                stepByStepFormula = listOf(
                    "H = (u² × sin²θ) / (2g)",
                    "H = ($u² × sin²($angleDegrees°)) / (2 × $g)",
                    "H = ${"%.2f".format(maxHeight)} m"
                ),
                isVerified = true
            )
            "time_of_flight", "time", "t" -> SolverResult(
                calculatedValue = timeOfFlight,
                unit = "s",
                stepByStepFormula = listOf(
                    "T = (2u × sinθ) / g",
                    "T = (2 × $u × sin($angleDegrees°)) / $g",
                    "T = ${"%.2f".format(timeOfFlight)} s"
                ),
                isVerified = true
            )
            else -> SolverResult(
                calculatedValue = horizontalRange,
                unit = "m",
                stepByStepFormula = listOf(
                    "R = (u² × sin(2θ)) / g",
                    "R = ($u² × sin(2 × $angleDegrees°)) / $g",
                    "R = ${"%.2f".format(horizontalRange)} m"
                ),
                isVerified = true
            )
        }
    }

    /**
     * Topic 3: Newton's 2nd Law / Inclined Plane (newton_second_law)
     * Equations:
     *   F = ma => a = F / m
     *   Incline (angle θ, friction μ): a = g(sinθ - μ cosθ)
     */
    fun solveNewtonSecondLaw(
        force: Double? = null,
        mass: Double? = null,
        angleDegrees: Double? = null,
        frictionCoeff: Double = 0.0,
        g: Double = G
    ): SolverResult {
        if (angleDegrees != null) {
            val thetaRad = Math.toRadians(angleDegrees)
            val a = g * (sin(thetaRad) - frictionCoeff * cos(thetaRad))
            return SolverResult(
                calculatedValue = a,
                unit = "m/s²",
                stepByStepFormula = listOf(
                    "a = g(sinθ - μ·cosθ)",
                    "a = $g × (sin($angleDegrees°) - $frictionCoeff × cos($angleDegrees°))",
                    "a = ${"%.2f".format(a)} m/s²"
                ),
                isVerified = true
            )
        }

        if (force != null && mass != null && mass > 0.0) {
            val a = force / mass
            return SolverResult(
                calculatedValue = a,
                unit = "m/s²",
                stepByStepFormula = listOf(
                    "F = ma => a = F / m",
                    "a = $force N / $mass kg = ${"%.2f".format(a)} m/s²"
                ),
                isVerified = true
            )
        }

        throw IllegalArgumentException("Insufficient variables for Newton's 2nd Law calculation")
    }
}
