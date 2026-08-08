package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    // 从 messageNodes 中提取实际的当前消息
    val messages = remember(conversation.messageNodes) {
        conversation.messageNodes.flatMap { node ->
            if (node.selectIndex in node.messages.indices) {
                listOf(node.messages[node.selectIndex])
            } else if (node.messages.isNotEmpty()) {
                listOf(node.messages.last())
            } else {
                emptyList()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // 顶部加载条
        AnimatedVisibility(visible = loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 错误横幅
        if (errors.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errors.lastOrNull()?.let { it.title ?: it.error.message } ?: "错误",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearAllErrors) {
                        Text("关闭", fontSize = 12.sp)
                    }
                }
            }
        }

        if (messages.isEmpty() && !loading) {
            // 空状态 — 新对话
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "开始一段新的对话吧",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "在下方输入消息开始聊天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // 加载中的三个点动画
                if (loading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            ThinkingDots()
                        }
                    }
                }
            }
        }
    }
}

// 三个点动画 — AI 思考中
@Composable
private fun ThinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
            )
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 消息气泡
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                                        shape = RoundedCornerShape(8.dp)
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
                                    onDeny = { reason -> onToolApproval(part.toolCallId, false, reason) }
                                )
                            }
                            is UIMessagePart.Image -> {
                                Text(
                                    text = "[图片]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {}
                        }
                    }

                    // 翻译
                    message.translation?.let { translation ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
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
            }

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                TextButton(
                    onClick = { onEdit(message) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("编辑", fontSize = 11.sp)
                }
                TextButton(
                    onClick = { onDelete(message) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("删除", fontSize = 11.sp)
                }
                if (!isUser) {
                    TextButton(
                        onClick = { onRegenerate(message) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("重新生成", fontSize = 11.sp)
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
    onDeny: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
            if (tool.approvalState is me.rerere.ai.ui.ToolApprovalState.Pending) {
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
