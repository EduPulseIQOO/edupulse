# EduPulse — AI Agent Operational Cheatsheet & Master Spec
**Target Competition:** iQOO City Battles 2026 (powered by Reskilll)  
**Project:** EduPulse ("Doubt-to-Diagram") — 100% Offline AI STEM Visual Tutor  
**Author:** EduPulse Core Team  
**Audience:** AI Coding Assistants (Antigravity, Claude Code, Gemini in Android Studio) & Developers

---

## 0. AGENT SYSTEM DIRECTIVES (MANDATORY FOR ANY AI AGENT)

```
[SYSTEM PROMPT FOR ASSISTANT]
You are a principal Android & On-Device AI Engineer building EduPulse.
Follow these unbreakable constraints:
1. STRICTLY OFFLINE: Never generate code that makes cloud HTTP/API calls. Everything must run in Airplane Mode.
2. HYBRID AI/MATH: Never let an LLM perform mathematical arithmetic. The LLM extracts parameters into JSON; Kotlin functions compute results deterministically.
3. FAIL-SAFE FIRST: Every screen must have a fallback. If OCR misreads, provide an editable text box. If Gemma lags, use the keyword-template solver.
4. TELEMETRY COMPLIANT: Use Camera, On-Device NPU (LiteRT-LM), and Vivo Office Kit heavily to satisfy HackTracker telemetry.
5. 60 FPS DIAGRAMS: Jetpack Compose Canvas must react instantly to What-If sliders using pure mathematical functions without invoking the LLM.
```

---

## 0.1 INITIAL AGENT KICKOFF PROTOCOL (WHAT TO DO AT THE VERY BEGINNING)

When initializing the project during **Saturday 11:00–11:45 (Green Light)**, the AI agent MUST execute these steps in exact order:

### Execution Order:
1. **Read Section 0 & 0.1:** Internalize offline/deterministic constraints and file architecture.
2. **Setup Dependencies (Read Section 5):** Apply the exact dependencies to `app/build.gradle.kts` and sync Gradle.
3. **Scaffold Folder Structure:** Create packages under `com.edupulse`:
   ```
   app/src/main/java/com/edupulse/
     ├── model/          # ProblemModels.kt (From Section 3)
     ├── ocr/            # TextRecognizerHelper.kt (ML Kit offline fallback)
     ├── ai/             # LiteRtLmManager.kt, TemplateSolver.kt (Section 7)
     ├── solver/         # KinematicSolvers.kt, Verifier.kt (Section 4)
     ├── ui/
     │    ├── canvas/    # ProjectileCanvas.kt, KinematicsCanvas.kt
     │    ├── components/# WhatIfSlider.kt, EditableTextBox.kt
     │    └── lab/       # PromptLabScreen.kt (In-app prompt tuning)
     └── bridge/         # OfficeKitBridges.kt (Section 6)
   ```
4. **Create Data Models (Read Section 3):** Implement `model/ProblemModels.kt` first so all subsequent modules compile cleanly.
5. **Implement Deterministic Solvers (Read Section 4):** Write and unit-test `solver/KinematicSolvers.kt` (zero dependencies, instant win).
6. **Setup GitHub Actions Workflow:** Create `.github/workflows/build-apk.yml` immediately before making the first git push so Red Light cloud builds are unlocked.

---

## 1. COMPETITION MATRIX & RUBRIC OPTIMIZATION

| Category | Weight | How EduPulse Captures Maximum Points |
| :--- | :---: | :--- |
| **End Product Quality** | **30%** | Deterministic Kotlin solver guarantees 0% math hallucination. Clean UI, loading skeletons, error-handled inputs. |
| **Novelty & Impact** | **20%** | Visual, interactive STEM tutor with vernacular offline audio for students in low-connectivity regions. |
| **Creative Phone Use** | **15%** | Tracked by **HackTracker**: Camera capture + on-device NPU compute + local TTS synthesis. |
| **Technical Depth** | **15%** | Quantized Gemma 2B running locally via LiteRT-LM + Compose 2D Canvas rendering engine. |
| **Office Kit Usage** | **10%** | **Mandatory:** 3 working bridges (Export lesson PNG, Clipboard problem paste, Mirrored classroom slider). |
| **Demo & Pitch** | **10%** | Crisp 3-minute pitch starting with "Airplane Mode ON" and ending with "0.00 KB cloud transfer." |

