package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Any,
    state: Any,
    loading: Boolean,
    processingStatus: String,
    settings: Any,
    hazeState: Any,
    errors: List<Throwable>,
    onDismissError: (Throwable) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onForkMessage: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateMessage: (Any) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onTranslate: (String, String) -> Unit,
    onClearTranslation: (String) -> Unit,
    animatedVisibilityScope: Any,
    onToolApproval: (String, Boolean, String) -> Unit,
    onToolAnswer: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onConversationSystemPromptChange: (String) -> Unit?,
    onPublishMessage: (String) -> Unit
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
