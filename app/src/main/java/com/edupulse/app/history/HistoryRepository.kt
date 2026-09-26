package com.edupulse.app.history

import android.content.Context
import com.edupulse.app.ui.ChatMessage
import com.edupulse.app.ui.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

object HistoryRepository {
    private const val SESSIONS_FILE = "chat_sessions.json"
    private const val LEGACY_HISTORY_FILE = "doubt_history.json"
    private val mutex = Mutex()

    /**
     * Retrieve all chat sessions sorted by most recently updated first.
     * Automatically migrates legacy doubt_history.json if present.
     */
    suspend fun getSessions(context: Context): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            getSessionsInternal(context)
        }
    }

    /**
     * Save or update a session in local storage.
     */
    suspend fun saveSession(context: Context, session: ChatSession): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = getSessionsInternal(context).toMutableList()
            val index = existing.indexOfFirst { it.id == session.id }
            if (index >= 0) {
                existing[index] = session
            } else {
                existing.add(0, session)
            }
            existing.sortByDescending { it.updatedAt }
            val trimmed = existing.take(50)
            saveSessionsInternal(context, trimmed)
            trimmed
        }
    }

    /**
     * Fetch a specific session by its unique ID.
     */
    suspend fun getSession(context: Context, sessionId: String): ChatSession? = withContext(Dispatchers.IO) {
        mutex.withLock {
            getSessionsInternal(context).firstOrNull { it.id == sessionId }
        }
    }

    /**
     * Delete a single session by its unique ID.
     */
    suspend fun deleteSession(context: Context, sessionId: String): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = getSessionsInternal(context).filterNot { it.id == sessionId }
            saveSessionsInternal(context, existing)
            existing
        }
    }

    /**
     * Clear all sessions from storage.
     */
    suspend fun clearAllSessions(context: Context): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveSessionsInternal(context, emptyList())
            emptyList()
        }
    }

    // --- Internal Helpers & Migration ---

    private fun getSessionsInternal(context: Context): List<ChatSession> {
        val sessionsFile = File(context.filesDir, SESSIONS_FILE)
        if (sessionsFile.exists() && sessionsFile.length() > 0L) {
            return try {
                val array = JSONArray(sessionsFile.readText())
                val list = mutableListOf<ChatSession>()
                for (i in 0 until array.length()) {
                    list.add(ChatSession.fromJson(array.getJSONObject(i)))
                }
                list.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                android.util.Log.e("HistoryRepository", "Failed to load sessions", e)
                emptyList()
            }
        }

        // Check if legacy doubt_history.json can be migrated
        val legacyFile = File(context.filesDir, LEGACY_HISTORY_FILE)
        if (legacyFile.exists() && legacyFile.length() > 0L) {
            try {
                val array = JSONArray(legacyFile.readText())
                val migrated = mutableListOf<ChatSession>()
                for (i in 0 until array.length()) {
                    val item = DoubtHistoryItem.fromJson(array.getJSONObject(i))
                    val userMsg = ChatMessage(
                        sender = MessageSender.USER,
                        text = item.question,
                        timestamp = item.timestamp
                    )
                    val asstMsg = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = item.solution,
                        diagram = item.diagram,
                        timestamp = item.timestamp
                    )
                    migrated.add(
                        ChatSession(
                            id = item.id,
                            title = item.title,
                            subject = item.subject,
                            createdAt = item.timestamp,
                            updatedAt = item.timestamp,
                            languageCode = item.languageCode,
                            messages = listOf(userMsg, asstMsg)
                        )
                    )
                }
                migrated.sortByDescending { it.updatedAt }
                saveSessionsInternal(context, migrated)
                return migrated
            } catch (e: Exception) {
                android.util.Log.w("HistoryRepository", "Legacy migration failed", e)
            }
        }

        return emptyList()
    }

    private fun saveSessionsInternal(context: Context, sessions: List<ChatSession>) {
        val file = File(context.filesDir, SESSIONS_FILE)
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        file.writeText(array.toString(2))
    }

    // --- Backward Compatibility for Legacy Calls ---

    suspend fun getHistory(context: Context): List<DoubtHistoryItem> = withContext(Dispatchers.IO) {
        val sessions = getSessions(context)
        sessions.map { s ->
            val firstUser = s.messages.firstOrNull { it.sender == MessageSender.USER }?.text ?: s.title
            val lastAsst = s.messages.lastOrNull { it.sender == MessageSender.ASSISTANT }?.text ?: ""
            DoubtHistoryItem(
                id = s.id,
                timestamp = s.updatedAt,
                title = s.title,
                question = firstUser,
                solution = lastAsst,
                diagram = s.activeDiagram,
                languageCode = s.languageCode,
                subject = s.subject
            )
        }
    }

    suspend fun saveItem(context: Context, item: DoubtHistoryItem): List<DoubtHistoryItem> {
        val userMsg = ChatMessage(sender = MessageSender.USER, text = item.question, timestamp = item.timestamp)
        val asstMsg = ChatMessage(sender = MessageSender.ASSISTANT, text = item.solution, diagram = item.diagram, timestamp = item.timestamp)
        val session = ChatSession(
            id = item.id,
            title = item.title,
            subject = item.subject,
            createdAt = item.timestamp,
            updatedAt = item.timestamp,
            languageCode = item.languageCode,
            messages = listOf(userMsg, asstMsg)
        )
        saveSession(context, session)
        return getHistory(context)
    }

    suspend fun deleteItem(context: Context, id: String): List<DoubtHistoryItem> {
        deleteSession(context, id)
        return getHistory(context)
    }

    suspend fun clearAll(context: Context): List<DoubtHistoryItem> {
        clearAllSessions(context)
        return emptyList()
    }
}
