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
     * Uses openFd length and SharedPreferences tracking to ensure updates are reliably detected.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized && database?.isOpen == true) return

        try {
            val dbFile = File(context.filesDir, DB_NAME)
            val assetLength = try {
                context.assets.openFd(DB_NAME).use { it.length }
            } catch (e: Exception) {
                try {
                    context.assets.open(DB_NAME).use { it.available().toLong() }
                } catch (e2: Exception) {
                    -1L
                }
            }

            val prefs = context.getSharedPreferences("okf_prefs", Context.MODE_PRIVATE)
            val lastCopiedLength = prefs.getLong("last_db_length", -1L)
            val needsCopy = !dbFile.exists() || dbFile.length() == 0L ||
                    (assetLength > 0 && (dbFile.length() != assetLength || lastCopiedLength != assetLength))

            if (needsCopy) {
                if (database?.isOpen == true) {
                    database?.close()
                }
                if (dbFile.exists()) dbFile.delete()
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                prefs.edit().putLong("last_db_length", dbFile.length()).apply()
                Log.i(TAG, "Copied OKF knowledge.db from assets (${dbFile.length()} bytes) to ${dbFile.absolutePath}")
            }

            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            isInitialized = true
            Log.i(TAG, "OKF SQLite database opened successfully. Size: ${dbFile.length()} bytes.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OKF database: ${e.message}", e)
        }
    }

    private val STOP_WORDS = setOf(
        "what", "is", "the", "a", "an", "and", "or", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "how", "why", "which", "where", "when", "who",
        "does", "do", "did", "can", "could", "will", "would", "should", "shall",
        "that", "this", "these", "those", "are", "were", "was", "been", "being",
        "have", "has", "had", "find", "calculate", "state", "explain", "give", "define"
    )

    /**
     * Queries the local OKF graph using a small lexical ranker.
     * The database is small enough to scan on-device, and refusing weak matches
     * is safer than injecting an unrelated chapter into the LLM prompt.
     */
    fun findMatchingNodes(context: Context, queryText: String, limit: Int = 2): List<OkfNode> {
        initialize(context)
        val db = database ?: return emptyList()
        val allTokens = queryText.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }
        val contentTokens = allTokens.filter { it !in STOP_WORDS }
        val tokens = (if (contentTokens.isNotEmpty()) contentTokens else allTokens).distinct().take(12)
        if (tokens.isEmpty()) return emptyList()

        val ranked = mutableListOf<Pair<OkfNode, Int>>()

        try {
            val cursor = db.rawQuery(
                "SELECT id, subject, class_level, chapter_title, unit_name, frontmatter_yaml, body_markdown, keywords, node_type, parent_id FROM okf_nodes",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val node = OkfNode(
                        id = it.getString(0),
                        subject = it.getString(1),
                        classLevel = it.getInt(2),
                        chapterTitle = it.getString(3),
                        unitName = it.getString(4),
                        frontmatterYaml = it.getString(5),
                        bodyMarkdown = it.getString(6),
                        keywords = it.getString(7),
                        nodeType = it.getString(8) ?: "chapter",
                        parentId = it.getString(9)
                    )
                    val title = node.chapterTitle.lowercase()
                    val unit = node.unitName.lowercase()
                    val keywords = node.keywords.lowercase()
                    val body = node.bodyMarkdown.lowercase()
                    val score = tokens.sumOf { token ->
                        when {
                            title.split(Regex("[^\\p{L}\\p{N}]+")).contains(token) -> 8
                            title.contains(token) -> 5
                            unit.contains(token) -> 4
                            keywords.contains(token) -> 3
                            body.contains(token) -> 1
                            else -> 0
                        }
                    }
                    if (score >= 3) ranked += node to score
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error matching OKF nodes: ${e.message}", e)
        }

        val sorted = ranked
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)

        Log.d(TAG, "findMatchingNodes: query='$queryText' matched ${sorted.size} nodes: ${sorted.map { it.id }}")
        return sorted
    }

    /**
     * OKF Graph traversal: fetches nodes linked to a given node via okf_edges table.
     * edge_type can be "parent", "child", or "related".
     */
    fun getRelatedNodes(
        context: Context,
        nodeId: String,
        edgeTypes: List<String> = listOf("related", "child")
    ): List<OkfNode> {
        initialize(context)
        val db = database ?: return emptyList()
        val relatedNodes = mutableListOf<OkfNode>()

        try {
            val placeholders = edgeTypes.joinToString(",") { "?" }
            val edgeCursor = db.rawQuery(
                "SELECT to_id FROM okf_edges WHERE from_id = ? AND edge_type IN ($placeholders)",
                arrayOf(nodeId, *edgeTypes.toTypedArray())
            )
            val relatedIds = mutableListOf<String>()
            edgeCursor.use {
                while (it.moveToNext()) relatedIds.add(it.getString(0))
            }

            for (relId in relatedIds) {
                val nodeCursor = db.rawQuery(
                    "SELECT id, subject, class_level, chapter_title, unit_name, frontmatter_yaml, body_markdown, keywords, node_type, parent_id FROM okf_nodes WHERE id = ?",
                    arrayOf(relId)
                )
                nodeCursor.use {
                    if (it.moveToFirst()) {
                        relatedNodes.add(
                            OkfNode(
                                id = it.getString(0),
                                subject = it.getString(1),
                                classLevel = it.getInt(2),
                                chapterTitle = it.getString(3),
                                unitName = it.getString(4),
                                frontmatterYaml = it.getString(5),
                                bodyMarkdown = it.getString(6),
                                keywords = it.getString(7),
                                nodeType = it.getString(8) ?: "chapter",
                                parentId = it.getString(9)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing OKF graph for $nodeId: ${e.message}", e)
        }

        return relatedNodes
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
     * Constructs a compact grounding context to inject into the on-device LLM (NEET-focused).
     * Includes formulas, canonical dimensions, exam traps from the matched node,
     * plus high-yield traps from graph-adjacent related nodes (OKF tree traversal).
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

        val quantities = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT symbol, name, dimension FROM okf_quantities WHERE node_id = ?",
                arrayOf(node.id)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val sym = it.getString(0)
                    val name = it.getString(1)
                    val dim = it.getString(2)
                    if (!dim.isNullOrEmpty() && dim != "1") {
                        quantities.add("- $name ($sym): [$dim]")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading quantities: ${e.message}", e)
        }

        val traps = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT trap_text FROM okf_exam_traps WHERE node_id = ?",
                arrayOf(node.id)
            )
            cursor.use {
                while (it.moveToNext()) {
                    traps.add("- ${it.getString(0)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading traps: ${e.message}", e)
        }

        // OKF graph traversal: include exam traps from related nodes (1-hop neighbours)
        val relatedNodes = getRelatedNodes(context, node.id, listOf("related"))
        for (relNode in relatedNodes.take(2)) {
            try {
                val relCursor = db.rawQuery(
                    "SELECT trap_text FROM okf_exam_traps WHERE node_id = ? LIMIT 2",
                    arrayOf(relNode.id)
                )
                relCursor.use {
                    while (it.moveToNext()) {
                        traps.add("- [${relNode.chapterTitle}] ${it.getString(0)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading related traps for ${relNode.id}: ${e.message}", e)
            }
        }

        if (formulas.isEmpty() && quantities.isEmpty() && traps.isEmpty()) {
            return null
        }

        val sb = java.lang.StringBuilder()
        sb.append("\n[GROUNDING KNOWLEDGE - OKF / NEET]\n")
        sb.append("Topic: ${node.chapterTitle} (${node.unitName})\n")
        if (quantities.isNotEmpty()) {
            sb.append("Canonical Dimensions:\n")
            quantities.take(6).forEach { sb.append("$it\n") }
        }
        if (formulas.isNotEmpty()) {
            sb.append("Verified Formulas:\n")
            formulas.forEach { sb.append("$it\n") }
        }
        if (traps.isNotEmpty()) {
            sb.append("High-Yield NEET Exam Traps:\n")
            traps.take(8).forEach { sb.append("$it\n") }
        }
        sb.append("[/GROUNDING KNOWLEDGE]\n")

        val grounding = sb.toString()
        Log.i(TAG, "Generated grounding prompt (${grounding.length} chars) for node ${node.id} (${node.chapterTitle})")
        return grounding
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
            node_type: concept
            domain: $domain
            subject: $domain
            chapter_title: "$title"
            exam_tags: ["NEET"]
            parent_id: neet.user
            children: []
            related: []
            ---
            # $title
            $content
        """.trimIndent()

        cardFile.writeText(okfCardContent)
        Log.i(TAG, "Saved user OKF card to ${cardFile.absolutePath}")
        return cardFile
    }
}
