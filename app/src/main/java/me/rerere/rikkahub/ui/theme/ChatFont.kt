package me.rerere.rikkahub.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting

val LocalChatFontFamily = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun ChatFontProvider(
    displaySetting: DisplaySetting,
    content: @Composable () -> Unit,
) {
    val chatFontFamily = rememberChatFontFamily(displaySetting)
    CompositionLocalProvider(LocalChatFontFamily provides chatFontFamily) {
        content()
    }
}

@Composable
fun rememberChatFontFamily(displaySetting: DisplaySetting): FontFamily {
    val context = LocalContext.current
    return remember(displaySetting.chatFontFamily) {
        displaySetting.resolveChatFontFamily(context)
    }
}

fun DisplaySetting.resolveChatFontFamily(context: Context): FontFamily = when (chatFontFamily) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> FontFamily.Default
}
