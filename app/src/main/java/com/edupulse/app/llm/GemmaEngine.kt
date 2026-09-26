package com.edupulse.app.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton wrapper around LiteRT-LM Engine for on-device Gemma inference.
 *
 * Manages engine lifecycle and provides a coroutine-friendly API for
 * sending questions and streaming responses.
 */
object GemmaEngine {

    private const val MODEL_FILENAME = "gemma-4-E2B-it-gpu.litertlm"

    fun buildSystemInstruction(languageInstruction: String = ""): String {
        val langClause = if (languageInstruction.isNotBlank()) "\n[Language Requirement]: $languageInstruction\n" else ""
        return "You are EduPulse, an offline AI tutor and homework problem solver.\n" +
            "Write in clean, plain readable text. Do NOT use LaTeX commands (never write \\frac, \\text, \\times, or $$). Use simple standard math symbols (+, -, *, /, =, ^).\n" +
            langClause +
            "\n" +
            "Step 1: Reconstruct the true intended question by intelligently correcting OCR misreads, garbled words, or letter-digit confusions (e.g. 'mau' -> 'mass', 'bady' -> 'body', '11 s boought' -> 'is brought'). State the clean question under 'Clean Question:'.\n" +
            "\n" +
            "Step 2: Solve the clean question step-by-step using this structure:\n" +
            "Clean Question:\n" +
            "Given / Key Facts:\n" +
            "Formula / Method:\n" +
            "Calculation / Explanation:\n" +
            "Final Answer:\n" +
            "\n" +
            "Step 3: If this problem involves physics (motion, kinematics, forces, projectile, or mechanics), conclude your response with a structured simulation descriptor at the very end in one of these formats:\n" +
            "- 1D Motion / Kinematics (acceleration, braking, deceleration, free fall, constant speed):\n" +
            "[DIAGRAM:KINEMATICS | mass=... | u=... | v=... | a=... | F=... | s=... | t=...]\n" +
            "- 2D Parabolic Projectile Motion (launch angle, velocity, range, height):\n" +
            "[DIAGRAM:PROJECTILE | velocity=... | angle=... | range=... | height=... | time=...]\n" +
            "- Force Balance / Equilibrium (normal, gravity, friction, applied):\n" +
            "[DIAGRAM:FREE_BODY | mass=... | normal=... | gravity=... | applied=... | friction=... | net=...]"
    }

    private var engine: Engine? = null

    private fun extractText(message: Message): String {
        return message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }

    /**
     * Initialize the engine. Call once from Application or ViewModel.
     * This is expensive (~5-10s) so run on a background thread.
     *
     * The model file is expected at [Context.getFilesDir]/[MODEL_FILENAME].
     * Copy it from assets or download it before calling this.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext

        val candidates = listOf(
            File(context.filesDir, MODEL_FILENAME),
            File(context.getExternalFilesDir(null), MODEL_FILENAME),
            File(File(context.getExternalFilesDir(null), "models"), MODEL_FILENAME),
            File("/sdcard/Android/data/${context.packageName}/files", MODEL_FILENAME),
            File("/sdcard/Download", MODEL_FILENAME),
            File("/sdcard", MODEL_FILENAME)
        )
        val resolvedModelFile = candidates.firstOrNull { it.exists() && it.length() > 0 }
            ?: run {
                // Try copying from assets if bundled there
                val internalDest = File(context.filesDir, MODEL_FILENAME)
                try {
                    context.assets.open(MODEL_FILENAME).use { input ->
                        internalDest.outputStream().use { output -> input.copyTo(output) }
                    }
                    internalDest
                } catch (_: Exception) {
                    throw IllegalStateException(
                        "Model file '$MODEL_FILENAME' not found. Checked: ${candidates.joinToString { it.absolutePath }}"
                    )
                }
            }
        android.util.Log.i("GemmaEngine", "Found Gemma model at: ${resolvedModelFile.absolutePath} (${resolvedModelFile.length() / (1024 * 1024)} MB)")

        val backends = listOf(
            Backend.GPU(),
            Backend.GOOGLE_TENSOR(),
            Backend.CPU()
        )
        var lastError: Exception? = null
        for (b in backends) {
            try {
                val config = EngineConfig(
                    modelPath = resolvedModelFile.absolutePath,
                    backend = b
                )
                val eng = Engine(config)
                eng.initialize()
                engine = eng
                android.util.Log.d("GemmaEngine", "Successfully initialized engine with backend: ${b.name}")
                break
            } catch (e: Exception) {
                android.util.Log.w("GemmaEngine", "Backend ${b.name} failed: ${e.message}")
                lastError = e
            }
        }
        if (engine == null) {
            throw lastError ?: IllegalStateException("Failed to initialize engine on any backend.")
        }
    }

    private var activeConversation: Conversation? = null

    fun resetConversation() {
        activeConversation = null
    }

    /**
     * Send a question or follow-up to Gemma and collect streamed response chunks.
     */
    fun chat(
        messageText: String,
        isFirstMessage: Boolean = false,
        languageInstruction: String = ""
    ): Flow<String> = callbackFlow {
        val eng = engine ?: throw IllegalStateException("GemmaEngine not initialized. Call initialize() first.")

        val conversation = if (isFirstMessage || activeConversation == null) {
            eng.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(buildSystemInstruction(languageInstruction))
                )
            ).also { activeConversation = it }
        } else {
            activeConversation!!
        }

        val prompt = buildString {
            if (isFirstMessage) append("Question:\n")
            append(messageText)
            if (languageInstruction.isNotBlank()) {
                append("\n\n[Instruction: ")
                append(languageInstruction)
                append("]")
            }
        }
        conversation.sendMessageAsync(prompt, object : com.google.ai.edge.litertlm.MessageCallback {
            override fun onMessage(message: Message) {
                val text = extractText(message)
                if (text.isNotEmpty()) {
                    trySend(text)
                }
            }

            override fun onDone() {
                close()
            }

            override fun onError(throwable: Throwable) {
                activeConversation = null
                close(throwable)
            }
        })

        awaitClose {
            // Callback completed or flow cancelled
        }
    }

    /**
     * Send a question to Gemma and collect the streamed response.
     * Returns a Flow of response text chunks.
     */
    fun solveQuestion(questionText: String): Flow<String> = chat(questionText, isFirstMessage = true)

    /**
     * Send a question and wait for the complete response (non-streaming).
     */
    suspend fun solveQuestionBlocking(questionText: String): String {
        val result = StringBuilder()
        solveQuestion(questionText).collect { chunk ->
            result.append(chunk)
        }
        return result.toString()
    }

    fun isInitialized(): Boolean = engine != null

    fun close() {
        activeConversation = null
        engine?.close()
        engine = null
    }
}
