package com.edupulse.bridge

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.edupulse.model.ProblemExtraction
import com.edupulse.model.SolverResult

object WorksheetExporter {
    fun exportLessonWorksheet(context: Context, extraction: ProblemExtraction, result: SolverResult): Uri? {
        val width = 1080
        val height = 1520
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background & Header
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.WHITE })
        canvas.drawRect(0f, 0f, width.toFloat(), 180f, Paint().apply { color = Color.parseColor("#1A237E") })

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("EduPulse — Classroom STEM Worksheet", 60f, 105f, titlePaint)
        
        val subPaint = Paint().apply {
            color = Color.parseColor("#9FA8DA")
            textSize = 26f
            isAntiAlias = true
        }
        canvas.drawText("100% Offline AI Visual Tutor • Generated on iQOO Phone", 60f, 150f, subPaint)

        // Problem Info
        val textPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 34f
            isAntiAlias = true
        }
        canvas.drawText("TOPIC: ${extraction.topic.uppercase()}", 60f, 260f, textPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Problem: ${extraction.explanationEn.take(65)}...", 60f, 320f, textPaint)

        // Derivation Box
        canvas.drawRoundRect(60f, 380f, (width - 60).toFloat(), 800f, 24f, 24f, Paint().apply { color = Color.parseColor("#F5F5F5") })
        val stepPaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            textSize = 34f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        canvas.drawText("DERIVATION & CALCULATIONS:", 90f, 440f, stepPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        stepPaint.typeface = Typeface.MONOSPACE
        var yPos = 520f
        result.stepByStepFormula.forEach { step ->
            canvas.drawText("• $step", 90f, yPos, stepPaint)
            yPos += 70f
        }

        // Final Answer Callout
        canvas.drawRoundRect(60f, 850f, (width - 60).toFloat(), 1050f, 24f, 24f, Paint().apply { color = Color.parseColor("#E8F5E9") })
        val ansPaint = Paint().apply {
            color = Color.parseColor("#1B5E20")
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("FINAL ANSWER: ${"%.2f".format(result.calculatedValue)} ${result.unit}", 90f, 960f, ansPaint)

        // Save & Share
        val uri = saveBitmapToPublic(context, bitmap)
        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Send via Vivo Office Kit / EasyShare"))
        }
        return uri
    }

    private fun saveBitmapToPublic(context: Context, bitmap: Bitmap): Uri? {
        val filename = "EduPulse_Lesson_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EduPulse")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { context.contentResolver.openOutputStream(it)?.use { s -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, s) } }
        Toast.makeText(context, "Worksheet Saved! Drag to Laptop via Office Kit", Toast.LENGTH_LONG).show()
        return uri
    }
}
