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
                    Slider(
                        value = velocity,
                        onValueChange = { velocity = it },
                        valueRange = 5f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8)
                        )
                    )

                    Text("Launch Angle: ${angle.toInt()}°", color = Color.White)
                    Slider(
                        value = angle,
                        onValueChange = { angle = it },
                        valueRange = 10f..85f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF472B6),
                            activeTrackColor = Color(0xFFF472B6)
                        )
                    )
                }
            }
        }
    }
}
