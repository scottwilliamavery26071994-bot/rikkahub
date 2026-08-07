package me.rerere.rikkahub.utils

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat

/**
 * 读取设备屏幕真实圆角半径（Android 12+ / API 31+）。
 *
 * 通过 WindowInsets.getRoundedCorner() 动态获取设备四个角的精确半径。
 * 用于让输入框 / 弹窗的圆角与屏幕圆角曲线契合，避免"方角贴圆屏"的视觉冲突。
 *
 * 低版本（< API 31）无系统接口，回退到常见设备圆角参考值。
 */
@Composable
fun rememberDeviceCornerRadius(
    position: Int = RoundedCorner.POSITION_BOTTOM_LEFT,
): Dp {
    val view = LocalView.current
    return remember(view, position) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val root = view.findActivityRootView()
                val insets = ViewCompat.getRootWindowInsets(root)
                val corner = insets?.getRoundedCorner(position)
                corner?.radius?.pxToDp() ?: FALLBACK_CORNER_RADIUS
            }.getOrDefault(FALLBACK_CORNER_RADIUS)
        } else {
            FALLBACK_CORNER_RADIUS
        }
    }
}

/** 输入框底部圆角（通常与屏幕底部圆角一致） */
@Composable
fun rememberInputBarCornerRadius(): Dp =
    rememberDeviceCornerRadius(RoundedCorner.POSITION_BOTTOM_LEFT)

/** 弹窗/底部面板顶部圆角（与屏幕顶部圆角一致） */
@Composable
fun rememberTopPanelCornerRadius(): Dp =
    rememberDeviceCornerRadius(RoundedCorner.POSITION_TOP_LEFT)

private fun android.view.View.findActivityRootView(): android.view.View {
    var v: android.view.View? = this
    while (v?.parent is android.view.View) {
        v = v.parent as android.view.View
    }
    return v ?: this
}

private fun Int.pxToDp(): Dp = (this / android.content.res.Resources.getSystem().displayMetrics.density).dp

/** 低版本回退：常见机型底部圆角约 28dp */
private val FALLBACK_CORNER_RADIUS: Dp = 28.dp
