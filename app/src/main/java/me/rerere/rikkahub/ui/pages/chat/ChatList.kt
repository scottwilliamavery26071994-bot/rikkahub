package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Any,
    state: Any,
    loading: Boolean,
    processingStatus: String,
    previewMode: Boolean,
    settings: Any,
    hazeState: Any,
    errors: List<Any>,
    onDismissError: (Any) -> Unit,
    onClearAllErrors: () -> Unit,
    onPublishMessage: (Any) -> Unit,
    onRegenerate: (Any) -> Unit,
    onEdit: (Any) -> Unit,
    onForkMessage: (Any) -> Unit,
    onDelete: (Any) -> Unit,
    onCompressContext: (String, Int, Boolean) -> Unit,
    onTranslate: (Any, String) -> Unit,
    onClearTranslation: (Any) -> Unit,
    onToolApproval: (Any, Boolean, String) -> Unit,
    onToolAnswer: (Any) -> Unit,
    onToggleFavorite: (Any) -> Unit,
    onConversationSystemPromptChange: (String) -> Unit?,
    onClickSuggestion: (Any) -> Unit,
    onUpdateMessage: (Any) -> Unit,
    animatedVisibilityScope: Any,
    onSuggestion: (Any) -> Unit,
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
