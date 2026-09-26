package com.edupulse.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edupulse.app.diagram.DiagramExtractor
import com.edupulse.app.diagram.PhysicsDiagram
import com.edupulse.app.llm.GemmaEngine
import com.edupulse.app.ocr.OcrCorrection
import com.edupulse.app.tts.TtsManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class OcrFix(val original: String, val fixed: String)

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val ocrFixes: List<OcrFix> = emptyList(),
    val diagram: PhysicsDiagram? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AppLanguage(val displayName: String, val promptInstruction: String) {
    ENGLISH(
        "English",
        "Write the entire response in clear, simple step-by-step English only. Do not use Thai, Telugu, Hindi, or any other script."
    ),
    TELUGU(
        "తెలుగు",
        "దయచేసి పూర్తి సమాధానం మరియు వివరణను తెలుగు లిపిలో (Telugu) మాత్రమే రాయండి. Explain the entire solution in natural Telugu script. Keep units (kg, m/s, N, s) standard."
    ),
    HINDI(
        "हिन्दी",
        "कृपया पूरा समाधान और चरण-दर-चरण व्याख्या केवल हिन्दी (Hindi in Devanagari script) में ही लिखें। Explain the entire solution in natural Hindi script. Keep units (kg, m/s, N, s) standard."
    )
}