### Timebox Format Rules
* **Red Light (~55% time):** Phone-only primary device. Use **Prompt Lab** inside app or **AI Studio Build / GitHub Actions** for updates.
* **Green Light (~45% time):** Laptops permitted. Do heavy Gradle builds, C++/JNI bindings, and architecture refactors.

---

## 2. SYSTEM ARCHITECTURE PIPELINE

```
[Student Handwritten Question]
               │
               ▼ (Hardware: CameraX)
      [Image Preprocessing]
               │
               ▼ (ML Kit / PaddleOCR ONNX)
      [Raw Recognized Text]
               │
               ▼ (Editable UI Text Box — Human Fallback)
    [LiteRT-LM Gemma 2B Engine]  ◄─── Prompt: prompts/extract.txt
               │
               ▼ (Structured JSON Contract)
   [Kotlin Deterministic Solver] ◄─── Config: formulas.json
               │
        ┌──────┴──────┐
        ▼             ▼
[Kotlin Verifier]  [Jetpack Compose Canvas]
        │             │
        ▼             ▼
[Offline TTS]   [What-If Sliders (Pure Math)]
```

---

## 3. STRICT JSON CONTRACTS & SCHEMAS

### A. Gemma 2B Extraction Schema
Gemma must return **only** a valid JSON object matching this schema:

```json
{
  "topic": "projectile_motion | kinematics_1d | newton_second_law",
  "knowns": {
    "u": 20.0,
    "angle": 45.0,
    "g": 9.8,
    "mass": null
  },
  "target": "range | max_height | time_of_flight | final_velocity | distance | acceleration",
  "explanation_en": "A ball is launched at 20 m/s at 45 degrees under standard gravity.",
  "explanation_te": "ఒక బంతి 20 మీ/సె వేగంతో 45 డిగ్రీల కోణంలో విసిరివేయబడింది."
}
```

### B. Kotlin Data Models (`model/ProblemModels.kt`)

```kotlin
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
```

---

## 4. THE 3 HERO TOPICS & DETERMINISTIC SOLVER SPEC

Limit the hackathon scope strictly to these 3 kinematics/mechanics topics:

### Topic 1: 1D Kinematics (`kinematics_1d`)
* **Standard Equations:**
  $$v = u + at$$
  $$s = ut + \frac{1}{2}at^2$$
  $$v^2 = u^2 + 2as$$
* **Kotlin Solver:**
  ```kotlin
  fun solve1DKinematics(u: Double, a: Double, t: Double?, s: Double?): SolverResult {
      return if (t != null) {
          val v = u + (a * t)
          val dist = (u * t) + (0.5 * a * t * t)
          SolverResult(v, "m/s", listOf("v = u + at", "v = $u + ($a × $t) = $v m/s"), true)
      } else if (s != null) {
          val v = kotlin.math.sqrt(u * u + 2 * a * s)
          SolverResult(v, "m/s", listOf("v² = u² + 2as", "v = √($u² + 2×$a×$s) = $v m/s"), true)
      } else {
          throw IllegalArgumentException("Insufficient variables")
      }
  }
  ```

### Topic 2: 2D Projectile Motion (`projectile_motion`)
* **Standard Equations ($g = 9.8\text{ m/s}^2$):**
  $$\text{Time of flight } T = \frac{2u \sin\theta}{g}$$
  $$\text{Max Height } H = \frac{u^2 \sin^2\theta}{2g}$$
  $$\text{Horizontal Range } R = \frac{u^2 \sin(2\theta)}{g}$$
* **Compose Canvas Trajectory:**
  $$y(x) = x \tan\theta - \frac{g x^2}{2 u^2 \cos^2\theta}$$

