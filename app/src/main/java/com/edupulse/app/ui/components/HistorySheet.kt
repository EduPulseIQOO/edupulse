package com.edupulse.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edupulse.app.history.ChatSession
import com.edupulse.app.history.DoubtHistoryItem
import com.edupulse.app.history.QuerySubject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onDismiss: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAll: () -> Unit,
    onNewChat: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💬", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chat Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (sessions.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = "${sessions.size}",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                if (sessions.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            FilledTonalButton(
                onClick = {
                    onNewChat()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Start New Doubt Thread", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 42.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No saved doubt sessions yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Your offline problem-solving sessions will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItemCard(
                            session = session,
                            isActive = session.id == currentSessionId,
                            onClick = {
                                onSelectSession(session)
                                onDismiss()
                            },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

// Backward compatibility overload
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    historyItems: List<DoubtHistoryItem>,
    onDismiss: () -> Unit,
    onSelectItem: (DoubtHistoryItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val sessions = historyItems.map { item ->
        val userMsg = com.edupulse.app.ui.ChatMessage(
            sender = com.edupulse.app.ui.MessageSender.USER,
            text = item.question,
            timestamp = item.timestamp
        )
        val asstMsg = com.edupulse.app.ui.ChatMessage(
            sender = com.edupulse.app.ui.MessageSender.ASSISTANT,
            text = item.solution,
            diagram = item.diagram,
            timestamp = item.timestamp
        )
        ChatSession(
            id = item.id,
            title = item.title,
            subject = item.subject,
            createdAt = item.timestamp,
            updatedAt = item.timestamp,
            languageCode = item.languageCode,
            messages = listOf(userMsg, asstMsg)
        )
    }

    HistorySheet(
        sessions = sessions,
        currentSessionId = "",
        onDismiss = onDismiss,
        onSelectSession = { session ->
            val firstUser = session.messages.firstOrNull { it.sender == com.edupulse.app.ui.MessageSender.USER }?.text ?: session.title
            val lastAsst = session.messages.lastOrNull { it.sender == com.edupulse.app.ui.MessageSender.ASSISTANT }?.text ?: ""
            onSelectItem(
                DoubtHistoryItem(
                    id = session.id,
                    timestamp = session.updatedAt,
                    title = session.title,
                    question = firstUser,
                    solution = lastAsst,
                    diagram = session.activeDiagram,
                    languageCode = session.languageCode,
                    subject = session.subject
                )
            )
        },
        onDeleteSession = onDeleteItem,
        onClearAll = onClearAll,
        onNewChat = onDismiss
    )
}

@Composable
private fun SessionItemCard(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        session.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    val subject = session.querySubject
    val badgeLabel = "${subject.emoji} ${subject.badgeText}"
    val badgeContainerColor = when (subject) {
        QuerySubject.GENERAL -> MaterialTheme.colorScheme.surfaceVariant
        QuerySubject.MATH, QuerySubject.CHEMISTRY -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = when (subject) {
        QuerySubject.GENERAL -> MaterialTheme.colorScheme.onSurfaceVariant
        QuerySubject.MATH, QuerySubject.CHEMISTRY -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val cardBorder = if (isActive) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeContainerColor
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeContentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = session.languageCode,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (session.lastMessagePreview.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = session.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💬 " + session.userMessageCount + " doubts (" + session.messages.size + " msgs)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (session.activeDiagram != null) {
                        Text(
                            text = "• 📐 Simulation",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
