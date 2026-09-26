package com.edupulse.app.ocr.paddle

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-level engine managing PaddleOCR (PP-OCRv5) on-device inference via ONNX Runtime.
 */
object PaddleOcrEngine {

    private const val TAG = "PaddleOcrEngine"
    private const val ASSET_DIR = "models/paddleocr"

    private var processor: OcrProcessor? = null

    /**
     * Initialize PaddleOCR. Copies models from assets on first run and creates ONNX sessions.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (processor != null) return@withContext

        val targetDir = File(context.filesDir, "paddleocr_models").apply { mkdirs() }

        val detFile = copyAssetIfNeeded(context, "$ASSET_DIR/det.onnx", File(targetDir, "det.onnx"))
        val recFile = copyAssetIfNeeded(context, "$ASSET_DIR/rec.onnx", File(targetDir, "rec.onnx"))
        val clsFile = copyAssetIfNeeded(context, "$ASSET_DIR/cls.onnx", File(targetDir, "cls.onnx"))
        val dictFile = copyAssetIfNeeded(context, "$ASSET_DIR/ppocrv5_dict.txt", File(targetDir, "ppocrv5_dict.txt"))

        val modelFiles = ModelFiles(
            version = "pp-ocrv5",
            baseDir = targetDir,
            detectionModel = detFile,
            recognitionModel = recFile,
            classificationModel = clsFile,
            dictionaryFile = dictFile
        )

        Log.d(TAG, "Initializing ONNX OcrProcessor with models from ${targetDir.absolutePath}...")
        processor = OcrProcessor(
            context = context,
            modelFiles = modelFiles,
            useAngleClassification = true
        )
        Log.d(TAG, "PaddleOCR initialized successfully!")
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, targetFile: File): File {
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }
        try {
            Log.d(TAG, "Copying asset $assetPath to ${targetFile.absolutePath}...")
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            return targetFile
        } catch (e: Exception) {
            Log.w(TAG, "Asset $assetPath not in APK, trying external files: ${e.message}")
        }

        val extCandidate1 = File(context.getExternalFilesDir(null), assetPath)
        val extCandidate2 = File(context.getExternalFilesDir(null), targetFile.name)
        val extCandidate3 = File(File(context.getExternalFilesDir(null), "models"), targetFile.name)
        val source = listOf(extCandidate1, extCandidate2, extCandidate3).firstOrNull { it.exists() && it.length() > 0 }
            ?: throw IllegalStateException("Model file '$assetPath' not found in assets or external storage.")

        Log.d(TAG, "Copying external model ${source.absolutePath} to ${targetFile.absolutePath}...")
        source.inputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    /**
     * Run PaddleOCR on a bitmap and return recognized lines joined into natural paragraphs.
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val proc = processor ?: throw IllegalStateException("PaddleOcrEngine not initialized. Call initialize() first.")
        val result = proc.processImage(bitmap, includeAllConfidenceScores = true)

        if (result.texts.isEmpty()) {
            return@withContext ""
        }

        // Pair each recognized text with its bounding box
        val items = result.texts.indices.map { i ->
            val box = if (i < result.boxes.size) result.boxes[i].boundingRect() else android.graphics.RectF()
            val text = result.texts[i].trim()
            Pair(box, text)
        }.filter { it.second.isNotBlank() }

        if (items.isEmpty()) return@withContext ""

        // Group words into lines based on vertical overlap (never merge distinct vertical lines)
        val sortedByTop = items.sortedBy { it.first.top }
        val lineGroups = mutableListOf<MutableList<Pair<android.graphics.RectF, String>>>()

        for (item in sortedByTop) {
            val box = item.first
            val itemHeight = max(box.height(), 10f)

            val matchingLine = lineGroups.find { line ->
                val lineTop = line.minOf { it.first.top }
                val lineBottom = line.maxOf { it.first.bottom }
                val overlap = max(0f, min(box.bottom, lineBottom) - max(box.top, lineTop))
                val minH = min(itemHeight, max(lineBottom - lineTop, 10f))
                minH > 0f && (overlap / minH) >= 0.35f
            }

            if (matchingLine != null) {
                matchingLine.add(item)
            } else {
                lineGroups.add(mutableListOf(item))
            }
        }

        // Sort lines top-to-bottom by their top coordinate
        lineGroups.sortBy { line -> line.minOf { it.first.top } }

        // Within each line, sort words left-to-right by X
        val joined = lineGroups.joinToString("\n") { line ->
            line.sortedBy { it.first.left }.joinToString(" ") { it.second }
        }

        Log.d(TAG, "PaddleOCR output (${lineGroups.size} lines):\n$joined")
        joined
    }

    fun isInitialized(): Boolean = processor != null
}
