package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.service.ChatError
import androidx.compose.foundation.lazy.LazyListState
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Text(
            text = "ChatList - Loading: $loading",
            modifier = Modifier.padding(16.dp)
        )
    }
}
