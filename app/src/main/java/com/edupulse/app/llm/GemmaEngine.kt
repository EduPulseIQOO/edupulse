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
import com.google.ai.edge.litertlm.SamplerConfig
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
        val langClause = if (languageInstruction.isNotBlank()) {
            "\n[Language Requirement]: $languageInstruction\n" +
                "CRITICAL: Write strictly and exclusively in the requested language. Under NO circumstances should you produce Thai (ภาษาไทย), Chinese, or any other foreign language text.\n"
        } else {
            "\n[Language Requirement]: Strict English only. Under NO circumstances should you produce Thai (ภาษาไทย), Chinese, or any non-English script. Every single word and character must be in English.\n"
        }
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
            "Step 3: If this problem involves a visual simulation in Physics, Mathematics, Chemistry, or Biology, conclude your response with a structured simulation descriptor at the very end in one of these formats:\n" +
            "- Physics Motion & Kinematics (ANY problem about cars, vehicles, runners, moving bodies, velocity, speed, acceleration, braking, stopping distance, or falling):\n" +
            "[DIAGRAM:KINEMATICS | mass=... | u=... | v=... | a=... | F=... | s=... | t=...]\n" +
            "- Physics 2D Projectile (angle, launch velocity, range, height):\n" +
            "[DIAGRAM:PROJECTILE | velocity=... | angle=... | range=... | height=... | time=...]\n" +
            "- Physics Forces (ONLY for static equilibrium e.g. hanging lamp, book resting on table):\n" +
            "[DIAGRAM:FREE_BODY | mass=... | normal=... | gravity=... | applied=... | friction=... | net=...]\n" +
            "- Physics Electric Circuit (voltage V, resistance R):\n" +
            "[DIAGRAM:CIRCUIT | v=... | r=...]\n" +
            "- Physics Simple Pendulum (length L in meters, gravity g):\n" +
            "[DIAGRAM:PENDULUM | length=... | gravity=...]\n" +
            "- Physics Wave Motion (wave speed v, frequency f, wavelength lambda):\n" +
            "[DIAGRAM:WAVE | v=... | f=... | lambda=...]\n" +
            "- Mathematics Function Grapher (linear y=mx+c or quadratic y=ax^2+bx+c or trig):\n" +
            "[DIAGRAM:GRAPH | eq=... | a=... | b=... | c=... | type=QUADRATIC]\n" +
            "- Chemistry Bohr Atomic Model (element name, symbol, atomic number Z, mass number A):\n" +
            "[DIAGRAM:ATOM | element=... | symbol=... | z=... | a=...]\n" +
            "- Chemistry Acid-Base pH Scale (substance name, pH value between 0-14):\n" +
            "[DIAGRAM:PH | substance=... | ph=...]\n" +
            "- Chemistry Reaction (reaction name, balanced equation, reaction type):\n" +
            "[DIAGRAM:REACTION | name=... | eq=... | type=...]\n" +
            "- Biology Punnett Square Genetics (trait name, parent 1 alleles e.g. Bb, parent 2 alleles e.g. Bb, dominant trait, recessive trait):\n" +
            "[DIAGRAM:PUNNETT | trait=... | p1=... | p2=... | dominant=... | recessive=...]\n" +
            "- Biology Cell Structure (cell type e.g. Plant Cell or Animal Cell):\n" +
            "[DIAGRAM:CELL | type=Plant Cell]\n" +
            "- Biology Energy Pyramid (ecosystem name, primary producer energy in J):\n" +
            "[DIAGRAM:PYRAMID | name=... | energy=...]\n" +
            "Important: If this is a conceptual theory, essay, or definition question with no quantitative or visual model, do NOT output any [DIAGRAM:...] tag."
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

        var resolvedModelFile = File(context.filesDir, MODEL_FILENAME)
        if (!resolvedModelFile.exists()) {
            val externalModel = File(context.getExternalFilesDir(null), MODEL_FILENAME)
            if (externalModel.exists()) {
                resolvedModelFile = externalModel
            } else {
                // Try copying from assets if bundled there
                try {
                    context.assets.open(MODEL_FILENAME).use { input ->
                        resolvedModelFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    throw IllegalStateException(
                        "Model file not found. Place '$MODEL_FILENAME' in internal files (${context.filesDir}), external files (${context.getExternalFilesDir(null)}), or assets."
                    )
                }
            }
        }

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
        android.util.Log.e("GemmaEngine", ">>> chat() called: message='$messageText'")
        val eng = engine ?: throw IllegalStateException("GemmaEngine not initialized. Call initialize() first.")

        // Fresh conversation per question ensures 100% clean GPU KV-cache
        val conv = eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(buildSystemInstruction(languageInstruction)),
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 0.1,
                    seed = 42
                )
            )
        ).also { activeConversation = it }

        val prompt = buildString {
            append("Question:\n")
            append(messageText)
            if (languageInstruction.isNotBlank()) {
                append("\n\n[Instruction: ")
                append(languageInstruction)
                append("]")
            }
        }

        android.util.Log.e("GemmaEngine", "Sending prompt (${prompt.length} chars) to LiteRT-LM...")
        var tokenCount = 0

        conv.sendMessageAsync(prompt, object : com.google.ai.edge.litertlm.MessageCallback {
            override fun onMessage(message: Message) {
                val text = extractText(message)
                if (text.isNotEmpty()) {
                    tokenCount++
                    if (tokenCount <= 3 || tokenCount % 15 == 0) {
                        android.util.Log.e("GemmaEngine", "Token #$tokenCount: ${text.take(30)}")
                    }
                    trySend(text)
                }
            }

            override fun onDone() {
                android.util.Log.e("GemmaEngine", "onDone() reached! Total tokens emitted: $tokenCount")
                // Explicit parameter avoids Kotlin synthetic close$default NoSuchMethodError
                val noError: Throwable? = null
                channel.close(noError)
            }

            override fun onError(throwable: Throwable) {
                android.util.Log.e("GemmaEngine", "onError() in LiteRT-LM: ${throwable.message}", throwable)
                activeConversation = null
                channel.close(throwable)
            }
        })

        awaitClose {
            android.util.Log.e("GemmaEngine", "awaitClose: flow collector finished.")
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
