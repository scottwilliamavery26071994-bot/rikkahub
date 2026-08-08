package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.model.Conversation
import me.rerere.rikkahub.model.MessageNode
import me.rerere.rikkahub.model.MessageRole
import me.rerere.rikkahub.ui.components.ChatMessage
import me.rerere.rikkahub.ui.components.ListSelectableItem
import me.rerere.rikkahub.ui.components.ConversationSizeWarningDialog
import me.rerere.rikkahub.util.rememberConversationSizeInfo
import java.util.UUID

@Composable
fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String,
    settings: Settings,
    hazeState: HazeState,
    errors: List<Throwable>,
    onDismissError: (Throwable) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onForkMessage: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onTranslate: (String, String) -> Unit,
    onClearTranslation: (String) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToolApproval: (String, Boolean, String) -> Unit,
    onToolAnswer: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onConversationSystemPromptChange: (String) -> Unit?,
    onPublishMessage: (String) -> Unit
) {
    // isAtBottom函数
    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastItem = lastOrNull() ?: return false
        val lastPos = lastItem.offset + lastItem.size
        return lastPos <= state.layoutInfo.viewportEndOffset - 8
    }

    // 聊天选择
    val selectedItems = remember { mutableStateListOf<UUID>() }
    var selecting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // 选中状态自动清空
    LaunchedEffect(selecting) {
        if (!selecting) {
            selectedItems.clear()
        }
    }

    // 自动跟随键盘滚动
    if (settings.displaySetting.enableAutoScroll) {
        // 贴底闩锁：生成期间一旦贴底就持续请求贴底，增长导致短暂离开底部时不丢请求
        var stickToBottom by remember { mutableStateOf(true) }
        LaunchedEffect(state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
                if (!state.isScrollInProgress && loading) {
                    if (visibleItemsInfo.isAtBottom()) {
                        stickToBottom = true
                    } else {
                        stickToBottom = false
                    }
                    if (stickToBottom) {
                        state.requestScrollToItem(conversation.messageNodes.lastIndex + 10)
                    }
                }
            }
        }
    }

    // LazyColumn
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        // 尺寸警告
        val sizeInfo = remember { rememberConversationSizeInfo(conversation) }
        if (sizeInfo.showWarning && showSizeWarningDialog) {
            item(key = SizeWarning) {
                ConversationSizeWarningDialog(
                    onDismiss = { showSizeWarningDialog = false },
                    sizeInfo = sizeInfo
                )
            }
        }

        // 主要内容
        item {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // 消息列表
                val displayNodes = remember(conversation.messageNodes) {
                    conversation.messageNodes.filter { node ->
                        val msg = node.currentMessage
                        val text = msg.toText().trim()
                        !(msg.role == MessageRole.ASSISTANT && text == [SKIP])
                    }
                }

                itemsIndexed(
                    items = displayNodes,
                    key = { index, item -> item.id },
                ) { index, node ->
                    Column {
                        ListSelectableItem(
                            key = node.id,
                            onSelectChange = {
                                if (!selectedItems.contains(node.id)) {
                                    selectedItems.add(node.id)
                                } else {
                                    selectedItems.remove(node.id)
                                }
                            },
                            selectedKeys = selectedItems,
                        )
                        ChatMessage(
                            node = node,
                            assistant = if (conversation.assistantIds.size > 1) {
                                node.currentMessage.modelId?.let { mid ->
                                    conversation.assistantIds.first { it != mid }
                                }
                            } else {
                                null
                            },
                            onPublish = if (conversation.assistantIds.size > 1 &&
                                node.currentMessage.modelId != null
                            ) {
                                { assistantId ->
                                    conversation.publish(assistantId)
                                }
                            } else {
                                null
                            },
                            onRegenerate = {
                                conversation.regenerate(node.currentMessage.id)
                            },
                            onEdit = {
                                conversation.edit(node.currentMessage.id)
                            },
                            onFork = {
                                conversation.fork(node.currentMessage.id)
                            },
                            onDelete = {
                                conversation.delete(node.currentMessage.id)
                            },
                            onShare = {
                                conversation.share(node.currentMessage.id)
                            },
                            selected = selectedItems.contains(node.id),
                            onUpdate = {
                                conversation.update(it)
                            },
                            onToggleFavorite = {
                                conversation.toggleFavorite(node.currentMessage.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