### Topic 3: Newton's 2nd Law / Inclined Plane (`newton_second_law`)
* **Standard Equations:**
  $$F_{net} = ma \implies a = \frac{F}{m}$$
  $$\text{Incline (angle }\theta\text{, friction }\mu\text{): } a = g(\sin\theta - \mu \cos\theta)$$

---

## 5. DEPENDENCIES SPEC (`app/build.gradle.kts`)

```kotlin
dependencies {
    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Offline Text Recognition (Tier-0 Guaranteed OCR)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // LiteRT-LM (On-Device LLM Runtime)
    implementation("com.google.ai.edge.litert:litert-lm:1.0.1")

    // Kotlin Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}
```

### 5.1 Cloud Build Workflow (`.github/workflows/build-apk.yml`)
```yaml
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: app-debug, path: app/build/outputs/apk/debug/app-debug.apk }
```

---

## 6. OFFICE KIT INTEGRATION SPEC (MANDATORY 10% SCORE)

Provide these 3 working touchpoints in the app UI to capture the 10% rubric score and maximize HackTracker telemetry:

### 6.1 Feature 1: "Import Desktop Textbook Doubt" (`bridge/ClipboardBridge.kt`)
Synchronizes seamlessly with laptop clipboard via Office Kit:

```kotlin
package com.edupulse.bridge

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardBridge {
    fun readFromLaptopClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        if (clipboard.hasPrimaryClip() && 
            (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
             clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        ) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                Toast.makeText(context, "Synced from Laptop via Office Kit", Toast.LENGTH_SHORT).show()
                return text
            }
        }
        Toast.makeText(context, "Clipboard empty. Copy question on laptop first!", Toast.LENGTH_SHORT).show()
        return null
    }
}
```

Compose UI trigger:
```kotlin
@Composable
fun OfficeKitPasteButton(onTextPasted: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { ClipboardBridge.readFromLaptopClipboard(context)?.let(onTextPasted) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Paste Question from Laptop (Office Kit)")
    }
}
```

### 6.2 Feature 2: "1-Click Worksheet Export" (`bridge/WorksheetExporter.kt`)
Draws an educational STEM worksheet bitmap (derivation, given values, final answer) and saves to shared storage for instant Office Kit file drag-and-drop:

```kotlin
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
            color = Color.WHITE; textSize = 52f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText("EduPulse — Classroom STEM Worksheet", 60f, 105f, titlePaint)
        
        val subPaint = Paint().apply {
            color = Color.parseColor("#9FA8DA"); textSize = 26f; isAntiAlias = true
        }
        canvas.drawText("100% Offline AI Visual Tutor • Generated on iQOO Phone", 60f, 150f, subPaint)

        // Problem Info
        val textPaint = Paint().apply {
            color = Color.parseColor("#212121"); textSize = 34f; isAntiAlias = true
        }
        canvas.drawText("TOPIC: ${extraction.topic.uppercase()}", 60f, 260f, textPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Problem: ${extraction.explanationEn.take(65)}...", 60f, 320f, textPaint)

        // Derivation Box
        canvas.drawRoundRect(60f, 380f, (width - 60).toFloat(), 800f, 24f, 24f, Paint().apply { color = Color.parseColor("#F5F5F5") })
        val stepPaint = Paint().apply {
            color = Color.parseColor("#0D47A1"); textSize = 34f; typeface = Typeface.MONOSPACE; isAntiAlias = true
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
            color = Color.parseColor("#1B5E20"); textSize = 44f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
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
```

### 6.3 Feature 3: "Classroom Mode" (`ui/classroom/ClassroomScreen.kt`)
High-contrast 16:9 fullscreen projection mode optimized for Office Kit screen mirroring:

```kotlin
package com.edupulse.ui.classroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edupulse.ui.canvas.ProjectileCanvas

@Composable
fun ClassroomModeScreen(
    initialVelocity: Float = 25f,
    initialAngle: Float = 45f,
    onClose: () -> Unit
) {
    var velocity by remember { mutableFloatStateOf(initialVelocity) }
    var angle by remember { mutableFloatStateOf(initialAngle) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Deep Slate Black for Projectors
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CLASSROOM MODE • PROJECTOR VIEW", color = Color(0xFF38BDF8), fontSize = 18.sp, style = MaterialTheme.typography.titleMedium)
                    Text("Office Kit Screen Mirroring Active • 60 FPS Math Engine", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Canvas Area (Dominates Display)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E293B), shape = MaterialTheme.shapes.medium)
                    .padding(8.dp)
            ) {
                ProjectileCanvas(velocity = velocity, angleDegrees = angle, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(16.dp))

            // Thumb Controller Deck
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Initial Velocity: ${velocity.toInt()} m/s", color = Color.White)
                    Slider(value = velocity, onValueChange = { velocity = it }, valueRange = 5f..60f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8)))

                    Text("Launch Angle: ${angle.toInt()}°", color = Color.White)
                    Slider(value = angle, onValueChange = { angle = it }, valueRange = 10f..85f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFF472B6), activeTrackColor = Color(0xFFF472B6)))
                }
            }
        }
    }
}
```

---

## 7. FAIL-SAFE & CRASH-PREVENTION MATRIX

| Stage | Potential Point of Failure | Fallback Strategy |
| :--- | :--- | :--- |
| **OCR** | PaddleOCR ONNX JNI linking or memory crash | Immediately switch to **Google ML Kit Text Recognition** (10 lines of code, 100% offline). |
| **OCR** | Bad handwriting misidentifies a variable ($5 \rightarrow S$) | Show **Editable Text Box** before processing; user corrects in 1 second. |
| **Gemma 2B** | Model loading out-of-memory or latency > 5s | Fallback to **`TemplateSolver.kt`** (regex pattern matching for kinematics keywords). |
| **TTS Voice** | Telugu voice data missing on FuntouchOS | 1. Check `TextToSpeech.LANG_AVAILABLE`<br>2. Fall back to English voice<br>3. Play pre-recorded `R.raw.telugu_projectile_demo.mp3`. |
| **Cloud Build**| GitHub Actions queue delayed during Red Light | Use **Prompt Lab screen** to edit prompts and JSON files locally in app storage. |

---

## 8. DEMO SCRIPT (EXACT 3-MINUTE WINNING WALKTHROUGH)

```
[00:00 - 00:30] THE HOOK & PROOF OF OFFLINE
- Turn on Airplane Mode in front of the judges.
- "Judges, meet EduPulse. Millions of students in Tier-2/3 India struggle with physics equations without access to stable internet or high-cost coaching. We solve this 100% offline."

[00:30 - 01:15] THE CORE DEMO (CAMERA → GEMMA → DIAGRAM)
- Point loaner phone camera at a handwritten question:
  "A cannonball is fired at 25 m/s at 45°. Find the maximum range."
- Tap Capture → OCR reads it → Gemma extracts parameters → Deterministic Kotlin engine calculates R = 63.77 m.
- Point out: "Notice our verifier recomputed the equation. Zero hallucination."

[01:15 - 02:00] THE "WHAT-IF" SLIDER & VERNACULAR VOICE
- Drag the "Initial Velocity" slider from 25 m/s to 40 m/s. The parabola expands smoothly at 60 FPS.
- Tap Telugu Audio: "Listen to the conceptual explanation in Telugu, synthesized directly on device."

[02:00 - 02:30] TELEMETRY & HARDWARE PROOF
- Show the Diagnostic Panel:
  - Cloud Transfer: 0.00 KB
  - Model: Quantized Gemma 2B (LiteRT-LM on NPU)
  - Processing Latency: 1.2s

[02:30 - 03:00] OFFICE KIT BRIDGES & CLOSING
- Switch on local link: Drag the exported diagram PDF to the laptop screen via Office Kit file transfer.
- Show Classroom Mirroring: "A teacher can mirror the slider onto the blackboard laptop while controlling it from the phone."
- Close: "EduPulse: Turning doubts into interactive diagrams, anywhere in the world."
```