data class HomeUiState(
    val showCamera: Boolean = false,
    val engineReady: Boolean = false,
    val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    val inputText: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val pendingOcrFixes: List<OcrFix> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
    val showHistory: Boolean = false,
    val currentSessionId: String = java.util.UUID.randomUUID().toString(),
    val currentSessionTitle: String = "New Chat",
    val currentSessionSubject: String = com.edupulse.app.history.QuerySubject.GENERAL.name,
    val sessions: List<com.edupulse.app.history.ChatSession> = emptyList(),
    val historyItems: List<com.edupulse.app.history.DoubtHistoryItem> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val ttsManager = TtsManager(application)
    val speakingMessageId: StateFlow<String?> = ttsManager.speakingMessageId

    fun onSpeakMessage(messageId: String, text: String) {
        ttsManager.speak(messageId, text, _uiState.value.selectedLanguage)
    }

    fun onStopSpeaking() {
        ttsManager.stop()
    }

    init {
        // Load offline chat sessions on startup
        viewModelScope.launch {
            try {
                val sessions = com.edupulse.app.history.HistoryRepository.getSessions(application)
                val legacy = com.edupulse.app.history.HistoryRepository.getHistory(application)
                _uiState.update { it.copy(sessions = sessions, historyItems = legacy) }
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Sessions load warning: ${e.message}")
            }
        }

        // Initialize Gemma engine in the background on app start
        viewModelScope.launch {
            try {
                GemmaEngine.initialize(application)
                _uiState.update { it.copy(engineReady = true) }
                Log.d("HomeViewModel", "Gemma engine initialized")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Gemma init failed: ${e.message}")
                _uiState.update {
                    it.copy(error = "Model loading failed: ${e.message}")
                }
            }
        }

        // Pre-initialize PaddleOCR engine in the background
        viewModelScope.launch {
            try {
                com.edupulse.app.ocr.paddle.PaddleOcrEngine.initialize(application)
                Log.d("HomeViewModel", "PaddleOCR pre-initialized")
            } catch (e: Exception) {
                Log.w("HomeViewModel", "PaddleOCR background init: ${e.message}")
            }
        }

        // Pre-initialize OKF repository in the background
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.edupulse.app.knowledge.OkfRepository.initialize(application)
                Log.d("HomeViewModel", "OKF repository pre-initialized")
            } catch (e: Exception) {
                Log.w("HomeViewModel", "OKF background init: ${e.message}")
            }
        }
    }

    fun onCaptureClick() {
        _uiState.update { it.copy(showCamera = true, error = null) }
    }

    fun onDismissCamera() {
        _uiState.update { it.copy(showCamera = false) }
    }

    private fun cropToViewfinder(src: Bitmap): Bitmap {
        val cropWidth = (src.width * 0.92f).toInt().coerceIn(1, src.width)
        val cropHeight = (src.height * 0.70f).toInt().coerceIn(1, src.height)
        val startX = ((src.width - cropWidth) / 2).coerceAtLeast(0)
        val startY = ((src.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(src, startX, startY, cropWidth, cropHeight)
    }

    private fun enhanceBitmapForOcr(src: Bitmap): Bitmap {
        val cm = ColorMatrix().apply {
            setSaturation(0f) // Convert to grayscale
            val scale = 1.35f
            val translate = (-128f * scale + 128f)
            postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun onImageCaptured(bitmap: Bitmap, rotationDegrees: Int = 0) {
        _uiState.update { it.copy(showCamera = false, isLoading = true, loadingMessage = "Processing camera photo...", error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            processImageBitmap(bitmap, rotationDegrees, isCropped = true)
        }
    }

    fun onImageSelected(context: Context, uri: Uri) {
        _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading image from gallery...", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                        val maxDim = maxOf(info.size.width, info.size.height)
                        if (maxDim > 2048) {
                            val sample = (maxDim + 2047) / 2048
                            decoder.setTargetSampleSize(sample)
                        }
                    }
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    val sample = if (maxDim > 2048) (maxDim + 2047) / 2048 else 1
                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    } ?: throw IllegalStateException("Could not read image file")
                }
                processImageBitmap(bitmap, rotationDegrees = 0, isCropped = false)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to load selected image", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load image: ${e.message}") }
            }
        }
    }

    private suspend fun processImageBitmap(rawBitmap: Bitmap, rotationDegrees: Int, isCropped: Boolean) {
        var rotated: Bitmap? = null
        var cropped: Bitmap? = null
        var enhanced: Bitmap? = null

        try {
            _uiState.update { it.copy(loadingMessage = "Analyzing handwriting with PaddleOCR...") }
            rotated = rotateBitmap(rawBitmap, rotationDegrees.toFloat())
            cropped = if (isCropped) cropToViewfinder(rotated) else rotated
            enhanced = enhanceBitmapForOcr(cropped)
            var rawText = ""

            // 1. Primary: Run PaddleOCR (deep-learning handwriting model)
            try {
                if (!com.edupulse.app.ocr.paddle.PaddleOcrEngine.isInitialized()) {
                    com.edupulse.app.ocr.paddle.PaddleOcrEngine.initialize(getApplication())
                }
                rawText = com.edupulse.app.ocr.paddle.PaddleOcrEngine.recognize(enhanced)
                Log.d("HomeViewModel", "PaddleOCR output: $rawText")
            } catch (e: Exception) {
                Log.w("HomeViewModel", "PaddleOCR attempt failed, falling back to ML Kit: ${e.message}", e)
            }

            // 2. Fallback to ML Kit if PaddleOCR returned empty
            if (rawText.isBlank()) {
                _uiState.update { it.copy(loadingMessage = "Trying ML Kit OCR fallback...") }
                Log.d("HomeViewModel", "Using ML Kit fallback...")
                val image = InputImage.fromBitmap(enhanced, 0)
                val result = textRecognizer.process(image).await()
                rawText = if (result.text.isNotBlank()) {
                    result.textBlocks.joinToString("\n\n") { block ->
                        block.lines.joinToString(" ") { it.text }
                    }
                } else ""
            }

            Log.d("HomeViewModel", "Final extracted OCR text:\n$rawText")

            if (rawText.isBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No text detected. Please ensure good lighting and text is in focus."
                    )
                }
                return
            }

            processOcrText(rawText)
        } catch (e: Exception) {
            Log.e("HomeViewModel", "OCR failed", e)
            _uiState.update {
                it.copy(isLoading = false, error = "OCR failed: ${e.message}")
            }
        } finally {
            // Explicit memory management: recycle all intermediate bitmaps immediately
            if (enhanced != null && enhanced !== cropped && !enhanced.isRecycled) {
                enhanced.recycle()
            }
            if (cropped != null && cropped !== rotated && !cropped.isRecycled) {
                cropped.recycle()
            }
            if (rotated != null && rotated !== rawBitmap && !rotated.isRecycled) {
                rotated.recycle()
            }
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            System.gc()
        }
    }

    fun processOcrText(rawText: String) {
        // Step 1: Fix unit format corruptions
        val unitFixed = OcrCorrection.fixUnitOcrErrors(rawText)
        // Step 2: Dynamic digit recovery near units + OKF domain confusions
        val okfConfusions = com.edupulse.app.knowledge.OkfRepository.getOcrConfusionDictionary(getApplication())
        val (cleaned, fixes) = OcrCorrection.recoverDigitsNearUnits(unitFixed, okfConfusions)

        _uiState.update {
            it.copy(
                inputText = cleaned,
                pendingOcrFixes = fixes.map { f -> OcrFix(f["original"]!!, f["fixed"]!!) },
                isLoading = false,
                error = null
            )
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onDismissOcrFixes() {
        _uiState.update { it.copy(pendingOcrFixes = emptyList()) }
    }

    fun onNewChat() {
        ttsManager.stop()
        GemmaEngine.resetConversation()
        _uiState.update {
            it.copy(
                currentSessionId = java.util.UUID.randomUUID().toString(),
                currentSessionTitle = "New Chat",
                currentSessionSubject = com.edupulse.app.history.QuerySubject.GENERAL.name,
                messages = emptyList(),
                inputText = "",
                pendingOcrFixes = emptyList(),
                error = null,
                showHistory = false
            )
        }
    }

    fun onClearChat() {
        onNewChat()
    }

    fun onLanguageSelected(language: AppLanguage) {
        if (_uiState.value.selectedLanguage != language) {
            ttsManager.stop()
            GemmaEngine.resetConversation()
            _uiState.update { it.copy(selectedLanguage = language) }
        }
    }

    fun onToggleHistory(show: Boolean) {
        _uiState.update { it.copy(showHistory = show) }
    }

    fun onSelectSession(session: com.edupulse.app.history.ChatSession) {
        ttsManager.stop()
        GemmaEngine.resetConversation()
        val lang = AppLanguage.entries.find { it.name == session.languageCode } ?: AppLanguage.ENGLISH
        _uiState.update {
            it.copy(
                currentSessionId = session.id,
                currentSessionTitle = session.title,
                currentSessionSubject = session.subject,
                messages = session.messages,
                selectedLanguage = lang,
                showHistory = false,
                error = null
            )
        }
    }

    fun onDeleteSession(sessionId: String) {
        viewModelScope.launch {
            val updated = com.edupulse.app.history.HistoryRepository.deleteSession(getApplication(), sessionId)
            val legacy = com.edupulse.app.history.HistoryRepository.getHistory(getApplication())
            val isActiveSession = _uiState.value.currentSessionId == sessionId
            _uiState.update {
                it.copy(
                    sessions = updated,
                    historyItems = legacy
                )
            }
            if (isActiveSession) {
                onNewChat()
            }
        }
    }

    fun onClearAllSessions() {
        viewModelScope.launch {
            com.edupulse.app.history.HistoryRepository.clearAllSessions(getApplication())
            _uiState.update {
                it.copy(
                    sessions = emptyList(),
                    historyItems = emptyList()
                )
            }
            onNewChat()
        }
    }

    fun onRestoreHistoryItem(item: com.edupulse.app.history.DoubtHistoryItem) {
        val matchingSession = _uiState.value.sessions.find { it.id == item.id }
        if (matchingSession != null) {
            onSelectSession(matchingSession)
        } else {
            ttsManager.stop()
            GemmaEngine.resetConversation()
            val userMsg = ChatMessage(sender = MessageSender.USER, text = item.question)
            val asstMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = item.solution,
                diagram = item.diagram,
                isStreaming = false
            )
            val lang = AppLanguage.entries.find { it.name == item.languageCode } ?: AppLanguage.ENGLISH
            _uiState.update {
                it.copy(
                    currentSessionId = item.id,
                    currentSessionTitle = item.title,
                    currentSessionSubject = item.subject,
                    messages = listOf(userMsg, asstMsg),
                    selectedLanguage = lang,
                    showHistory = false,
                    error = null
                )
            }
        }
    }

    fun onDeleteHistoryItem(id: String) {
        onDeleteSession(id)
    }

    fun onClearAllHistory() {
        onClearAllSessions()
    }

    fun onSendMessage(overrideText: String? = null) {
        val messageText = (overrideText ?: _uiState.value.inputText).trim()
        if (messageText.isBlank()) return

        val fixes = _uiState.value.pendingOcrFixes
        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = messageText,
            ocrFixes = fixes
        )
        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val initialDiagram = DiagramExtractor.extract("", messageText)
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            sender = MessageSender.ASSISTANT,
            text = "",
            diagram = initialDiagram,
            isStreaming = true
        )

        // Capture previous messages before updating state to calculate sliding context window
        val previousMessages = _uiState.value.messages
        val previousUserCount = previousMessages.count { it.sender == MessageSender.USER }
        val isFirst = previousUserCount == 0

        // Context Window: For follow-ups (turn 2+), pass the sliding window of the last 4 messages (2 turns).
        val contextWindow = if (isFirst) {
            emptyList()
        } else {
            previousMessages.takeLast(4)
        }

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantPlaceholder,
                inputText = "",
                pendingOcrFixes = emptyList(),
                isLoading = true,
                loadingMessage = "Thinking with Gemma on GPU...",
                error = null
            )
        }

        viewModelScope.launch {
            try {
                if (!GemmaEngine.isInitialized()) {
                    GemmaEngine.initialize(getApplication())
                    _uiState.update { it.copy(engineReady = true) }
                }

                val responseBuilder = StringBuilder()
                val langInstruction = _uiState.value.selectedLanguage.promptInstruction

                val okfGrounding = com.edupulse.app.knowledge.OkfRepository.getGroundingPrompt(getApplication(), messageText)
                val effectivePrompt = if (okfGrounding != null) "$messageText\n$okfGrounding" else messageText

                GemmaEngine.chat(
                    effectivePrompt,
                    isFirstMessage = isFirst,
                    contextWindow = contextWindow,
                    languageInstruction = langInstruction
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                    val currentText = responseBuilder.toString()
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = currentText, diagram = msg.diagram ?: initialDiagram, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                }

                val fullResponse = responseBuilder.toString()
                Log.e("HomeViewModel", ">>> Finished chat collection! Received ${fullResponse.length} chars")
                val diagram = DiagramExtractor.extract(fullResponse, messageText) ?: initialDiagram
                val cleanText = DiagramExtractor.stripDiagramTags(fullResponse)
                val displayText = if (cleanText.isNotBlank()) {
                    cleanText
                } else if (diagram != null) {
                    "Here is the interactive step-by-step simulation model:"
                } else {
                    "No response generated. Please tap 'Clear' or try rephrasing your question."
                }

                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = cleanText,
                            diagram = diagram,
                            isStreaming = false
                        )
                    } else {
                        msg
                    }
                }

                // Compute session title and subject
                val subject = com.edupulse.app.history.QueryClassifier.classify(messageText, diagram)
                val existingTitle = _uiState.value.currentSessionTitle
                val sessionTitle = if (isFirst || existingTitle == "New Chat") {
                    when (subject) {
                        com.edupulse.app.history.QuerySubject.PROJECTILE -> "Projectile (u=${(diagram as? PhysicsDiagram.Projectile)?.velocity ?: "?"}, θ=${(diagram as? PhysicsDiagram.Projectile)?.angle ?: "?"})"
                        com.edupulse.app.history.QuerySubject.KINEMATICS -> "Kinematics (u=${(diagram as? PhysicsDiagram.Kinematics)?.initialVelocity ?: "?"}, a=${(diagram as? PhysicsDiagram.Kinematics)?.acceleration ?: "?"})"
                        com.edupulse.app.history.QuerySubject.FORCES -> "Forces (${(diagram as? PhysicsDiagram.FreeBody)?.appliedForce ?: (diagram as? PhysicsDiagram.FreeBody)?.mass ?: "F"})"
                        com.edupulse.app.history.QuerySubject.PHYSICS -> "Physics: ${messageText.take(28)}"
                        com.edupulse.app.history.QuerySubject.CHEMISTRY -> "Chemistry: ${messageText.take(28)}"
                        com.edupulse.app.history.QuerySubject.BIOLOGY -> "Biology: ${messageText.take(28)}"
                        com.edupulse.app.history.QuerySubject.MATH -> "Math: ${messageText.take(28)}"
                        com.edupulse.app.history.QuerySubject.GENERAL -> if (messageText.length > 28) messageText.take(28) + "..." else messageText
                    }
                } else {
                    existingTitle
                }

                val sessionSubject = if (isFirst || _uiState.value.currentSessionSubject == com.edupulse.app.history.QuerySubject.GENERAL.name) {
                    subject.name
                } else {
                    _uiState.value.currentSessionSubject
                }

                val currentSession = com.edupulse.app.history.ChatSession(
                    id = _uiState.value.currentSessionId,
                    title = sessionTitle,
                    subject = sessionSubject,
                    createdAt = updatedMessages.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    languageCode = _uiState.value.selectedLanguage.name,
                    messages = updatedMessages
                )

                // Persist session to local storage
                val updatedSessions = com.edupulse.app.history.HistoryRepository.saveSession(getApplication(), currentSession)
                val legacyHistory = com.edupulse.app.history.HistoryRepository.getHistory(getApplication())

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        messages = updatedMessages,
                        currentSessionTitle = sessionTitle,
                        currentSessionSubject = sessionSubject,
                        sessions = updatedSessions,
                        historyItems = legacyHistory
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Chat failed: ${e.message}", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Failed to generate answer: ${e.message}",
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    text = if (initialDiagram != null) "Here is the interactive simulation model for your problem:" else "Error generating response: ${e.message}",
                                    diagram = initialDiagram,
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        }
                    )
                }
            }
        }
    }

    // Backward compatibility helper
    fun onSolveClick() {
        onSendMessage(_uiState.value.inputText)
    }

    fun onOcrTextEdited(newText: String) {
        onInputTextChanged(newText)
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
        GemmaEngine.close()
        ttsManager.shutdown()
    }
}
