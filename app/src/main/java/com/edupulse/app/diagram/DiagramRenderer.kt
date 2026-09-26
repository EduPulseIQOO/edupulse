package com.edupulse.app.diagram

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Extracts the first signed decimal from strings like "−2.500 m/s²", "10 m/s", "12.5 N". */
private fun parseSignedFloat(input: String?): Float? {
    if (input.isNullOrBlank()) return null
    return Regex("""(-?[0-9]+(?:\.[0-9]+)?)""").find(input.trim())
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramCard(
    diagram: PhysicsDiagram,
    modifier: Modifier = Modifier
) {
    var activeTab by remember(diagram.javaClass) { mutableIntStateOf(0) } // 0: Motion Track, 1: Free-Body Forces
    val scope = rememberCoroutineScope()
    val animProgress = remember(diagram.javaClass) { Animatable(0f) }
    var isSimulating by remember(diagram.javaClass) { mutableStateOf(false) }
    var playbackSpeed by remember(diagram.javaClass) { mutableFloatStateOf(1.0f) }
    var isLooping by remember(diagram.javaClass) { mutableStateOf(true) }
    var animationJob by remember(diagram.javaClass) { mutableStateOf<Job?>(null) }

    // Interactive "What-If?" Sandbox State
    var showSandbox by remember(diagram.javaClass) { mutableStateOf(false) }

    val isDecel = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> diagram.isDecelerating
            is PhysicsDiagram.FreeBody -> true
            else -> true
        }
    }

    val baseU = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                parseSignedFloat(diagram.initialVelocity)?.let { abs(it) }
                    ?: if (diagram.isDecelerating) 10f else 0f
            }
            is PhysicsDiagram.FreeBody -> 10f
            else -> 10f
        }
    }
    val baseM = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics ->
                parseSignedFloat(diagram.mass)?.let { abs(it) } ?: 5f
            is PhysicsDiagram.FreeBody ->
                parseSignedFloat(diagram.mass)?.let { abs(it) } ?: 10f
            else -> 5f
        }
    }
    // acceleration magnitude (sign carried by isDecel)
    val baseA = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                val a = parseSignedFloat(diagram.acceleration)?.let { abs(it) }
                val f = parseSignedFloat(diagram.force)?.let { abs(it) }
                val m = parseSignedFloat(diagram.mass)?.let { abs(it) }
                a ?: (if (f != null && m != null && m > 0f) f / m else 2.5f)
            }
            is PhysicsDiagram.FreeBody -> {
                val net = parseSignedFloat(diagram.netForce)?.let { abs(it) }
                val f = parseSignedFloat(diagram.frictionForce)?.let { abs(it) }
                    ?: parseSignedFloat(diagram.appliedForce)?.let { abs(it) }
                val m = parseSignedFloat(diagram.mass)?.let { abs(it) } ?: 10f
                val netF = net ?: f ?: 20f
                if (m > 0f) netF / m else 2.5f
            }
            else -> 2.5f
        }
    }
    val baseT = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                val t = parseSignedFloat(diagram.time)?.let { abs(it) }
                if (t != null && t > 0f) t
                else if (baseA > 0f && baseU > 0f && diagram.isDecelerating) baseU / baseA
                else 4f
            }
            is PhysicsDiagram.FreeBody -> {
                if (baseA > 0f && baseU > 0f) baseU / baseA else 4f
            }
            else -> 4f
        }
    }
    val baseV = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                parseSignedFloat(diagram.finalVelocity)?.let { abs(it) }
                    ?: if (diagram.isDecelerating) 0f else (baseU + baseA * baseT)
            }
            is PhysicsDiagram.FreeBody -> 0f
            else -> 0f
        }
    }
    val baseS = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                val s = parseSignedFloat(diagram.distance)?.let { abs(it) }
                if (s != null && s > 0f) s
                else if (diagram.isDecelerating && baseA > 0f && baseU > 0f) (baseU * baseU) / (2f * baseA)
                else (baseU * baseT + 0.5f * baseA * baseT * baseT).coerceAtLeast(10f)
            }
            is PhysicsDiagram.FreeBody -> {
                if (baseA > 0f && baseU > 0f) (baseU * baseU) / (2f * baseA) else 20f
            }
            else -> 20f
        }
    }
    val baseF = remember(diagram) {
        when (diagram) {
            is PhysicsDiagram.Kinematics -> {
                parseSignedFloat(diagram.force)?.let { abs(it) } ?: (baseM * baseA)
            }
            is PhysicsDiagram.FreeBody -> {
                parseSignedFloat(diagram.netForce)?.let { abs(it) } ?: (baseM * baseA)
            }
            else -> (baseM * baseA)
        }
    }

    var sandboxU by remember(diagram) { mutableFloatStateOf(baseU) }
    var sandboxM by remember(diagram) { mutableFloatStateOf(baseM) }

    val currentU = if (showSandbox) sandboxU else baseU
    val currentM = if (showSandbox) sandboxM else baseM
    val currentA = baseA
    val currentV = if (showSandbox) {
        if (isDecel) 0f else (currentU + currentA * baseT)
    } else baseV
    val currentF = if (showSandbox) (currentM * currentA) else baseF
    val currentT = if (showSandbox) {
        if (currentA > 0f) (abs(currentV - currentU) / currentA).coerceAtLeast(0.5f) else baseT
    } else baseT
    val currentS = if (showSandbox) {
        if (isDecel) {
            if (currentA > 0f && currentU > 0f) (currentU * currentU) / (2f * currentA) else 20f
        } else {
            (currentU * currentT + 0.5f * currentA * currentT * currentT).coerceAtLeast(10f)
        }
    } else baseS
    val currentKE0 = 0.5f * currentM * (if (isDecel) currentU * currentU else currentV * currentV)

    fun stopAnimation() {
        Log.d("EduPulseSim", "stopAnimation called")
        isSimulating = false
        animationJob?.cancel()
        animationJob = null
    }

    fun startAnimation() {
        Log.d("EduPulseSim", "startAnimation called: currentT=$currentT, speed=$playbackSpeed, looping=$isLooping")
        animationJob?.cancel()
        isSimulating = true
        animationJob = scope.launch {
            try {
                if (animProgress.value >= 0.98f) animProgress.snapTo(0f)
                while (isActive && isSimulating) {
                    val remaining = (1f - animProgress.value).coerceAtLeast(0.01f)
                    val baseDurationMs = (currentT * 1000f).coerceIn(1200f, 6000f)
                    val animDuration = ((baseDurationMs / playbackSpeed) * remaining).toInt().coerceAtLeast(50)
                    animProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = animDuration, easing = LinearEasing)
                    )
                    if (!isLooping || !isSimulating) break
                    delay(750)
                    animProgress.snapTo(0f)
                }
            } finally {
                if (animationJob == coroutineContext[Job]) {
                    animationJob = null
                    isSimulating = false
                }
            }
        }
    }

    fun toggleSimulation() {
        Log.d("EduPulseSim", "toggleSimulation: currently simulating=$isSimulating")
        if (isSimulating) {
            stopAnimation()
        } else {
            startAnimation()
        }
    }

    fun resetSimulation() {
        Log.d("EduPulseSim", "resetSimulation called")
        stopAnimation()
        scope.launch {
            animProgress.snapTo(0f)
        }
    }

    // Auto-start dynamic simulation when diagram first appears
    LaunchedEffect(diagram.javaClass) {
        startAnimation()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Live Status & Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (simIcon, simTitle) = when (diagram) {
                    is PhysicsDiagram.Kinematics -> "🏎️" to "Physics: Motion Simulator"
                    is PhysicsDiagram.FreeBody -> "🏎️" to "Physics: Motion & Force Simulator"
                    is PhysicsDiagram.Projectile -> "🏹" to "Physics: 2D Projectile Trajectory"
                    is PhysicsDiagram.MathFunction -> "📐" to "Mathematics: Function Grapher"
                    is PhysicsDiagram.ChemistryAtom -> "⚛️" to "Chemistry: Bohr Atomic Model"
                    is PhysicsDiagram.ChemistryPh -> "🧪" to "Chemistry: Acid-Base pH Scale"
                    is PhysicsDiagram.BiologyGenetics -> "🧬" to "Biology: Punnett Square Genetics"
                    is PhysicsDiagram.ElectricCircuit -> "💡" to "Physics: Electric Circuit & Ohm's Law"
                    is PhysicsDiagram.HarmonicPendulum -> "⏱️" to "Physics: Simple Harmonic Pendulum"
                    is PhysicsDiagram.WaveMotion -> "〰️" to "Physics: Wave Mechanics & Frequency"
                    is PhysicsDiagram.ChemistryReaction -> "⚗️" to "Chemistry: Reaction Stoichiometry"
                    is PhysicsDiagram.BiologyCell -> "🔬" to "Biology: Cell Structure & Organelles"
                    is PhysicsDiagram.EcosystemPyramid -> "🌲" to "Biology: Trophic Energy Pyramid"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(simIcon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = simTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (diagram is PhysicsDiagram.Kinematics || diagram is PhysicsDiagram.FreeBody) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSimulating) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = if (isSimulating) "● LIVE" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isSimulating) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // View Toggle Pills (Track / Forces)
                if (diagram is PhysicsDiagram.Kinematics || diagram is PhysicsDiagram.FreeBody) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (activeTab == 0) {
                            FilledTonalButton(
                                onClick = {
                                    Log.d("EduPulseSim", "Tab selected: Track")
                                    activeTab = 0
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("🏎️ Track", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    Log.d("EduPulseSim", "Tab selected: Track")
                                    activeTab = 0
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("🏎️ Track", fontSize = 11.sp)
                            }
                        }

                        if (activeTab == 1) {
                            FilledTonalButton(
                                onClick = {
                                    Log.d("EduPulseSim", "Tab selected: Forces")
                                    activeTab = 1
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("⚖️ Forces", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    Log.d("EduPulseSim", "Tab selected: Forces")
                                    activeTab = 1
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("⚖️ Forces", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Drawing
            when (diagram) {
                is PhysicsDiagram.Kinematics -> {
                    if (activeTab == 0) {
                        KinematicsTrackCanvas(
                            progressProvider = { animProgress.value },
                            currentU = currentU,
                            currentV = currentV,
                            currentA = currentA,
                            currentM = currentM,
                            currentF = currentF,
                            currentS = currentS,
                            isDecelerating = isDecel,
                            isSimulating = isSimulating,
                            onTap = { toggleSimulation() },
                            onSeek = { fraction ->
                                stopAnimation()
                                scope.launch { animProgress.snapTo(fraction) }
                            }
                        )
                    } else {
                        FreeBodyForcesCanvas(
                            mass = "${currentM.toInt()} kg",
                            force = "${String.format(Locale.US, "%.1f", currentF)} N",
                            isDecelerating = isDecel
                        )
                    }
                }
                is PhysicsDiagram.FreeBody -> {
                    if (activeTab == 0) {
                        KinematicsTrackCanvas(
                            progressProvider = { animProgress.value },
                            currentU = currentU,
                            currentV = currentV,
                            currentA = currentA,
                            currentM = currentM,
                            currentF = currentF,
                            currentS = currentS,
                            isDecelerating = isDecel,
                            isSimulating = isSimulating,
                            onTap = { toggleSimulation() },
                            onSeek = { fraction ->
                                stopAnimation()
                                scope.launch { animProgress.snapTo(fraction) }
                            }
                        )
                    } else {
                        FreeBodyForcesCanvas(
                            mass = diagram.mass ?: "${currentM.toInt()} kg",
                            force = diagram.frictionForce ?: diagram.appliedForce ?: diagram.netForce ?: "${String.format(Locale.US, "%.1f", currentF)} N",
                            isDecelerating = true
                        )
                    }
                }
                is PhysicsDiagram.Projectile -> {
                    ProjectileCanvas(diagram = diagram)
                }
                is PhysicsDiagram.MathFunction -> {
                    MathGraphCanvas(diagram = diagram)
                }
                is PhysicsDiagram.ChemistryAtom -> {
                    ChemistryAtomCanvas(diagram = diagram)
                }
                is PhysicsDiagram.ChemistryPh -> {
                    ChemistryPhCanvas(diagram = diagram)
                }
                is PhysicsDiagram.BiologyGenetics -> {
                    BiologyGeneticsCanvas(diagram = diagram)
                }
                is PhysicsDiagram.ElectricCircuit -> {
                    ElectricCircuitCanvas(diagram = diagram)
                }
                is PhysicsDiagram.HarmonicPendulum -> {
                    HarmonicPendulumCanvas(diagram = diagram)
                }
                is PhysicsDiagram.WaveMotion -> {
                    WaveMotionCanvas(diagram = diagram)
                }
                is PhysicsDiagram.ChemistryReaction -> {
                    ChemistryReactionCanvas(diagram = diagram)
                }
                is PhysicsDiagram.BiologyCell -> {
                    BiologyCellCanvas(diagram = diagram)
                }
                is PhysicsDiagram.EcosystemPyramid -> {
                    EcosystemPyramidCanvas(diagram = diagram)
                }
            }

            // Interactive Controls & Dynamic Telemetry (when in Track view)
            if ((diagram is PhysicsDiagram.Kinematics || diagram is PhysicsDiagram.FreeBody) && activeTab == 0) {
                // Live Physics Telemetry HUD (decoupled to avoid recomposing buttons)
                LiveTelemetryHud(
                    progressProvider = { animProgress.value },
                    currentT = currentT,
                    currentU = currentU,
                    currentV = currentV,
                    currentS = currentS,
                    currentM = currentM,
                    currentKE0 = currentKE0,
                    isDecelerating = isDecel
                )

                // Interactive Timeline Scrubber Slider (decoupled)
                TimelineScrubber(
                    progressProvider = { animProgress.value },
                    onSeek = { newVal ->
                        stopAnimation()
                        scope.launch { animProgress.snapTo(newVal) }
                    },
                    currentT = currentT,
                    currentS = currentS
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Row 1: Primary Playback Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Button
                    Button(
                        onClick = { toggleSimulation() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (isSimulating) "⏸ Pause" else "▶ Play",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset Button
                    OutlinedButton(
                        onClick = { resetSimulation() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("⏮ Reset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    // Loop Toggle Button
                    OutlinedButton(
                        onClick = {
                            isLooping = !isLooping
                            if (isLooping && !isSimulating) {
                                startAnimation()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLooping) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                    ) {
                        Text(
                            if (isLooping) "🔁 Loop ON" else "🔁 Loop OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Speed Multipliers & Sandbox Lab Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sandbox Toggle Button
                    OutlinedButton(
                        onClick = {
                            showSandbox = !showSandbox
                            Log.d("EduPulseSim", "Sandbox toggled: showSandbox=$showSandbox")
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showSandbox) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (showSandbox) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            if (showSandbox) "🧪 Sandbox Close" else "🧪 What-If? Lab",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showSandbox) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Speed Multipliers
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.5f to "0.5x", 1.0f to "1.0x", 2.0f to "2.0x").forEach { (speed, label) ->
                            val isSelected = abs(playbackSpeed - speed) < 0.1f
                            if (isSelected) {
                                FilledTonalButton(
                                    onClick = {
                                        Log.d("EduPulseSim", "Speed multiplier selected: $label")
                                        playbackSpeed = speed
                                        if (isSimulating) startAnimation()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        Log.d("EduPulseSim", "Speed multiplier selected: $label")
                                        playbackSpeed = speed
                                        if (isSimulating) startAnimation()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // If Sandbox is opened: Interactive Dynamic Experimentation
                AnimatedVisibility(visible = showSandbox) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🧪 Live What-If? Speed & Mass Lab",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        "s = u² / 2a",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                // Speed Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Initial Velocity (u):", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        String.format(Locale.US, "%.1f m/s", sandboxU),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Slider(
                                    value = sandboxU,
                                    onValueChange = {
                                        sandboxU = it
                                        stopAnimation()
                                        scope.launch { animProgress.snapTo(0f) }
                                    },
                                    valueRange = 2f..30f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.tertiary,
                                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                                    )
                                )

                                // Preset Speed Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(5f, 10f, 15f, 25f).forEach { preset ->
                                        val isSelected = abs(sandboxU - preset) < 0.5f
                                        if (isSelected) {
                                            FilledTonalButton(
                                                onClick = {
                                                    Log.d("EduPulseSim", "Sandbox preset speed selected: $preset")
                                                    sandboxU = preset
                                                    startAnimation()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Text("${preset.toInt()} m/s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    Log.d("EduPulseSim", "Sandbox preset speed selected: $preset")
                                                    sandboxU = preset
                                                    startAnimation()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Text("${preset.toInt()} m/s", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Live Derived Results
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        String.format(Locale.US, "Stopping Distance: %.1f m", currentS),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                    Text(
                                        String.format(Locale.US, "Time to Stop: %.1f s", currentT),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            // Key Parameter Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (diagram) {
                    is PhysicsDiagram.Kinematics -> {
                        ParamChip("m", "${currentM.toInt()} kg")
                        ParamChip("u", String.format(Locale.US, "%.1f m/s", currentU), Color(0xFF2E7D32))
                        ParamChip("v", String.format(Locale.US, "%.1f m/s", currentV), if (isDecel) Color(0xFFC62828) else Color(0xFF2E7D32))
                        ParamChip("a", String.format(Locale.US, if (isDecel) "-%.1f m/s²" else "+%.1f m/s²", currentA))
                        ParamChip("F", String.format(Locale.US, if (isDecel) "-%.1f N" else "+%.1f N", currentF), Color(0xFFD84315))
                        ParamChip("s", String.format(Locale.US, "%.1f m", currentS), Color(0xFF1565C0))
                        ParamChip("t", String.format(Locale.US, "%.1f s", currentT))
                    }
                    is PhysicsDiagram.Projectile -> {
                        // Dynamic telemetry rendered live inside ProjectileCanvas
                    }
                    is PhysicsDiagram.FreeBody -> {
                        diagram.mass?.let { ParamChip("m", it) }
                        diagram.normalForce?.let { ParamChip("Normal", it, Color(0xFF1976D2)) }
                        diagram.gravityForce?.let { ParamChip("Weight", it, Color(0xFF7B1FA2)) }
                        diagram.appliedForce?.let { ParamChip("Applied", it, Color(0xFF2E7D32)) }
                        diagram.frictionForce?.let { ParamChip("Friction", it, Color(0xFFD32F2F)) }
                        diagram.netForce?.let { ParamChip("F_net", it, Color(0xFFD84315)) }
                    }
                    is PhysicsDiagram.MathFunction -> {
                        ParamChip("Equation", diagram.equation, Color(0xFF1565C0))
                        ParamChip("Type", diagram.funcType)
                        if (diagram.funcType == "QUADRATIC") {
                            ParamChip("Vertex", "(${String.format(Locale.US, "%.1f", diagram.vertexX)}, ${String.format(Locale.US, "%.1f", diagram.vertexY)})", Color(0xFF7B1FA2))
                            if (diagram.root1 != null && diagram.root2 != null) {
                                ParamChip("Roots", "${String.format(Locale.US, "%.2f", diagram.root1)}, ${String.format(Locale.US, "%.2f", diagram.root2)}", Color(0xFF2E7D32))
                            }
                        }
                        ParamChip("y-int", String.format(Locale.US, "%.1f", diagram.yIntercept))
                    }
                    is PhysicsDiagram.ChemistryAtom -> {
                        ParamChip("Element", "${diagram.element} (${diagram.symbol})", Color(0xFF1565C0))
                        ParamChip("Z (Protons)", "${diagram.atomicNumber}", Color(0xFFD32F2F))
                        ParamChip("A (Mass)", "${diagram.massNumber}")
                        ParamChip("Neutrons", "${diagram.massNumber - diagram.atomicNumber}")
                        val shells = listOf("K=${diagram.kShell}", "L=${diagram.lShell}", "M=${diagram.mShell}", "N=${diagram.nShell}").filter { !it.endsWith("=0") }
                        ParamChip("Shells", shells.joinToString(", "), Color(0xFF2E7D32))
                    }
                    is PhysicsDiagram.ChemistryPh -> {
                        ParamChip("Substance", diagram.substance, Color(0xFF1565C0))
                        ParamChip("pH Value", String.format(Locale.US, "%.1f", diagram.ph), Color(0xFFD84315))
                        val nature = when {
                            diagram.ph < 6.5f -> "Acidic"
                            diagram.ph in 6.5f..7.5f -> "Neutral"
                            else -> "Alkaline / Basic"
                        }
                        ParamChip("Nature", nature, if (diagram.ph < 7f) Color(0xFFD32F2F) else Color(0xFF1976D2))
                    }
                    is PhysicsDiagram.BiologyGenetics -> {
                        ParamChip("Trait", diagram.trait, Color(0xFF7B1FA2))
                        ParamChip("Parents", "${diagram.p1} × ${diagram.p2}", Color(0xFF1565C0))
                        ParamChip("Dominant", diagram.dominantTrait, Color(0xFF2E7D32))
                        ParamChip("Recessive", diagram.recessiveTrait, Color(0xFFD84315))
                    }
                    is PhysicsDiagram.ElectricCircuit -> {
                        ParamChip("Voltage (V)", "${diagram.voltage.toInt()} V", Color(0xFF1976D2))
                        ParamChip("Resistance (R)", "${diagram.resistance.toInt()} Ω", Color(0xFFD84315))
                        ParamChip("Current (I)", "${String.format(Locale.US, "%.2f", diagram.current)} A", Color(0xFF2E7D32))
                        ParamChip("Power (P)", "${String.format(Locale.US, "%.1f", diagram.power)} W", Color(0xFF7B1FA2))
                    }
                    is PhysicsDiagram.HarmonicPendulum -> {
                        ParamChip("Length (L)", "${String.format(Locale.US, "%.2f", diagram.length)} m", Color(0xFF1976D2))
                        ParamChip("Gravity (g)", "${diagram.gravity} m/s²")
                        ParamChip("Period (T)", "${String.format(Locale.US, "%.2f", diagram.period)} s", Color(0xFF2E7D32))
                        ParamChip("Freq (f)", "${String.format(Locale.US, "%.2f", diagram.frequency)} Hz", Color(0xFF7B1FA2))
                    }
                    is PhysicsDiagram.WaveMotion -> {
                        ParamChip("Speed (v)", "${diagram.waveSpeed.toInt()} m/s", Color(0xFF1976D2))
                        ParamChip("Frequency (f)", "${diagram.frequency.toInt()} Hz", Color(0xFF2E7D32))
                        ParamChip("Wavelength (λ)", "${String.format(Locale.US, "%.2f", diagram.wavelength)} m", Color(0xFFD84315))
                        ParamChip("Amplitude", "${diagram.amplitude}", Color(0xFF7B1FA2))
                    }
                    is PhysicsDiagram.ChemistryReaction -> {
                        ParamChip("Reaction", diagram.reactionName, Color(0xFF1976D2))
                        ParamChip("Type", diagram.reactionType, Color(0xFF7B1FA2))
                        ParamChip("Reactants", diagram.reactants.joinToString(" + "), Color(0xFFD84315))
                        ParamChip("Products", diagram.products.joinToString(" + "), Color(0xFF2E7D32))
                    }
                    is PhysicsDiagram.BiologyCell -> {
                        ParamChip("Cell Type", diagram.cellType, Color(0xFF2E7D32))
                        ParamChip("Organelles", "${diagram.keyOrganelles.size} Key Structures", Color(0xFF1976D2))
                    }
                    is PhysicsDiagram.EcosystemPyramid -> {
                        ParamChip("Ecosystem", diagram.ecosystemName, Color(0xFF2E7D32))
                        ParamChip("Primary Energy", "${diagram.primaryEnergy.toInt()} J", Color(0xFFD84315))
                        ParamChip("Rule", "10% Energy Transfer", Color(0xFF7B1FA2))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryHud(
    progressProvider: () -> Float,
    currentT: Float,
    currentU: Float,
    currentV: Float,
    currentS: Float,
    currentM: Float,
    currentKE0: Float,
    isDecelerating: Boolean
) {
    val tau = progressProvider()
    val instantT = (tau * currentT).coerceAtLeast(0f)
    val instantV = if (isDecelerating) {
        (currentU * (1f - tau)).coerceAtLeast(0f)
    } else {
        (currentU + (currentV - currentU) * tau).coerceAtLeast(0f)
    }
    val distanceFraction = if (isDecelerating) {
        (2f * tau - tau * tau).coerceIn(0f, 1f)
    } else {
        (tau * tau).coerceIn(0f, 1f)
    }
    val instantS = (currentS * distanceFraction).coerceAtLeast(0f)
    val instantKE = (0.5f * currentM * instantV * instantV).coerceAtLeast(0f)
    val maxKE = maxOf(currentKE0, 1f)
    val keFraction = (instantKE / maxKE).coerceIn(0f, 1f)
    val isDone = tau >= 0.98f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TelemetryItem(
                    label = "⏱️ Time",
                    value = String.format(Locale.US, "%.2fs", instantT),
                    subValue = "of ${String.format(Locale.US, "%.1fs", currentT)}"
                )
                TelemetryItem(
                    label = "🚀 Velocity",
                    value = String.format(Locale.US, "%.1f m/s", instantV),
                    subValue = if (isDecelerating) (if (isDone) "Stopped" else "Decelerating") else (if (isDone) "Target Speed" else "Accelerating"),
                    valueColor = if (instantV > 0.5f) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                )
                TelemetryItem(
                    label = "📏 Position",
                    value = String.format(Locale.US, "%.1fm", instantS),
                    subValue = "of ${String.format(Locale.US, "%.1fm", currentS)}",
                    valueColor = Color(0xFF1565C0)
                )
                TelemetryItem(
                    label = "⚡ Kinetic E",
                    value = String.format(Locale.US, "%.0f J", instantKE),
                    subValue = "of ${String.format(Locale.US, "%.0f J", currentKE0)}",
                    valueColor = Color(0xFF7B1FA2)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Work / Energy Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDecelerating) "⚡ Friction Work (Dissipation):" else "⚡ Kinetic Energy Gain:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isDecelerating) "${((1f - keFraction) * 100).toInt()}% converted to heat" else "${(keFraction * 100).toInt()}% work done",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) Color(0xFF2E7D32) else Color(0xFFD84315)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (if (isDecelerating) (1f - keFraction) else keFraction).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = if (isDecelerating) Color(0xFFD84315) else Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    progressProvider: () -> Float,
    onSeek: (Float) -> Unit,
    currentT: Float,
    currentS: Float
) {
    // Local slider position — tracks animation when idle, holds still during drag
    var sliderPos by remember(currentT, currentS) { mutableFloatStateOf(progressProvider()) }
    var isDragging by remember(currentT, currentS) { mutableStateOf(false) }

    // Sync from animation only when the user isn't dragging
    val liveProgress = progressProvider()
    if (!isDragging) { sliderPos = liveProgress }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "0.0s",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = sliderPos,
            onValueChange = { v ->
                isDragging = true
                sliderPos = v
                onSeek(v)
            },
            onValueChangeFinished = { isDragging = false },
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = String.format(Locale.US, "%.1fs (%.0fm)", currentT, currentS),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0)
        )
    }
}


@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    subValue: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ParamChip(label: String, value: String, accentColor: Color? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label = ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor ?: MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KinematicsTrackCanvas(
    progressProvider: () -> Float,
    currentU: Float,
    currentV: Float,
    currentA: Float,
    currentM: Float,
    currentF: Float,
    currentS: Float,
    isDecelerating: Boolean,
    isSimulating: Boolean,
    onTap: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val velocityColor = Color(0xFF2E7D32) // Green
    val brakingColor = Color(0xFFD32F2F)  // Red
    val distanceColor = Color(0xFF1976D2) // Blue

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(isDecelerating, currentS, currentU, currentV) {
                detectTapGestures { offset ->
                    val startX = 65f * density
                    val endX   = size.width - 75f * density
                    val groundY = size.height * 0.60f
                    if (offset.y >= groundY - 16f * density) {
                        // Tap on the track / distance ruler area -> seek to that fraction
                        if (endX > startX) {
                            val fraction = ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                            onSeek(fraction)
                        }
                    } else {
                        // Tap on the cart / velocity arrow / upper canvas area -> toggle play/pause
                        onTap()
                    }
                }
            }
    ) {

        val tau = progressProvider()
        val maxV = maxOf(currentU, currentV, 1f)
        val distanceFraction = if (isDecelerating) {
            (2f * tau - tau * tau).coerceIn(0f, 1f)
        } else {
            (tau * tau).coerceIn(0f, 1f)
        }
        val instantV = if (isDecelerating) {
            (currentU * (1f - tau)).coerceAtLeast(0f)
        } else {
            (currentU + (currentV - currentU) * tau).coerceAtLeast(0f)
        }
        val instantS = (currentS * distanceFraction).coerceAtLeast(0f)
        val speedFraction = (instantV / maxV).coerceIn(0f, 1f)

        val width = size.width
        val height = size.height

        val groundY = height * 0.60f
        val startX = 65.dp.toPx()
        val endX = width - 75.dp.toPx()
        val trackSpan = endX - startX

        // 1. Draw ground track
        drawLine(
            color = outlineColor.copy(alpha = 0.7f),
            start = Offset(16.dp.toPx(), groundY),
            end = Offset(width - 16.dp.toPx(), groundY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Ground hash lines
        val hashStep = 18.dp.toPx()
        var hx = 22.dp.toPx()
        while (hx < width - 22.dp.toPx()) {
            drawLine(
                color = outlineColor.copy(alpha = 0.25f),
                start = Offset(hx, groundY),
                end = Offset(hx - 8.dp.toPx(), groundY + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
            hx += hashStep
        }

        // 2. Start and End Position Markers
        val blockW = 66.dp.toPx()
        val blockH = 38.dp.toPx()
        val wheelRadius = 6.dp.toPx()

        // Dynamic Skid / Tire Trail
        val currentBlockX = startX + (trackSpan * distanceFraction)
        if (distanceFraction > 0.01f) {
            drawLine(
                color = if (isDecelerating) Color(0x772B2B2B) else Color(0x551976D2),
                start = Offset(startX, groundY - 1f),
                end = Offset(currentBlockX, groundY - 1f),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Ghost End Position
        drawRoundRect(
            color = outlineColor.copy(alpha = 0.2f),
            topLeft = Offset(endX - blockW / 2, groundY - blockH - wheelRadius * 2),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        )

        // Target flag / Rest indicator at end
        val flagColor = if (isDecelerating) brakingColor else Color(0xFF2E7D32)
        val flagTopY = groundY - blockH - wheelRadius * 2 - 14.dp.toPx()
        drawLine(
            color = flagColor,
            start = Offset(endX, flagTopY),
            end = Offset(endX, groundY),
            strokeWidth = 2.dp.toPx()
        )
        // Pennant flag
        val flagPath = Path().apply {
            moveTo(endX, flagTopY)
            lineTo(endX + 14.dp.toPx(), flagTopY + 6.dp.toPx())
            lineTo(endX, flagTopY + 12.dp.toPx())
            close()
        }
        drawPath(path = flagPath, color = flagColor)

        // Ripple Wave when Done
        if (tau >= 0.98f) {
            drawCircle(
                color = Color(0xFF2E7D32).copy(alpha = 0.35f),
                radius = 16.dp.toPx(),
                center = Offset(endX, flagTopY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Dynamic Moving Cart & Rotating Wheels
        val cartTop = groundY - wheelRadius * 2 - blockH

        // Contact Friction Sparks / Smoke Particles behind rear wheel during motion
        if (isSimulating && speedFraction > 0.05f) {
            val rearWheelX = currentBlockX - 18.dp.toPx()
            val particleSeed = (tau * 100).toInt()
            for (i in 0 until 4) {
                val pProgress = ((particleSeed + i * 5) % 20) / 20f
                val px = rearWheelX - (pProgress * 22.dp.toPx())
                val py = groundY - wheelRadius - (pProgress * 10.dp.toPx())
                val pAlpha = (1f - pProgress) * 0.5f * speedFraction
                drawCircle(
                    color = Color(0xFF757575).copy(alpha = pAlpha),
                    radius = (1.5f + pProgress * 2.5f).dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Draw Cart Body
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(currentBlockX - blockW / 2, cartTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(currentBlockX - blockW / 2, cartTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Rotating Wheels (Front & Rear)
        val wheelAngle = (instantS * 75f / wheelRadius)
        val wheel1Center = Offset(currentBlockX - 18.dp.toPx(), groundY - wheelRadius)
        val wheel2Center = Offset(currentBlockX + 18.dp.toPx(), groundY - wheelRadius)
        val wheelColor = Color(0xFF263238)

        drawSpokeWheel(wheel1Center, wheelRadius, wheelAngle, wheelColor)
        drawSpokeWheel(wheel2Center, wheelRadius, wheelAngle, wheelColor)

        // 4. Dynamic Vector Arrows: Velocity & Force
        val vArrowY = cartTop - 12.dp.toPx()
        val fArrowY = cartTop + blockH / 2

        if (speedFraction > 0.04f || !isDecelerating) {
            // Velocity Arrow (Right ->) dynamically scaled by instantaneous velocity
            val vArrowLen = (70.dp.toPx() * speedFraction).coerceIn(16.dp.toPx(), 80.dp.toPx())
            val vColor = if (speedFraction > 0.4f) velocityColor else Color(0xFFEF6C00)
            drawArrow(
                color = vColor,
                start = Offset(currentBlockX, vArrowY),
                end = Offset(currentBlockX + vArrowLen, vArrowY),
                strokeWidth = 3.dp.toPx()
            )

            if (isDecelerating) {
                // Braking Force Arrow (Left <-) opposing motion
                val fArrowLen = 42.dp.toPx()
                drawArrow(
                    color = brakingColor,
                    start = Offset(currentBlockX - blockW / 2, fArrowY),
                    end = Offset(currentBlockX - blockW / 2 - fArrowLen, fArrowY),
                    strokeWidth = 3.dp.toPx()
                )
            } else {
                // Accelerating Force Arrow (Right ->) in direction of motion
                val fArrowLen = 42.dp.toPx()
                drawArrow(
                    color = Color(0xFF1976D2),
                    start = Offset(currentBlockX + blockW / 2, fArrowY),
                    end = Offset(currentBlockX + blockW / 2 + fArrowLen, fArrowY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // 5. Dimension Line (Distance 's') below ground track
        val dimY = groundY + 22.dp.toPx()
        val tickH = 6.dp.toPx()

        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY - tickH),
            end = Offset(startX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(endX, dimY - tickH),
            end = Offset(endX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        drawDoubleArrow(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY),
            end = Offset(endX, dimY),
            strokeWidth = 1.8.dp.toPx()
        )

        // Native Text Rendering on Canvas for 60/120fps dynamic text
        drawContext.canvas.nativeCanvas.apply {
            // Mass label inside cart body
            textPaint.color = android.graphics.Color.WHITE
            textPaint.textSize = 28f
            drawText("${currentM.toInt()} kg", currentBlockX, cartTop + blockH * 0.65f, textPaint)

            // Dynamic Velocity label above velocity vector
            if (speedFraction > 0.05f || !isDecelerating) {
                textPaint.color = if (speedFraction > 0.4f) 0xFF2E7D32.toInt() else 0xFFEF6C00.toInt()
                textPaint.textSize = 26f
                drawText(
                    "v = ${String.format(Locale.US, "%.1f m/s", instantV)}",
                    currentBlockX + 32.dp.toPx(),
                    vArrowY - 6.dp.toPx(),
                    textPaint
                )

                if (isDecelerating) {
                    textPaint.color = 0xFFD32F2F.toInt()
                    textPaint.textSize = 26f
                    drawText(
                        "F = -${String.format(Locale.US, "%.1f N", currentF)}",
                        currentBlockX - blockW / 2 - 22.dp.toPx(),
                        fArrowY - 8.dp.toPx(),
                        textPaint
                    )
                } else {
                    textPaint.color = 0xFF1976D2.toInt()
                    textPaint.textSize = 26f
                    drawText(
                        "F = +${String.format(Locale.US, "%.1f N", currentF)}",
                        currentBlockX + blockW / 2 + 22.dp.toPx(),
                        fArrowY - 8.dp.toPx(),
                        textPaint
                    )
                }
            }

            // Indicator text at end
            if (tau >= 0.98f) {
                textPaint.color = 0xFF2E7D32.toInt()
                textPaint.textSize = 26f
                drawText(
                    if (isDecelerating) "✓ STOPPED" else "🏁 TARGET REACHED",
                    endX,
                    flagTopY - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

private fun DrawScope.drawSpokeWheel(
    center: Offset,
    radius: Float,
    rotationRad: Float,
    wheelColor: Color
) {
    // Outer tire
    drawCircle(color = wheelColor, radius = radius, center = center)
    // Rim highlight
    drawCircle(color = Color.LightGray, radius = radius * 0.7f, center = center)
    // Hub
    drawCircle(color = wheelColor, radius = radius * 0.28f, center = center)

    // 4 spinning cross-spokes
    val spokeLen = radius * 0.7f
    for (i in 0 until 4) {
        val angle = rotationRad + (i * (PI / 2.0)).toFloat()
        val sx = center.x + spokeLen * cos(angle)
        val sy = center.y + spokeLen * sin(angle)
        drawLine(
            color = Color.DarkGray,
            start = center,
            end = Offset(sx, sy),
            strokeWidth = 1.5f
        )
    }
}

@Composable
private fun FreeBodyForcesCanvas(
    mass: String,
    force: String,
    isDecelerating: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val normalColor = Color(0xFF1976D2)  // Blue
    val gravityColor = Color(0xFF7B1FA2) // Purple
    val redColor = Color(0xFFD32F2F)     // Red
    val greenColor = Color(0xFF388E3C)   // Green

    var activeForceName by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        activeForceName = when {
                            dy < -20.dp.toPx() -> if (activeForceName == "Normal") null else "Normal"
                            dy > 20.dp.toPx() -> if (activeForceName == "Gravity") null else "Gravity"
                            dx < -20.dp.toPx() -> if (activeForceName == "Friction") null else "Friction"
                            dx > 20.dp.toPx() -> if (activeForceName == "Motion") null else "Motion"
                            else -> if (activeForceName != null) null else "Mass"
                        }
                    }
                }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val boxSize = 54.dp.toPx()

            // Center Box
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(cx - boxSize / 2, cy - boxSize / 2),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(cx - boxSize / 2, cy - boxSize / 2),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 1. Normal Force (Upwards ↑)
            drawArrow(
                color = if (activeForceName == "Normal") Color(0xFF0D47A1) else normalColor,
                start = Offset(cx, cy - boxSize / 2),
                end = Offset(cx, cy - boxSize / 2 - 45.dp.toPx()),
                strokeWidth = if (activeForceName == "Normal") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 2. Gravitational Force (Downwards ↓)
            drawArrow(
                color = if (activeForceName == "Gravity") Color(0xFF4A148C) else gravityColor,
                start = Offset(cx, cy + boxSize / 2),
                end = Offset(cx, cy + boxSize / 2 + 45.dp.toPx()),
                strokeWidth = if (activeForceName == "Gravity") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 3. Motion vector (Right →)
            drawArrow(
                color = if (activeForceName == "Motion") Color(0xFF1B5E20) else greenColor,
                start = Offset(cx + boxSize / 2, cy),
                end = Offset(cx + boxSize / 2 + 50.dp.toPx(), cy),
                strokeWidth = if (activeForceName == "Motion") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 4. Retarding / Friction Force (Left ←)
            drawArrow(
                color = if (activeForceName == "Friction") Color(0xFFB71C1C) else redColor,
                start = Offset(cx - boxSize / 2, cy),
                end = Offset(cx - boxSize / 2 - 50.dp.toPx(), cy),
                strokeWidth = if (activeForceName == "Friction") 4.dp.toPx() else 2.5.dp.toPx()
            )
        }

        // Interactive Force Selection Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "Normal" to "Normal ⬆",
                "Gravity" to "Gravity ⬇",
                "Motion" to "Motion ➡",
                "Friction" to "Friction ⬅"
            ).forEach { (fKey, fLbl) ->
                val isSel = activeForceName == fKey
                if (isSel) {
                    FilledTonalButton(
                        onClick = { activeForceName = null },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(fLbl, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { activeForceName = fKey },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(fLbl, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Interactive Vector Inspection Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            val text = when (activeForceName) {
                "Normal" -> "⬆ Normal Reaction (N = mg): Perpendicular contact force supporting mass."
                "Gravity" -> "⬇ Gravity Force (W = mg): Downward pull of Earth toward center."
                "Friction" -> "⬅ Friction / Retarding Force ($force): Opposes direction of velocity, slowing block."
                "Motion" -> "➡ Motion Vector: Forward initial velocity direction."
                "Mass" -> "📦 Body of mass $mass: In contact with rough horizontal surface."
                else -> "👆 Tap any force button or arrow above to inspect its physical role"
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (activeForceName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontWeight = if (activeForceName != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProjectileCanvas(
    diagram: PhysicsDiagram.Projectile,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val scope = rememberCoroutineScope()

    // Parse base diagram values
    val baseAngleDeg = parseSignedFloat(diagram.angle) ?: 45f
    val baseV0 = parseSignedFloat(diagram.velocity) ?: 20f

    // Interactive mutable state for real-time comparison (e.g. 30° vs 60°)
    var currentAngle by remember(diagram.javaClass) { mutableFloatStateOf(baseAngleDeg) }
    var currentV0 by remember(diagram.javaClass) { mutableFloatStateOf(baseV0) }
    var isFlying by remember(diagram.javaClass) { mutableStateOf(false) }
    var isLooping by remember(diagram.javaClass) { mutableStateOf(false) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val projProgress = remember(diagram.javaClass) { Animatable(0f) }

    // Dynamic physics recalculations
    val g = 9.8f
    val angleRad = (currentAngle * PI / 180.0).toFloat()
    val sinA = sin(angleRad)
    val dynFlight = ((2f * currentV0 * sinA) / g).coerceAtLeast(0.5f)
    val dynHMax = ((currentV0 * currentV0 * sinA * sinA) / (2f * g)).coerceAtLeast(0.5f)
    val dynRange = ((currentV0 * currentV0 * sin(2f * angleRad)) / g).coerceAtLeast(1f)

    fun launchProjectile(newAngle: Float = currentAngle, newV0: Float = currentV0, restartFromZero: Boolean = true) {
        Log.d("EduPulseSim", "launchProjectile: angle=$newAngle, v0=$newV0, restartFromZero=$restartFromZero")
        currentAngle = newAngle
        currentV0 = newV0
        animationJob?.cancel()
        isFlying = true
        animationJob = scope.launch {
            try {
                if (restartFromZero || projProgress.value >= 0.98f) {
                    projProgress.snapTo(0f)
                }
                while (isActive && isFlying) {
                    val aRad = (currentAngle * PI / 180.0).toFloat()
                    val sA = sin(aRad)
                    val flightSec = ((2f * currentV0 * sA) / 9.8f).coerceAtLeast(0.5f)
                    val durationMs = (flightSec * 900f).coerceIn(1000f, 5000f).toInt()
                    val remaining = (1f - projProgress.value).coerceAtLeast(0.01f)
                    val animTime = (durationMs * remaining).toInt().coerceAtLeast(50)
                    projProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = animTime, easing = FastOutSlowInEasing)
                    )
                    if (!isLooping || !isFlying) break
                    kotlinx.coroutines.delay(600)
                    projProgress.snapTo(0f)
                }
            } finally {
                if (animationJob == coroutineContext[Job]) {
                    animationJob = null
                    isFlying = false
                }
            }
        }
    }

    fun toggleFlight() {
        Log.d("EduPulseSim", "toggleFlight: currently isFlying=$isFlying")
        if (isFlying) {
            isFlying = false
            animationJob?.cancel()
            animationJob = null
        } else {
            launchProjectile(restartFromZero = projProgress.value >= 0.98f)
        }
    }

    fun resetFlight() {
        Log.d("EduPulseSim", "resetFlight called")
        isFlying = false
        animationJob?.cancel()
        animationJob = null
        scope.launch {
            projProgress.snapTo(0f)
        }
    }

    LaunchedEffect(diagram.javaClass) {
        launchProjectile()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        toggleFlight()
                    }
                }
        ) {
            val groundY = size.height * 0.82f
            val startX  = 44.dp.toPx()
            val endX    = size.width - 44.dp.toPx()
            val trackW  = endX - startX

            val apexX = startX + trackW / 2f
            val canvasHeightAvail = groundY - 10.dp.toPx()
            val refH = (currentV0 * currentV0) / (2f * 9.8f)
            val hFraction = (dynHMax / refH.coerceAtLeast(1f)).coerceIn(0.1f, 0.95f)
            val apexY = groundY - canvasHeightAvail * hFraction

            // Ground line
            drawLine(
                color = outlineColor,
                start = Offset(20.dp.toPx(), groundY),
                end = Offset(size.width - 20.dp.toPx(), groundY),
                strokeWidth = 2.dp.toPx()
            )

            // Dashed parabolic trajectory
            val path = Path().apply {
                moveTo(startX, groundY)
                quadraticTo(apexX, apexY, endX, groundY)
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            )

            // Launch vector arrow — direction from actual angle
            val arrowLen = 44.dp.toPx()
            drawArrow(
                color = Color(0xFF2E7D32),
                start = Offset(startX, groundY),
                end = Offset(startX + arrowLen * cos(angleRad), groundY - arrowLen * sin(angleRad)),
                strokeWidth = 2.5.dp.toPx()
            )

            // Max height dotted line
            if (projProgress.value > 0.1f) {
                drawLine(
                    color = Color(0xFF7B1FA2).copy(alpha = 0.5f),
                    start = Offset(apexX - 16.dp.toPx(), apexY),
                    end = Offset(apexX, apexY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // Range dimension line at bottom
            if (projProgress.value >= 0.98f) {
                drawDoubleArrow(
                    color = Color(0xFF1565C0).copy(alpha = 0.7f),
                    start = Offset(startX, groundY + 16.dp.toPx()),
                    end   = Offset(endX, groundY + 16.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Animated ball along Bezier parabola
            val t = projProgress.value
            val mt = 1f - t
            val ballX = mt * mt * startX + 2 * mt * t * apexX + t * t * endX
            val ballY = mt * mt * groundY + 2 * mt * t * apexY + t * t * groundY

            // Ball velocity arrow (tangent direction)
            if (t in 0.05f..0.95f) {
                val dtBallX = 2f * (mt * (apexX - startX) + t * (endX - apexX))
                val dtBallY = 2f * (mt * (apexY - groundY) + t * (groundY - apexY))
                val len     = kotlin.math.sqrt(dtBallX * dtBallX + dtBallY * dtBallY).coerceAtLeast(1f)
                val arrowScale = 24.dp.toPx()
                drawArrow(
                    color = Color(0xFF2E7D32).copy(alpha = 0.7f),
                    start = Offset(ballX, ballY),
                    end   = Offset(ballX + dtBallX / len * arrowScale, ballY + dtBallY / len * arrowScale),
                    strokeWidth = 2f.dp.toPx()
                )
            }

            drawCircle(color = Color(0xFFD32F2F), radius = 7.dp.toPx(), center = Offset(ballX, ballY))
            drawCircle(color = Color.White,        radius = 2.5.dp.toPx(), center = Offset(ballX, ballY))

            // Native text labels
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                tp.textSize = 26f; tp.color = 0xFF7B1FA2.toInt()
                if (projProgress.value > 0.05f) {
                    drawText("H = %.1fm".format(Locale.US, dynHMax), apexX - 26.dp.toPx(), apexY - 6.dp.toPx(), tp)
                }
                if (projProgress.value >= 0.98f) {
                    tp.textSize = 24f; tp.color = 0xFF1565C0.toInt()
                    drawText("R = %.1fm".format(Locale.US, dynRange), (startX + endX) / 2f, groundY + 28.dp.toPx(), tp)
                }
                tp.textSize = 24f; tp.color = 0xFF2E7D32.toInt()
                drawText("θ = %.0f°".format(Locale.US, currentAngle), startX + 32.dp.toPx(), groundY - 48.dp.toPx(), tp)
            }
        }

        // Primary Action Controls Row (Height 44.dp for generous touch target)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { toggleFlight() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(44.dp)
            ) {
                Text(
                    text = if (isFlying) "⏸ Pause" else if (projProgress.value >= 0.98f) "🔁 Re-Launch" else "▶ Launch",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { resetFlight() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("⏮ Reset", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = {
                    isLooping = !isLooping
                    if (isLooping && !isFlying) {
                        launchProjectile(restartFromZero = projProgress.value >= 0.98f)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isLooping) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent
                ),
                border = BorderStroke(
                    1.dp,
                    if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .height(44.dp)
            ) {
                Text(
                    if (isLooping) "🔁 Loop ON" else "🔁 Loop OFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Angle Comparison Selector (Directly supports comparing angles like 30° vs 60°)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Angle θ:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(30f to "30°", 45f to "45°", 60f to "60°", 75f to "75°").forEach { (deg, lbl) ->
                    val isSel = abs(currentAngle - deg) < 1.0f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = {
                                Log.d("EduPulseSim", "Angle chip clicked: $deg")
                                launchProjectile(newAngle = deg, restartFromZero = true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                Log.d("EduPulseSim", "Angle chip clicked: $deg")
                                launchProjectile(newAngle = deg, restartFromZero = true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Speed Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed v₀:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15f to "15 m/s", 20f to "20 m/s", 25f to "25 m/s", 35f to "35 m/s").forEach { (spd, lbl) ->
                    val isSel = abs(currentV0 - spd) < 1.0f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = {
                                Log.d("EduPulseSim", "Speed chip clicked: $spd")
                                launchProjectile(newV0 = spd, restartFromZero = true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                Log.d("EduPulseSim", "Speed chip clicked: $spd")
                                launchProjectile(newV0 = spd, restartFromZero = true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Trajectory Telemetry Badges (2 clean rows to eliminate cramped wrapping)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v₀ = %.1f m/s".format(Locale.US, currentV0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "θ = %.0f°".format(Locale.US, currentAngle),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "t = %.2f s".format(Locale.US, dynFlight),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "H_max = %.2f m".format(Locale.US, dynHMax),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B1FA2)
                    )
                    Text(
                        text = "Range = %.2f m".format(Locale.US, dynRange),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                }
            }
        }
    }
}


private fun DrawScope.drawArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = 4f,
    headLength: Float = 22f
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    val dx = end.x - start.x
    val dy = end.y - start.y
    val angle = kotlin.math.atan2(dy, dx)

    val arrowAngle = PI / 6.0 // 30 degrees
    val x1 = end.x - headLength * cos(angle - arrowAngle).toFloat()
    val y1 = end.y - headLength * sin(angle - arrowAngle).toFloat()
    val x2 = end.x - headLength * cos(angle + arrowAngle).toFloat()
    val y2 = end.y - headLength * sin(angle + arrowAngle).toFloat()

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawDoubleArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = 3f,
    headLength: Float = 16f
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth)

    // Left arrowhead (<)
    val leftPath = Path().apply {
        moveTo(start.x, start.y)
        lineTo(start.x + headLength, start.y - headLength * 0.5f)
        lineTo(start.x + headLength, start.y + headLength * 0.5f)
        close()
    }
    drawPath(path = leftPath, color = color)

    // Right arrowhead (>)
    val rightPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - headLength, end.y - headLength * 0.5f)
        lineTo(end.x - headLength, end.y + headLength * 0.5f)
        close()
    }
    drawPath(path = rightPath, color = color)
}

// ─────────────────────────────────────────────────────────────────────────────
// Mathematics: Interactive Function Grapher Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MathGraphCanvas(
    diagram: PhysicsDiagram.MathFunction,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    var rangeX by remember(diagram) { mutableFloatStateOf(6f) }
    var probeX by remember(diagram) { mutableFloatStateOf(0f) }

    fun f(x: Float): Float {
        return when (diagram.funcType) {
            "LINEAR" -> diagram.b * x + diagram.c
            "SIN" -> sin(x)
            else -> diagram.a * x * x + diagram.b * x + diagram.c
        }
    }

    val probeY = f(probeX)
    val probeSlope = when (diagram.funcType) {
        "LINEAR" -> diagram.b
        "SIN" -> cos(probeX)
        else -> 2f * diagram.a * probeX + diagram.b
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(rangeX) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cx = size.width / 2f
                            val scaleX = (size.width - 40f * density) / (2f * rangeX)
                            probeX = ((offset.x - cx) / scaleX).coerceIn(-rangeX, rangeX)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width / 2f
                            val scaleX = (size.width - 40f * density) / (2f * rangeX)
                            probeX = ((change.position.x - cx) / scaleX).coerceIn(-rangeX, rangeX)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val margin = 20.dp.toPx()
            val scaleX = (w - margin * 2) / (2f * rangeX)
            val rangeY = rangeX * (h / w)
            val scaleY = (h - margin * 2) / (2f * rangeY)

            // Grid lines
            val step = if (rangeX <= 6f) 1f else if (rangeX <= 12f) 2f else 5f
            var gx = -rangeX
            while (gx <= rangeX) {
                val px = cx + gx * scaleX
                drawLine(
                    color = outlineColor.copy(alpha = 0.15f),
                    start = Offset(px, 0f),
                    end = Offset(px, h),
                    strokeWidth = 1.dp.toPx()
                )
                gx += step
            }
            var gy = -rangeY
            while (gy <= rangeY) {
                val py = cy - gy * scaleY
                drawLine(
                    color = outlineColor.copy(alpha = 0.15f),
                    start = Offset(0f, py),
                    end = Offset(w, py),
                    strokeWidth = 1.dp.toPx()
                )
                gy += step
            }

            // Axes
            drawLine(outlineColor.copy(alpha = 0.6f), Offset(0f, cy), Offset(w, cy), 1.5.dp.toPx())
            drawLine(outlineColor.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, h), 1.5.dp.toPx())

            // Function Curve
            val path = Path()
            var first = true
            val numSamples = 120
            for (i in 0..numSamples) {
                val x = -rangeX + (2f * rangeX * i / numSamples)
                val y = f(x)
                val px = cx + x * scaleX
                val py = cy - y * scaleY
                if (first) { path.moveTo(px, py); first = false }
                else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Highlight Roots
            diagram.root1?.let { r ->
                if (abs(r) <= rangeX) {
                    val rpx = cx + r * scaleX
                    drawCircle(Color(0xFFD32F2F), 5.dp.toPx(), Offset(rpx, cy))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(rpx, cy))
                }
            }
            diagram.root2?.let { r ->
                if (abs(r) <= rangeX) {
                    val rpx = cx + r * scaleX
                    drawCircle(Color(0xFFD32F2F), 5.dp.toPx(), Offset(rpx, cy))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(rpx, cy))
                }
            }

            // Highlight Vertex
            if (diagram.funcType == "QUADRATIC" && abs(diagram.vertexX) <= rangeX && abs(diagram.vertexY) <= rangeY) {
                val vpx = cx + diagram.vertexX * scaleX
                val vpy = cy - diagram.vertexY * scaleY
                drawCircle(Color(0xFF7B1FA2), 6.dp.toPx(), Offset(vpx, vpy))
                drawCircle(Color.White, 2.5.dp.toPx(), Offset(vpx, vpy))
            }

            // Touch Probe
            val pPx = cx + probeX * scaleX
            val pPy = (cy - probeY * scaleY).coerceIn(0f, h)
            // Crosshairs
            drawLine(
                color = Color(0xFF1565C0).copy(alpha = 0.4f),
                start = Offset(pPx, 0f), end = Offset(pPx, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
            drawLine(
                color = Color(0xFF1565C0).copy(alpha = 0.4f),
                start = Offset(0f, pPy), end = Offset(w, pPy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
            drawCircle(Color(0xFF1565C0), 7.dp.toPx(), Offset(pPx, pPy))
            drawCircle(Color.White, 3.dp.toPx(), Offset(pPx, pPy))

            // Canvas Text (Coordinates)
            drawContext.canvas.nativeCanvas.apply {
                val p = Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = 0xFF1565C0.toInt()
                }
                drawText(
                    "(${String.format(Locale.US, "%.1f", probeX)}, ${String.format(Locale.US, "%.1f", probeY)})",
                    (pPx + 10.dp.toPx()).coerceAtMost(w - 90.dp.toPx()),
                    (pPy - 10.dp.toPx()).coerceAtLeast(20.dp.toPx()),
                    p
                )
            }
        }

        // Zoom / Range Row & Telemetry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4f to "±4", 8f to "±8", 15f to "±15").forEach { (r, label) ->
                    val isSel = rangeX == r
                    FilledTonalButton(
                        onClick = { rangeX = r },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Probe Jump Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (diagram.funcType == "QUADRATIC") {
                    OutlinedButton(
                        onClick = { probeX = diagram.vertexX },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Vertex", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    }
                    if (diagram.root1 != null) {
                        OutlinedButton(
                            onClick = { probeX = diagram.root1!! },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Root", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { probeX = 0f },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("x=0", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Probe: x=%.2f, y=%.2f".format(Locale.US, probeX, probeY),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Slope f'(x) = %.2f".format(Locale.US, probeSlope),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (probeSlope > 0f) Color(0xFF2E7D32) else if (probeSlope < 0f) Color(0xFFD32F2F) else Color(0xFF7B1FA2),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chemistry: Interactive Bohr Atomic Model Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChemistryAtomCanvas(
    diagram: PhysicsDiagram.ChemistryAtom,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    var isSpinning by remember(diagram) { mutableStateOf(true) }
    var spinSpeed by remember(diagram) { mutableFloatStateOf(1.0f) }
    val spinProgress = remember(diagram) { Animatable(0f) }

    LaunchedEffect(isSpinning, spinSpeed, diagram) {
        if (isSpinning) {
            while (isSpinning) {
                val duration = (6000 / spinSpeed).toInt().coerceAtLeast(500)
                spinProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(duration, easing = LinearEasing)
                )
                spinProgress.snapTo(0f)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(diagram) {
                    detectTapGestures {
                        isSpinning = !isSpinning
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rot = spinProgress.value * 2f * PI.toFloat()

            // Shell Radii
            val shellR = listOf(
                32.dp.toPx(),  // K shell
                54.dp.toPx(),  // L shell
                76.dp.toPx(),  // M shell
                96.dp.toPx()   // N shell
            )
            val shellCounts = listOf(diagram.kShell, diagram.lShell, diagram.mShell, diagram.nShell)

            // Draw Shells & Orbiting Electrons
            for (idx in 0 until 4) {
                val count = shellCounts[idx]
                if (count <= 0 && idx > 0) continue

                val r = shellR[idx]
                // Orbit circle
                drawCircle(
                    color = outlineColor.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )

                // Electrons in this shell
                val dir = if (idx % 2 == 0) 1f else -1f
                val speedMult = 1f + idx * 0.4f
                val shellAngle = rot * dir * speedMult
                for (e in 0 until count) {
                    val theta = shellAngle + (2f * PI.toFloat() * e / count)
                    val ex = cx + r * cos(theta)
                    val ey = cy + r * sin(theta)

                    // Electron glow + particle
                    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.4f), 5.dp.toPx(), Offset(ex, ey))
                    drawCircle(Color(0xFF00B0FF), 3.5.dp.toPx(), Offset(ex, ey))
                    drawCircle(Color.White, 1.2.dp.toPx(), Offset(ex, ey))
                }
            }

            // Central Nucleus
            val nucleusR = 18.dp.toPx()
            drawCircle(Color(0xFFD32F2F), nucleusR, Offset(cx, cy))
            drawCircle(Color(0xFFFF5252).copy(alpha = 0.6f), nucleusR * 0.7f, Offset(cx, cy))

            // Nucleus Text
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                }
                drawText(diagram.symbol, cx, cy + 9f, tp)
            }
        }

        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { isSpinning = !isSpinning },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (isSpinning) "⏸ Pause Orbits" else "▶ Spin Electrons",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.5f to "0.5x", 1.0f to "1.0x", 2.0f to "2.0x").forEach { (speed, label) ->
                    val isSelected = abs(spinSpeed - speed) < 0.1f
                    if (isSelected) {
                        FilledTonalButton(
                            onClick = { spinSpeed = speed },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { spinSpeed = speed },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chemistry: Interactive Acid-Base pH Scale Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChemistryPhCanvas(
    diagram: PhysicsDiagram.ChemistryPh,
    modifier: Modifier = Modifier
) {
    var phVal by remember(diagram) { mutableFloatStateOf(diagram.ph) }

    fun phColor(ph: Float): Color {
        return when {
            ph < 3f -> Color(0xFFD32F2F)        // Strong Acid (Red)
            ph < 6f -> Color(0xFFFF9800)        // Weak Acid (Orange)
            ph in 6f..7.5f -> Color(0xFF4CAF50) // Neutral (Green)
            ph in 7.5f..10f -> Color(0xFF00BCD4)// Weak Base (Teal/Cyan)
            else -> Color(0xFF673AB7)           // Strong Base (Purple)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val w = size.width
            val h = size.height

            // Solution Beaker Graphic on the left
            val beakerW = 50.dp.toPx()
            val beakerH = 65.dp.toPx()
            val beakerX = 24.dp.toPx()
            val beakerY = (h - beakerH) / 2f

            // Liquid fill
            val liquidH = beakerH * 0.7f
            drawRoundRect(
                color = phColor(phVal).copy(alpha = 0.75f),
                topLeft = Offset(beakerX, beakerY + beakerH - liquidH),
                size = Size(beakerW, liquidH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            // Glass outline
            drawRoundRect(
                color = Color.DarkGray,
                topLeft = Offset(beakerX, beakerY),
                size = Size(beakerW, beakerH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Dynamic pH Spectrum Bar on the right
            val barX = beakerX + beakerW + 24.dp.toPx()
            val barW = w - barX - 16.dp.toPx()
            val barH = 22.dp.toPx()
            val barY = h * 0.35f

            // Gradient strip segments (0 to 14)
            for (i in 0 until 14) {
                val segX = barX + (barW * i / 14f)
                val segW = barW / 14f
                drawRect(
                    color = phColor(i.toFloat()),
                    topLeft = Offset(segX, barY),
                    size = Size(segW + 1f, barH)
                )
            }

            // Current pH Marker Indicator
            val markerX = barX + (barW * (phVal / 14f)).coerceIn(0f, barW)
            drawCircle(Color.White, 8.dp.toPx(), Offset(markerX, barY + barH / 2f))
            drawCircle(phColor(phVal), 6.dp.toPx(), Offset(markerX, barY + barH / 2f))

            // Canvas Text
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                tp.textSize = 28f; tp.color = phColor(phVal).toArgb()
                drawText("pH = %.1f".format(Locale.US, phVal), markerX.coerceIn(barX + 24.dp.toPx(), barX + barW - 24.dp.toPx()), barY - 10.dp.toPx(), tp)

                tp.textSize = 20f; tp.color = android.graphics.Color.GRAY
                drawText("0 Acid", barX + 16.dp.toPx(), barY + barH + 18.dp.toPx(), tp)
                drawText("7 Neutral", barX + barW / 2f, barY + barH + 18.dp.toPx(), tp)
                drawText("14 Base", barX + barW - 16.dp.toPx(), barY + barH + 18.dp.toPx(), tp)
            }
        }

        // Interactive Slider for Testing Different Solutions
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
            Slider(
                value = phVal,
                onValueChange = { phVal = it },
                valueRange = 0f..14f,
                modifier = Modifier.weight(1f).height(38.dp),
                colors = SliderDefaults.colors(thumbColor = phColor(phVal), activeTrackColor = phColor(phVal))
            )
            Text("14", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF673AB7))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Solution Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Lemon (2.2)" to 2.2f, "Coffee (5.0)" to 5.0f, "Water (7.0)" to 7.0f, "Soap (10.0)" to 10.0f, "Bleach (12.5)" to 12.5f).forEach { (name, p) ->
                val isSel = abs(phVal - p) < 0.3f
                if (isSel) {
                    FilledTonalButton(
                        onClick = { phVal = p },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(name.substringBefore(" "), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { phVal = p },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(name.substringBefore(" "), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Biology: Interactive Punnett Square Genetics Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BiologyGeneticsCanvas(
    diagram: PhysicsDiagram.BiologyGenetics,
    modifier: Modifier = Modifier
) {
    var selectedQuadrant by remember(diagram) { mutableIntStateOf(0) }

    val p1Alleles = if (diagram.p1.length >= 2) listOf(diagram.p1[0].toString(), diagram.p1[1].toString()) else listOf("B", "b")
    val p2Alleles = if (diagram.p2.length >= 2) listOf(diagram.p2[0].toString(), diagram.p2[1].toString()) else listOf("B", "b")

    val combinations = listOf(
        p2Alleles[0] + p1Alleles[0], // Top-Left
        p2Alleles[0] + p1Alleles[1], // Top-Right
        p2Alleles[1] + p1Alleles[0], // Bottom-Left
        p2Alleles[1] + p1Alleles[1]  // Bottom-Right
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(diagram) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val gridSize = 120.dp.toPx()
                        val left = cx - gridSize / 2f
                        val top = cy - gridSize / 2f
                        val cellSize = gridSize / 2f

                        if (offset.x in left..(left + gridSize) && offset.y in top..(top + gridSize)) {
                            val col = if (offset.x < left + cellSize) 0 else 1
                            val row = if (offset.y < top + cellSize) 0 else 1
                            selectedQuadrant = row * 2 + col
                        }
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val gridSize = 120.dp.toPx()
            val left = cx - gridSize / 2f
            val top = cy - gridSize / 2f
            val cellSize = gridSize / 2f

            // Parent 1 Labels across top
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 28f; color = 0xFF1565C0.toInt()
                }
                drawText(p1Alleles[0], left + cellSize / 2f, top - 10.dp.toPx(), tp)
                drawText(p1Alleles[1], left + cellSize * 1.5f, top - 10.dp.toPx(), tp)

                // Parent 2 Labels along left
                tp.color = 0xFF7B1FA2.toInt()
                drawText(p2Alleles[0], left - 18.dp.toPx(), top + cellSize / 2f + 9f, tp)
                drawText(p2Alleles[1], left - 18.dp.toPx(), top + cellSize * 1.5f + 9f, tp)
            }

            // 4 Quadrants
            for (row in 0..1) {
                for (col in 0..1) {
                    val idx = row * 2 + col
                    val isSel = idx == selectedQuadrant
                    val cellX = left + col * cellSize
                    val cellY = top + row * cellSize

                    drawRoundRect(
                        color = if (isSel) Color(0xFFE3F2FD) else Color(0xFFFAFAFA),
                        topLeft = Offset(cellX, cellY),
                        size = Size(cellSize, cellSize),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (isSel) Color(0xFF1976D2) else Color.LightGray,
                        topLeft = Offset(cellX, cellY),
                        size = Size(cellSize, cellSize),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = if (isSel) 2.5.dp.toPx() else 1.2.dp.toPx())
                    )

                    // Combination Text inside cell
                    drawContext.canvas.nativeCanvas.apply {
                        val tp = Paint().apply {
                            isAntiAlias = true; textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textSize = 30f
                            color = if (isSel) 0xFF0D47A1.toInt() else 0xFF263238.toInt()
                        }
                        drawText(combinations[idx], cellX + cellSize / 2f, cellY + cellSize / 2f + 10f, tp)
                    }
                }
            }
        }

        // 4 Quadrant Selector Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            combinations.forEachIndexed { idx, combo ->
                val isSel = idx == selectedQuadrant
                if (isSel) {
                    FilledTonalButton(
                        onClick = { selectedQuadrant = idx },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(combo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedQuadrant = idx },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(combo, fontSize = 13.sp)
                    }
                }
            }
        }

        // Inspection banner for selected quadrant
        val selGenotype = combinations[selectedQuadrant]
        val hasDominant = selGenotype.any { it.isUpperCase() }
        val selPhenotype = if (hasDominant) diagram.dominantTrait else diagram.recessiveTrait

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: $selGenotype → $selPhenotype",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasDominant) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
                Text(
                    text = "Chance: 25% (1/4)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Physics: Interactive Electric Circuit & Ohm's Law Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ElectricCircuitCanvas(
    diagram: PhysicsDiagram.ElectricCircuit,
    modifier: Modifier = Modifier
) {
    var vVal by remember(diagram) { mutableFloatStateOf(diagram.voltage) }
    var rVal by remember(diagram) { mutableFloatStateOf(diagram.resistance) }
    var isSwitchClosed by remember(diagram) { mutableStateOf(true) }

    val current = if (isSwitchClosed && rVal > 0f) vVal / rVal else 0f
    val power = if (isSwitchClosed) vVal * current else 0f

    val electronAnim = remember(diagram) { Animatable(0f) }

    LaunchedEffect(isSwitchClosed, current) {
        if (isSwitchClosed && current > 0.01f) {
            val speedMs = (3000f / current.coerceIn(0.5f, 10f)).toInt().coerceIn(400, 4000)
            while (isSwitchClosed && current > 0.01f) {
                electronAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = speedMs, easing = LinearEasing)
                )
                electronAnim.snapTo(0f)
            }
        } else {
            electronAnim.snapTo(0f)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        isSwitchClosed = !isSwitchClosed
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val padX = 40.dp.toPx()
            val padY = 25.dp.toPx()
            val left = padX
            val right = w - padX
            val top = padY
            val bottom = h - padY

            val wireColor = Color(0xFF607D8B)
            val strokeW = 3.dp.toPx()

            // Top wire (split for light bulb)
            val bulbCx = (left + right) / 2f
            val bulbR = 18.dp.toPx()
            drawLine(wireColor, Offset(left, top), Offset(bulbCx - bulbR - 4f, top), strokeW, StrokeCap.Round)
            drawLine(wireColor, Offset(bulbCx + bulbR + 4f, top), Offset(right, top), strokeW, StrokeCap.Round)

            // Right wire (split for resistor)
            val resCy = (top + bottom) / 2f
            val resH = 24.dp.toPx()
            drawLine(wireColor, Offset(right, top), Offset(right, resCy - resH), strokeW, StrokeCap.Round)
            drawLine(wireColor, Offset(right, resCy + resH), Offset(right, bottom), strokeW, StrokeCap.Round)

            // Bottom wire (split for switch)
            val switchCx = (left + right) / 2f
            val switchGap = 20.dp.toPx()
            drawLine(wireColor, Offset(left, bottom), Offset(switchCx - switchGap, bottom), strokeW, StrokeCap.Round)
            drawLine(wireColor, Offset(switchCx + switchGap, bottom), Offset(right, bottom), strokeW, StrokeCap.Round)

            // Left wire (split for DC battery)
            val batCy = (top + bottom) / 2f
            val batGap = 16.dp.toPx()
            drawLine(wireColor, Offset(left, top), Offset(left, batCy - batGap), strokeW, StrokeCap.Round)
            drawLine(wireColor, Offset(left, batCy + batGap), Offset(left, bottom), strokeW, StrokeCap.Round)

            // Battery Source (Left)
            drawLine(Color(0xFFD32F2F), Offset(left - 16.dp.toPx(), batCy - 6.dp.toPx()), Offset(left + 16.dp.toPx(), batCy - 6.dp.toPx()), 3.5.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF1976D2), Offset(left - 9.dp.toPx(), batCy + 6.dp.toPx()), Offset(left + 9.dp.toPx(), batCy + 6.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)

            // Resistor (Right)
            val resW = 20.dp.toPx()
            val resBoxTop = resCy - resH
            drawRoundRect(
                color = Color(0xFFEF6C00),
                topLeft = Offset(right - resW / 2f, resBoxTop),
                size = Size(resW, resH * 2f),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(right - resW / 2f, resBoxTop),
                size = Size(resW, resH * 2f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Light Bulb / Lamp (Top)
            val glowFraction = (power / 60f).coerceIn(0f, 1f)
            if (isSwitchClosed && power > 0.1f) {
                drawCircle(
                    color = Color(0xFFFFEB3B).copy(alpha = 0.25f + 0.5f * glowFraction),
                    radius = bulbR + 12.dp.toPx() * glowFraction,
                    center = Offset(bulbCx, top)
                )
            }
            drawCircle(
                color = if (isSwitchClosed && power > 0.1f) Color(0xFFFFD600) else Color(0xFFCFD8DC),
                radius = bulbR,
                center = Offset(bulbCx, top)
            )
            drawCircle(
                color = Color(0xFF455A64),
                radius = bulbR,
                center = Offset(bulbCx, top),
                style = Stroke(width = 2.dp.toPx())
            )
            val filColor = if (isSwitchClosed && power > 0.1f) Color(0xFFFF5722) else Color(0xFF78909C)
            drawLine(filColor, Offset(bulbCx - 6.dp.toPx(), top + 4.dp.toPx()), Offset(bulbCx, top - 6.dp.toPx()), 2.dp.toPx())
            drawLine(filColor, Offset(bulbCx, top - 6.dp.toPx()), Offset(bulbCx + 6.dp.toPx(), top + 4.dp.toPx()), 2.dp.toPx())

            // Switch (Bottom)
            drawCircle(Color(0xFF37474F), 3.5.dp.toPx(), Offset(switchCx - switchGap, bottom))
            drawCircle(Color(0xFF37474F), 3.5.dp.toPx(), Offset(switchCx + switchGap, bottom))
            if (isSwitchClosed) {
                drawLine(Color(0xFF2E7D32), Offset(switchCx - switchGap, bottom), Offset(switchCx + switchGap, bottom), 3.dp.toPx(), StrokeCap.Round)
            } else {
                drawLine(Color(0xFFD32F2F), Offset(switchCx - switchGap, bottom), Offset(switchCx + switchGap - 4.dp.toPx(), bottom - 14.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            }

            // Flowing Electrons
            if (isSwitchClosed && current > 0.01f) {
                val perimeter = 2f * (right - left) + 2f * (bottom - top)
                val numElectrons = 14
                val t = electronAnim.value
                for (i in 0 until numElectrons) {
                    val pos = ((t + i.toFloat() / numElectrons) % 1f) * perimeter
                    val (ex, ey) = when {
                        pos < (right - left) -> (left + pos) to top
                        pos < (right - left) + (bottom - top) -> right to (top + (pos - (right - left)))
                        pos < 2f * (right - left) + (bottom - top) -> (right - (pos - ((right - left) + (bottom - top)))) to bottom
                        else -> left to (bottom - (pos - (2f * (right - left) + (bottom - top))))
                    }
                    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.5f), 4.5.dp.toPx(), Offset(ex, ey))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(ex, ey))
                }
            }

            // Labels on Canvas
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }

                tp.color = 0xFFD32F2F.toInt()
                drawText("+", left + 22.dp.toPx(), batCy - 8.dp.toPx(), tp)
                tp.color = 0xFF1976D2.toInt()
                drawText("-", left + 22.dp.toPx(), batCy + 14.dp.toPx(), tp)
                drawText("%.0fV".format(vVal), left - 24.dp.toPx(), batCy + 6.dp.toPx(), tp)

                tp.color = 0xFFEF6C00.toInt()
                drawText("%.0fΩ".format(rVal), right + 24.dp.toPx(), resCy + 6.dp.toPx(), tp)

                tp.color = 0xFFE65100.toInt()
                drawText(if (isSwitchClosed) "%.1fW".format(power) else "OFF", bulbCx, top - bulbR - 6.dp.toPx(), tp)

                tp.color = if (isSwitchClosed) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt()
                drawText(if (isSwitchClosed) "CLOSED" else "OPEN", switchCx, bottom + 18.dp.toPx(), tp)
            }
        }

        // Switch Button & Voltage presets
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { isSwitchClosed = !isSwitchClosed },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isSwitchClosed) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (isSwitchClosed) "⚡ Switch: CLOSED" else "⭕ Switch: OPEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(3f to "3V", 6f to "6V", 12f to "12V", 24f to "24V").forEach { (v, lbl) ->
                    val isSel = abs(vVal - v) < 0.2f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = { vVal = v },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { vVal = v },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Resistance presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Load Resistance:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(2f to "2Ω", 4f to "4Ω", 8f to "8Ω", 12f to "12Ω").forEach { (r, lbl) ->
                    val isSel = abs(rVal - r) < 0.2f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = { rVal = r },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { rVal = r },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Ohm's Law HUD Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current I = V/R = %.2f A".format(Locale.US, current),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "Power P = V×I = %.1f W".format(Locale.US, power),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Physics: Interactive Simple Harmonic Pendulum Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HarmonicPendulumCanvas(
    diagram: PhysicsDiagram.HarmonicPendulum,
    modifier: Modifier = Modifier
) {
    var lengthVal by remember(diagram) { mutableFloatStateOf(diagram.length) }
    var gravityVal by remember(diagram) { mutableFloatStateOf(diagram.gravity) }
    var isPlaying by remember(diagram) { mutableStateOf(true) }

    val period = (2f * PI.toFloat() * sqrt(lengthVal / gravityVal)).coerceAtLeast(0.2f)
    val frequency = 1f / period

    val animTime = remember(diagram) { Animatable(0f) }

    LaunchedEffect(isPlaying, period) {
        if (isPlaying) {
            val periodMs = (period * 1000f).toInt().coerceIn(300, 10000)
            while (isPlaying) {
                animTime.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = periodMs, easing = LinearEasing)
                )
                animTime.snapTo(0f)
            }
        }
    }

    val thetaMax = 0.45f
    val theta = thetaMax * sin(2f * PI.toFloat() * animTime.value)
    val kineticFrac = (cos(2f * PI.toFloat() * animTime.value) * cos(2f * PI.toFloat() * animTime.value)).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(Unit) {
                    detectTapGestures { isPlaying = !isPlaying }
                }
        ) {
            val cx = size.width / 2f
            val pivotY = 16.dp.toPx()
            val maxCanvasL = 120.dp.toPx()
            val currentL = (maxCanvasL * (lengthVal / 2.0f).coerceIn(0.4f, 1.1f))

            val bobX = cx + currentL * sin(theta)
            val bobY = pivotY + currentL * cos(theta)
            val bobRadius = 14.dp.toPx()

            // Ceiling Mount Bracket
            drawRect(Color(0xFF455A64), Offset(cx - 30.dp.toPx(), 4.dp.toPx()), Size(60.dp.toPx(), 8.dp.toPx()))
            drawCircle(Color(0xFF263238), 4.dp.toPx(), Offset(cx, pivotY))

            // Dashed Equilibrium Line
            drawLine(
                color = Color.Gray.copy(alpha = 0.35f),
                start = Offset(cx, pivotY),
                end = Offset(cx, pivotY + maxCanvasL + 10.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            // Dashed Swing Path Arc
            val arcPath = Path().apply {
                val startArcX = cx + currentL * sin(-thetaMax)
                val startArcY = pivotY + currentL * cos(-thetaMax)
                val endArcX = cx + currentL * sin(thetaMax)
                val endArcY = pivotY + currentL * cos(thetaMax)
                moveTo(startArcX, startArcY)
                quadraticTo(cx, pivotY + currentL, endArcX, endArcY)
            }
            drawPath(
                path = arcPath,
                color = Color(0xFF1976D2).copy(alpha = 0.25f),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )

            // Cord String
            drawLine(Color(0xFF37474F), Offset(cx, pivotY), Offset(bobX, bobY), 2.5.dp.toPx(), StrokeCap.Round)

            // Pendulum Bob with Specular Highlight
            drawCircle(Color(0xFF1565C0), bobRadius, Offset(bobX, bobY))
            drawCircle(Color(0xFF42A5F5), bobRadius * 0.7f, Offset(bobX - 3.dp.toPx(), bobY - 3.dp.toPx()))
            drawCircle(Color.White, bobRadius * 0.25f, Offset(bobX - 4.dp.toPx(), bobY - 4.dp.toPx()))

            // Velocity Vector on Bob
            val vTangential = -sin(2f * PI.toFloat() * animTime.value)
            if (abs(vTangential) > 0.05f) {
                val arrowScale = 28.dp.toPx() * vTangential
                val tangentAngle = theta + (PI / 2.0).toFloat()
                val vx = arrowScale * cos(tangentAngle)
                val vy = arrowScale * sin(tangentAngle)
                drawArrow(
                    color = Color(0xFF2E7D32),
                    start = Offset(bobX, bobY),
                    end = Offset(bobX + vx, bobY + vy),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // Canvas Text
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    color = 0xFF1565C0.toInt()
                }
                drawText("L = %.2fm".format(lengthVal), cx - 45.dp.toPx(), pivotY + currentL / 2f, tp)
                tp.color = 0xFF2E7D32.toInt()
                drawText("T = %.2fs".format(period), cx + 45.dp.toPx(), pivotY + currentL / 2f, tp)
            }
        }

        // Energy Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡ Kinetic Energy (v²)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            Text("Potential Energy (mgh) 🏔️", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { kineticFrac },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Color(0xFF2E7D32),
            trackColor = Color(0xFF7B1FA2).copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Play/Pause & Length presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { isPlaying = !isPlaying },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸ Pause" else "▶ Swing",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.5f to "0.5m", 1.0f to "1.0m", 1.5f to "1.5m", 2.0f to "2.0m").forEach { (l, lbl) ->
                    val isSel = abs(lengthVal - l) < 0.1f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = { lengthVal = l },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { lengthVal = l },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Gravity Presets (Moon, Earth, Jupiter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gravity Field:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1.62f to "Moon", 9.8f to "Earth", 24.8f to "Jupiter").forEach { (g, lbl) ->
                    val isSel = abs(gravityVal - g) < 0.2f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = { gravityVal = g },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { gravityVal = g },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Physics: Interactive Wave Motion Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaveMotionCanvas(
    diagram: PhysicsDiagram.WaveMotion,
    modifier: Modifier = Modifier
) {
    var freqVal by remember(diagram) { mutableFloatStateOf(diagram.frequency) }
    var speedVal by remember(diagram) { mutableFloatStateOf(diagram.waveSpeed) }
    var isPlaying by remember(diagram) { mutableStateOf(true) }

    val wavelength = if (freqVal > 0f) speedVal / freqVal else 1.0f
    val waveAnim = remember(diagram) { Animatable(0f) }

    LaunchedEffect(isPlaying, freqVal) {
        if (isPlaying) {
            val periodMs = ((1f / freqVal.coerceIn(50f, 1000f)) * 10000f).toInt().coerceIn(400, 3000)
            while (isPlaying) {
                waveAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = periodMs, easing = LinearEasing)
                )
                waveAnim.snapTo(0f)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(Unit) {
                    detectTapGestures { isPlaying = !isPlaying }
                }
        ) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val amplitudePx = 42.dp.toPx()
            val phase = waveAnim.value * 2f * PI.toFloat()

            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(0f, cy),
                end = Offset(w, cy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )

            val path = Path()
            val numPoints = 120
            val spatialFreq = (2f * PI.toFloat() / (w * 0.45f))

            for (i in 0..numPoints) {
                val x = w * (i.toFloat() / numPoints)
                val y = cy - amplitudePx * sin(spatialFreq * x - phase)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color(0xFF1976D2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            for (k in 0..2) {
                val crestX = ((phase / (2f * PI.toFloat()) + k) * (w * 0.45f)) % w
                if (crestX in 10f..(w - 10f)) {
                    val crestY = cy - amplitudePx
                    drawCircle(Color(0xFF2E7D32), 6.dp.toPx(), Offset(crestX, crestY))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(crestX, crestY))
                }
            }

            val ampX = 35.dp.toPx()
            drawLine(Color(0xFF7B1FA2), Offset(ampX, cy), Offset(ampX, cy - amplitudePx), 1.8.dp.toPx())
            drawCircle(Color(0xFF7B1FA2), 3.dp.toPx(), Offset(ampX, cy - amplitudePx))

            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = 0xFF7B1FA2.toInt()
                }
                drawText("A = %.1fm".format(diagram.amplitude), ampX + 8.dp.toPx(), cy - amplitudePx / 2f + 6f, tp)

                tp.color = 0xFF1976D2.toInt()
                drawText("λ = %.2fm".format(wavelength), w / 2f, cy + amplitudePx + 18.dp.toPx(), tp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { isPlaying = !isPlaying },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸ Pause Wave" else "▶ Oscillate",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(85f to "85 Hz", 170f to "170 Hz", 340f to "340 Hz").forEach { (f, lbl) ->
                    val isSel = abs(freqVal - f) < 5f
                    if (isSel) {
                        FilledTonalButton(
                            onClick = { freqVal = f },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { freqVal = f },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v = f × λ = %.0f m/s".format(Locale.US, speedVal),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "Period T = 1/f = %.3fs".format(Locale.US, if (freqVal > 0f) 1f / freqVal else 0f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chemistry: Interactive Reaction & Stoichiometry Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChemistryReactionCanvas(
    diagram: PhysicsDiagram.ChemistryReaction,
    modifier: Modifier = Modifier
) {
    var reactionPhase by remember(diagram) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            val cy = h / 2f

            val leftCx = w * 0.25f
            val rightCx = w * 0.75f
            val centerCx = w * 0.50f

            val chamberW = w * 0.38f
            val chamberH = 110.dp.toPx()
            val chamberTop = (h - chamberH) / 2f

            drawRoundRect(
                color = if (reactionPhase == 0) Color(0xFFE3F2FD) else Color(0xFFFAFAFA),
                topLeft = Offset(w * 0.06f, chamberTop),
                size = Size(chamberW, chamberH),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            drawRoundRect(
                color = if (reactionPhase == 0) Color(0xFF1976D2) else Color.LightGray,
                topLeft = Offset(w * 0.06f, chamberTop),
                size = Size(chamberW, chamberH),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = if (reactionPhase == 0) 2.5.dp.toPx() else 1.2.dp.toPx())
            )

            drawRoundRect(
                color = if (reactionPhase == 2) Color(0xFFE8F5E9) else Color(0xFFFAFAFA),
                topLeft = Offset(w * 0.56f, chamberTop),
                size = Size(chamberW, chamberH),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            drawRoundRect(
                color = if (reactionPhase == 2) Color(0xFF2E7D32) else Color.LightGray,
                topLeft = Offset(w * 0.56f, chamberTop),
                size = Size(chamberW, chamberH),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = if (reactionPhase == 2) 2.5.dp.toPx() else 1.2.dp.toPx())
            )

            val arrowColor = if (reactionPhase == 1) Color(0xFFFF9800) else Color.DarkGray
            drawArrow(
                color = arrowColor,
                start = Offset(centerCx - 18.dp.toPx(), cy),
                end = Offset(centerCx + 18.dp.toPx(), cy),
                strokeWidth = 3.dp.toPx()
            )

            if (reactionPhase == 1) {
                drawCircle(Color(0xFFFF9800).copy(alpha = 0.35f), 18.dp.toPx(), Offset(centerCx, cy))
                drawCircle(Color(0xFFFFD54F), 8.dp.toPx(), Offset(centerCx, cy))
            }

            drawCircle(Color(0xFF1976D2), 16.dp.toPx(), Offset(leftCx - 16.dp.toPx(), cy - 10.dp.toPx()))
            drawCircle(Color(0xFFD32F2F), 18.dp.toPx(), Offset(leftCx + 16.dp.toPx(), cy + 8.dp.toPx()))

            drawCircle(Color(0xFF2E7D32), 20.dp.toPx(), Offset(rightCx, cy))
            drawCircle(Color(0xFF81C784), 10.dp.toPx(), Offset(rightCx - 12.dp.toPx(), cy - 12.dp.toPx()))
            drawCircle(Color(0xFF81C784), 10.dp.toPx(), Offset(rightCx + 12.dp.toPx(), cy - 12.dp.toPx()))

            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }

                tp.color = 0xFF1976D2.toInt()
                drawText("REACTANTS", leftCx, chamberTop + 24.dp.toPx(), tp)
                tp.textSize = 22f
                drawText(diagram.reactants.joinToString(" + "), leftCx, chamberTop + chamberH - 12.dp.toPx(), tp)

                tp.textSize = 24f
                tp.color = 0xFF2E7D32.toInt()
                drawText("PRODUCTS", rightCx, chamberTop + 24.dp.toPx(), tp)
                tp.textSize = 22f
                drawText(diagram.products.joinToString(" + "), rightCx, chamberTop + chamberH - 12.dp.toPx(), tp)

                tp.textSize = 20f
                tp.color = 0xFFEF6C00.toInt()
                drawText(if (reactionPhase == 1) "ACTIVATION" else "→", centerCx, cy - 16.dp.toPx(), tp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1️⃣ Reactants", "⚡ Transition", "2️⃣ Products").forEachIndexed { idx, lbl ->
                val isSel = reactionPhase == idx
                FilledTonalButton(
                    onClick = { reactionPhase = idx },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text(
                        lbl,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diagram.equation,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "Type: ${diagram.reactionType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Biology: Interactive Cell Structure & Organelles Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BiologyCellCanvas(
    diagram: PhysicsDiagram.BiologyCell,
    modifier: Modifier = Modifier
) {
    var cellType by remember(diagram) { mutableStateOf(diagram.cellType) }
    var selectedOrganelle by remember(diagram) { mutableStateOf("Nucleus") }

    val isPlant = cellType.contains("Plant", true)

    val organelleInfo = mapOf(
        "Nucleus" to "🧠 Nucleus: Contains cell's genetic code (DNA); commands protein synthesis and division.",
        "Mitochondria" to "⚡ Mitochondria: Generates cellular energy (ATP) through aerobic cellular respiration.",
        "Chloroplast" to "☀️ Chloroplast: Conducts photosynthesis using chlorophyll to produce glucose from light.",
        "Vacuole" to "💧 Vacuole: Stores water, nutrients, and maintains cellular turgor pressure.",
        "Cell Wall" to "🛡️ Cell Wall: Rigid cellulose layer providing mechanical structural support and protection.",
        "Cell Membrane" to "🚪 Cell Membrane: Semi-permeable lipid bilayer regulating molecular transport."
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(cellType) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        selectedOrganelle = when {
                            dx * dx + dy * dy < (28.dp.toPx() * 28.dp.toPx()) -> "Nucleus"
                            dy < -25.dp.toPx() -> if (isPlant) "Chloroplast" else "Mitochondria"
                            dy > 25.dp.toPx() -> if (isPlant) "Vacuole" else "Cell Membrane"
                            dx > 30.dp.toPx() -> "Mitochondria"
                            else -> if (isPlant) "Cell Wall" else "Cell Membrane"
                        }
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cellW = size.width * 0.78f
            val cellH = 135.dp.toPx()
            val left = cx - cellW / 2f
            val top = cy - cellH / 2f

            if (isPlant) {
                drawRoundRect(
                    color = Color(0xFF2E7D32),
                    topLeft = Offset(left, top),
                    size = Size(cellW, cellH),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 5.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFE8F5E9),
                    topLeft = Offset(left + 6.dp.toPx(), top + 6.dp.toPx()),
                    size = Size(cellW - 12.dp.toPx(), cellH - 12.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF81C784),
                    topLeft = Offset(left + 6.dp.toPx(), top + 6.dp.toPx()),
                    size = Size(cellW - 12.dp.toPx(), cellH - 12.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx())
                )

                val vacW = cellW * 0.38f
                val vacH = cellH * 0.55f
                val isVacSel = selectedOrganelle == "Vacuole"
                drawRoundRect(
                    color = if (isVacSel) Color(0xFF81D4FA) else Color(0xFFB3E5FC),
                    topLeft = Offset(cx - vacW / 2f + 35.dp.toPx(), cy - vacH / 2f),
                    size = Size(vacW, vacH),
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
                drawRoundRect(
                    color = if (isVacSel) Color(0xFF0288D1) else Color(0xFF4FC3F7),
                    topLeft = Offset(cx - vacW / 2f + 35.dp.toPx(), cy - vacH / 2f),
                    size = Size(vacW, vacH),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = if (isVacSel) 2.5.dp.toPx() else 1.2.dp.toPx())
                )

                val isChlSel = selectedOrganelle == "Chloroplast"
                listOf(Offset(left + 35.dp.toPx(), top + 30.dp.toPx()), Offset(left + 45.dp.toPx(), top + cellH - 35.dp.toPx())).forEach { pt ->
                    drawOval(if (isChlSel) Color(0xFF1B5E20) else Color(0xFF43A047), pt - Offset(14.dp.toPx(), 9.dp.toPx()), Size(28.dp.toPx(), 18.dp.toPx()))
                    if (isChlSel) {
                        drawOval(Color.White, pt - Offset(14.dp.toPx(), 9.dp.toPx()), Size(28.dp.toPx(), 18.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                }
            } else {
                val isMemSel = selectedOrganelle == "Cell Membrane"
                drawRoundRect(
                    color = Color(0xFFFFF3E0),
                    topLeft = Offset(left, top),
                    size = Size(cellW, cellH),
                    cornerRadius = CornerRadius(36.dp.toPx())
                )
                drawRoundRect(
                    color = if (isMemSel) Color(0xFFE65100) else Color(0xFFFFB74D),
                    topLeft = Offset(left, top),
                    size = Size(cellW, cellH),
                    cornerRadius = CornerRadius(36.dp.toPx()),
                    style = Stroke(width = if (isMemSel) 3.5.dp.toPx() else 2.dp.toPx())
                )
            }

            val nucX = cx - (if (isPlant) 45.dp.toPx() else 0f)
            val nucR = 24.dp.toPx()
            val isNucSel = selectedOrganelle == "Nucleus"
            drawCircle(if (isNucSel) Color(0xFF6A1B9A) else Color(0xFF7B1FA2), nucR, Offset(nucX, cy))
            drawCircle(Color(0xFFBA68C8), nucR * 0.7f, Offset(nucX, cy))
            drawCircle(Color(0xFF4A148C), nucR * 0.35f, Offset(nucX, cy))
            if (isNucSel) {
                drawCircle(Color.White, nucR + 4.dp.toPx(), Offset(nucX, cy), style = Stroke(2.dp.toPx()))
            }

            val mitoX = cx + (if (isPlant) 70.dp.toPx() else 45.dp.toPx())
            val mitoY = cy - 25.dp.toPx()
            val isMitoSel = selectedOrganelle == "Mitochondria"
            drawOval(Color(0xFFE65100), Offset(mitoX - 16.dp.toPx(), mitoY - 10.dp.toPx()), Size(32.dp.toPx(), 20.dp.toPx()))
            if (isMitoSel) {
                drawOval(Color.White, Offset(mitoX - 16.dp.toPx(), mitoY - 10.dp.toPx()), Size(32.dp.toPx(), 20.dp.toPx()), style = Stroke(2.dp.toPx()))
            }

            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    color = android.graphics.Color.WHITE
                }
                drawText("Nucleus", nucX, cy + 6f, tp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { cellType = if (isPlant) "Animal Cell" else "Plant Cell" },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (isPlant) "Switch to: 🐾 Animal Cell" else "Switch to: 🌱 Plant Cell",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = cellType,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPlant) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val chips = if (isPlant) {
                listOf("Nucleus", "Mitochondria", "Chloroplast", "Vacuole", "Cell Wall")
            } else {
                listOf("Nucleus", "Mitochondria", "Cell Membrane", "Vacuole")
            }
            chips.forEach { org ->
                val isSel = selectedOrganelle == org
                if (isSel) {
                    FilledTonalButton(
                        onClick = { selectedOrganelle = org },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(org, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedOrganelle = org },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(org, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = organelleInfo[selectedOrganelle] ?: "Tap any organelle to view its biological role.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Biology: Interactive Ecosystem Energy Pyramid Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcosystemPyramidCanvas(
    diagram: PhysicsDiagram.EcosystemPyramid,
    modifier: Modifier = Modifier
) {
    var selectedTier by remember(diagram) { mutableIntStateOf(0) }
    var primaryEnergy by remember(diagram) { mutableFloatStateOf(diagram.primaryEnergy) }

    val tierData = listOf(
        Triple("🌿 Primary Producers", primaryEnergy, "Plants, Phytoplankton, Trees (Capture solar energy via photosynthesis)"),
        Triple("🐇 Primary Consumers", primaryEnergy * 0.10f, "Herbivores (Grasshoppers, Rabbits, Zooplankton)"),
        Triple("🦊 Secondary Consumers", primaryEnergy * 0.01f, "Carnivores / Omnivores (Frogs, Small birds, Fish)"),
        Triple("🦅 Apex Predators", primaryEnergy * 0.001f, "Tertiary Carnivores (Hawks, Lions, Sharks)")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val h = size.height
                        val tierH = (h - 20.dp.toPx()) / 4f
                        val tier = 3 - ((offset.y - 10.dp.toPx()) / tierH).toInt().coerceIn(0, 3)
                        selectedTier = tier
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val topY = 12.dp.toPx()
            val totalH = h - 24.dp.toPx()
            val tierH = totalH / 4f

            val tierColors = listOf(
                Color(0xFF2E7D32),
                Color(0xFF8BC34A),
                Color(0xFFFFA000),
                Color(0xFFD32F2F)
            )

            for (level in 3 downTo 0) {
                val isSel = selectedTier == level
                val y1 = topY + (3 - level) * tierH
                val y2 = y1 + tierH

                val w1 = w * 0.16f + (w * 0.60f) * ((3 - level) / 4f)
                val w2 = w * 0.16f + (w * 0.60f) * ((4 - level) / 4f)

                val trapPath = Path().apply {
                    moveTo(cx - w1 / 2f, y1)
                    lineTo(cx + w1 / 2f, y1)
                    lineTo(cx + w2 / 2f, y2)
                    lineTo(cx - w2 / 2f, y2)
                    close()
                }

                drawPath(path = trapPath, color = tierColors[level].copy(alpha = if (isSel) 0.95f else 0.70f))
                drawPath(
                    path = trapPath,
                    color = if (isSel) Color.White else Color.Black.copy(alpha = 0.25f),
                    style = Stroke(width = if (isSel) 2.5.dp.toPx() else 1.dp.toPx())
                )

                if (level < 3) {
                    val heatY = y1
                    val arrowStartX = cx + w1 / 2f + 6.dp.toPx()
                    drawArrow(
                        color = Color(0xFFD32F2F).copy(alpha = 0.7f),
                        start = Offset(arrowStartX, heatY),
                        end = Offset(arrowStartX + 22.dp.toPx(), heatY - 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    color = android.graphics.Color.WHITE
                }

                for (level in 3 downTo 0) {
                    val yMid = topY + (3 - level) * tierH + tierH * 0.65f
                    val eVal = tierData[level].second
                    drawText("%.0f J".format(eVal), cx, yMid, tp)
                }

                tp.color = 0xFFD32F2F.toInt()
                tp.textSize = 20f
                tp.textAlign = Paint.Align.LEFT
                drawText("90% Heat Loss", cx + w * 0.28f, topY + totalH * 0.45f, tp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Producers", "Herbivores", "Carnivores", "Apex").forEachIndexed { idx, name ->
                val isSel = selectedTier == idx
                if (isSel) {
                    FilledTonalButton(
                        onClick = { selectedTier = idx },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedTier = idx },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(name, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val currentInfo = tierData[selectedTier]
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentInfo.first,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Energy: %.0f J".format(currentInfo.second),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentInfo.third,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
