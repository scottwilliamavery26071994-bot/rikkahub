package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.service.ChatError
import kotlin.uuid.Uuid

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String?,
    previewMode: Boolean,
    settings: Settings,
    hazeState: Any,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onPublishMessage: (UIMessage) -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onCompressContext: (String, Int, Int) -> Unit,
    onTranslate: (UIMessage, String) -> Unit,
    onClearTranslation: (UIMessage) -> Unit,
    onToolApproval: (String, Boolean, String) -> Unit,
    onToolAnswer: (String, String) -> Unit,
    onToggleFavorite: (MessageNode) -> Unit,
    onConversationSystemPromptChange: (String) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    animatedVisibilityScope: Any,
    onSuggestion: (String) -> Unit,
    onNewPrompt: (String) -> Unit,
    onJumpToMessage: (Int) -> Unit
) {
    val messages = conversation.currentMessages

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Loading indicator
        AnimatedVisibility(visible = loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Processing status
        if (processingStatus != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = processingStatus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Error banner
        if (errors.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errors.lastOrNull()?.message ?: "发生错误",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearAllErrors) {
                        Text("关闭", fontSize = 12.sp)
                    }
                    if (errors.size > 1) {
                        TextButton(onClick = onClearAllErrors) {
                            Text("清除全部(${errors.size})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Messages list
        if (messages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loading) "思考中..." else "开始一段新的对话",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onRegenerate = onRegenerate,
                        onForkMessage = onForkMessage,
                        onTranslate = onTranslate,
                        onClearTranslation = onClearTranslation,
                        onToolApproval = onToolApproval,
                        onToolAnswer = onToolAnswer
                    )
                }

                // Loading placeholder at the end
                if (loading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            AnimatedVisibility(visible = true, enter = fadeIn()) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UIMessage,
    onEdit: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onTranslate: (UIMessage, String) -> Unit,
    onClearTranslation: (UIMessage) -> Unit,
    onToolApproval: (String, Boolean, String) -> Unit,
    onToolAnswer: (String, String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Role label
            if (!isUser && !isSystem) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }

            // Message content
            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 12.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 12.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        )
                    )
                    .background(
                        when {
                            isUser -> MaterialTheme.colorScheme.primaryContainer
                            isSystem -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                message.parts.forEach { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            if (part.text.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = part.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is UIMessagePart.Reasoning -> {
                            if (part.reasoning.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "思考: ${part.reasoning}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                        is UIMessagePart.Tool -> {
                            ToolCallCard(
                                tool = part,
                                onApprove = { onToolApproval(part.toolCallId, true, "") },
                                onDeny = { reason -> onToolApproval(part.toolCallId, false, reason) },
                                onAnswer = { answer -> onToolAnswer(part.toolCallId, answer) }
                            )
                        }
                        is UIMessagePart.Image -> {
                            Text(
                                text = "[图片: ${part.url}]",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            // Other part types (video, audio, etc.)
                        }
                    }
                }

                // Translation
                message.translation?.let { translation ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Message actions (show on hover or tap)
            if (!isSystem) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    TextButton(
                        onClick = { onEdit(message) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("编辑", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { onDelete(message) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("删除", fontSize = 11.sp)
                    }
                    if (!isUser) {
                        TextButton(
                            onClick = { onRegenerate(message) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("重新生成", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(
    tool: UIMessagePart.Tool,
    onApprove: () -> Unit,
    onDeny: (String) -> Unit,
    onAnswer: (String) -> Unit
) {
    val approvalState = tool.approvalState
    val isPending = approvalState is me.rerere.ai.ui.ToolApprovalState.Pending

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "🔧 ${tool.toolName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }

            // Tool output
            tool.output.forEach { output ->
                when (output) {
                    is UIMessagePart.Text -> {
                        Text(
                            text = output.text.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    else -> {}
                }
            }

            // Approval buttons for pending tools
            if (isPending) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onApprove,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("批准", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onDeny("用户拒绝") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("拒绝", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
