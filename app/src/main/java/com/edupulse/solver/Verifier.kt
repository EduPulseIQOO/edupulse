package com.edupulse.solver

import com.edupulse.model.SolverResult
import kotlin.math.abs
import kotlin.math.sin

object Verifier {
    private const val EPSILON = 1e-2

    /**
     * Cross-verifies calculated values against physical conservation and kinematic identities.
     */
    fun verifyKinematics1D(u: Double, a: Double, v: Double, t: Double? = null, s: Double? = null): Boolean {
        if (t != null) {
            val expectedV = u + a * t
            if (abs(expectedV - v) > EPSILON) return false
        }
        if (s != null) {
            val vSq = v * v
            val expectedVSq = u * u + 2 * a * s
            if (abs(vSq - expectedVSq) > 0.5) return false
        }
        return true
    }

    fun verifyProjectileRange(u: Double, angleDeg: Double, calculatedRange: Double, g: Double = 9.8): Boolean {
        val rad = Math.toRadians(angleDeg)
        val expectedRange = (u * u * sin(2 * rad)) / g
        return abs(expectedRange - calculatedRange) < EPSILON
    }

    fun verifyNewtonSecondLaw(f: Double, m: Double, a: Double): Boolean {
        if (m <= 0) return false
        val expectedF = m * a
        return abs(expectedF - f) < EPSILON
    }
}
