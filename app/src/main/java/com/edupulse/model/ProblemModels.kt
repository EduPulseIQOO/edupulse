package com.edupulse.model

data class ProblemExtraction(
    val topic: String,
    val knowns: Map<String, Double?>,
    val target: String,
    val explanationEn: String,
    val explanationTe: String? = null
)

data class SolverResult(
    val calculatedValue: Double,
    val unit: String,
    val stepByStepFormula: List<String>,
    val isVerified: Boolean
)

data class DiagramState(
    val topic: String,
    val primaryParam: Float,    // e.g., initial velocity u
    val secondaryParam: Float,  // e.g., angle theta
    val primaryRange: ClosedFloatingPointRange<Float>,
    val secondaryRange: ClosedFloatingPointRange<Float>
)
