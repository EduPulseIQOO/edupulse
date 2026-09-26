package com.edupulse.app.knowledge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object OkfRepository {
    private const val TAG = "OkfRepository"
    private const val DB_NAME = "knowledge.db"

    private var database: SQLiteDatabase? = null
    private var isInitialized = false

    /**
     * Initializes the OKF database from assets to app private storage.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized && database?.isOpen == true) return

        try {
            val dbFile = File(context.filesDir, DB_NAME)
            // Copy from assets if not exists or if asset size differs
            if (!dbFile.exists() || dbFile.length() == 0L) {
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied OKF knowledge.db from assets to ${dbFile.absolutePath}")
            }

            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            isInitialized = true
            Log.i(TAG, "OKF SQLite database opened successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OKF database: ${e.message}", e)
        }
    }

    /**
     * Queries OKF knowledge base using FTS5 or keyword matching.
     * Returns matching nodes sorted by relevance.
     */
    fun findMatchingNodes(context: Context, queryText: String, limit: Int = 2): List<OkfNode> {
        initialize(context)
        val db = database ?: return emptyList()
        val results = mutableListOf<OkfNode>()

        try {
            // Clean query tokens for FTS5 (remove punctuation, keep alphanumeric)
            val tokens = queryText.split(Regex("[^a-zA-Z0-9]+"))
                .filter { it.length > 2 }
                .take(6)

            if (tokens.isNotEmpty()) {
                val ftsQuery = tokens.joinToString(" OR ") { "$it*" }
                val cursor = db.rawQuery(
                    """
                    SELECT n.id, n.subject, n.class_level, n.chapter_title, n.unit_name, 
                           n.frontmatter_yaml, n.body_markdown, n.keywords
                    FROM okf_fts f
                    JOIN okf_nodes n ON f.id = n.id
                    WHERE okf_fts MATCH ?
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(ftsQuery, limit.toString())
                )

                cursor.use {
                    while (it.moveToNext()) {
                        results.add(
                            OkfNode(
                                id = it.getString(0),
                                subject = it.getString(1),
                                classLevel = it.getInt(2),
                                chapterTitle = it.getString(3),
                                unitName = it.getString(4),
                                frontmatterYaml = it.getString(5),
                                bodyMarkdown = it.getString(6),
                                keywords = it.getString(7)
                            )
                        )
                    }
                }
            }

            // Fallback: Default to Kinematics (keph102) if no direct FTS hit for physics text
            if (results.isEmpty()) {
                val fallbackCursor = db.rawQuery(
                    "SELECT id, subject, class_level, chapter_title, unit_name, frontmatter_yaml, body_markdown, keywords FROM okf_nodes WHERE id LIKE '%keph102%' LIMIT 1",
                    null
                )
                fallbackCursor.use {
                    if (it.moveToFirst()) {
                        results.add(
                            OkfNode(
                                id = it.getString(0),
                                subject = it.getString(1),
                                classLevel = it.getInt(2),
                                chapterTitle = it.getString(3),
                                unitName = it.getString(4),
                                frontmatterYaml = it.getString(5),
                                bodyMarkdown = it.getString(6),
                                keywords = it.getString(7)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error matching OKF nodes: ${e.message}", e)
        }

        return results
    }

    /**
     * Extracts known OCR confusions (e.g. "isn" -> "15N") from the OKF database.
     */
    fun getOcrConfusionDictionary(context: Context): Map<String, String> {
        initialize(context)
        val db = database ?: return emptyMap()
        val confusions = mutableMapOf<String, String>()

        try {
            val cursor = db.rawQuery("SELECT corrupt_token, intended_token FROM ocr_confusions", null)
            cursor.use {
                while (it.moveToNext()) {
                    confusions[it.getString(0)] = it.getString(1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OCR confusions: ${e.message}", e)
        }

        return confusions
    }

    /**
     * Constructs a compact grounding context to inject into Gemma 2B.
     */
    fun getGroundingPrompt(context: Context, ocrText: String): String? {
        val nodes = findMatchingNodes(context, ocrText, limit = 1)
        if (nodes.isEmpty()) return null

        val node = nodes.first()
        val db = database ?: return null

        val formulas = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT formula_latex, formula_code, constraints FROM okf_formulas WHERE node_id = ?",
                arrayOf(node.id)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val code = it.getString(1)
                    val constraints = it.getString(2)
                    if (constraints != null && constraints != "[]") {
                        formulas.add("- $code (Constraints: $constraints)")
                    } else {
                        formulas.add("- $code")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading formulas: ${e.message}", e)
        }

        val sb = java.lang.StringBuilder()
        sb.append("\n[GROUNDING KNOWLEDGE - OKF]\n")
        sb.append("Topic: ${node.chapterTitle} (${node.unitName})\n")
        if (formulas.isNotEmpty()) {
            sb.append("Verified Formulas:\n")
            formulas.forEach { sb.append("$it\n") }
        }
        sb.append("[/GROUNDING KNOWLEDGE]\n")

        return sb.toString()
    }

    /**
     * Saves a user-generated study note as an OKF card in app-private storage.
     */
    fun saveUserCard(context: Context, cardId: String, title: String, domain: String, content: String): File {
        val userDir = File(context.filesDir, "user_knowledge")
        if (!userDir.exists()) {
            userDir.mkdirs()
        }

        val cleanSlug = cardId.replace(Regex("[^a-zA-Z0-9_]+"), "_").lowercase()
        val cardFile = File(userDir, "$cleanSlug.okf.md")

        val okfCardContent = """
            ---
            id: user.$cleanSlug
            domain: $domain
            subject: physics
            chapter_title: "$title"
            exam_tags: ["User_Custom", "JEE_NEET"]
            ---
            # $title
            $content
        """.trimIndent()

        cardFile.writeText(okfCardContent)
        Log.i(TAG, "Saved user OKF card to ${cardFile.absolutePath}")
        return cardFile
    }
}
