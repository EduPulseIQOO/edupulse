package com.edupulse.app.knowledge

/**
 * Domain representations for Open Knowledge Format (OKF) nodes.
 */
data class OkfQuantity(
    val symbol: String,
    val name: String,
    val units: List<String>,
    val dimension: String
)

data class OkfFormula(
    val id: String,
    val formula: String,
    val latex: String,
    val constraints: List<String> = emptyList()
)

data class OkfNode(
    val id: String,
    val subject: String,
    val classLevel: Int,
    val chapterTitle: String,
    val unitName: String,
    val frontmatterYaml: String,
    val bodyMarkdown: String,
    val keywords: String = "",
    val nodeType: String = "chapter",
    val parentId: String? = null
)

data class OkfOcrConfusion(
    val corruptToken: String,
    val intendedToken: String,
    val entityType: String
)
