/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（SettingApkReversePage：APK 静态分析）
 */

package me.rerere.rikkahub.ui.pages.setting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.api.ApkReverse
import me.rerere.rikkahub.data.api.ApkReverseResult
import java.io.File
import java.io.FileOutputStream

/**
 * APK 逆向分析页面：选择 APK 文件，解析包名/版本/权限/组件/接口.
 */
@Composable
fun SettingApkReversePage(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApkReverseResult?>(null) }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            loading = true
            result = withContext(Dispatchers.IO) {
                runCatching {
                    // 复制到缓存目录后分析
                    val cacheFile = File(context.cacheDir, "analysis.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                    }
                    ApkReverse.reverse(cacheFile)
                }.getOrElse {
                    ApkReverseResult(error = it.message ?: "读取失败")
                }
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("APK 逆向分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("选择 APK 文件，静态分析其包名、版本、权限、组件和外部接口。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { filePicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) {
                Text("选择 APK 文件")
            }

            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            val r = result
            if (r != null) {
                Spacer(Modifier.height(16.dp))
                if (r.error.isNotBlank()) {
                    Text("错误: ${r.error}", color = MaterialTheme.colorScheme.error)
                }
                if (r.fileName.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            InfoRow("文件名", r.fileName)
                            InfoRow("大小", "${r.fileSize / 1024 / 1024.0} MB")
                            InfoRow("包名", r.packageName)
                            InfoRow("版本", "${r.versionName} (code ${r.versionCode})")
                            InfoRow("SDK", "min ${r.minSdk} / target ${r.targetSdk}")
                            InfoRow("DEX 数量", "${r.dexCount}")
                        }
                    }
                    if (r.permissions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("权限 (${r.permissions.size})", style = MaterialTheme.typography.titleSmall)
                        r.permissions.take(30).forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (r.activities.isNotEmpty() || r.services.isNotEmpty() || r.receivers.isNotEmpty() || r.providers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("组件", style = MaterialTheme.typography.titleSmall)
                        r.activities.take(15).forEach { Text("Activity: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        r.services.take(10).forEach { Text("Service: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        r.receivers.take(10).forEach { Text("Receiver: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        r.providers.take(5).forEach { Text("Provider: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (r.classes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Text("类 (${r.classes.size})", style = MaterialTheme.typography.titleSmall)
                        r.classes.take(50).forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (r.interfaces.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Text("外部接口 (${r.interfaces.size})", style = MaterialTheme.typography.titleSmall)
                        r.interfaces.take(30).forEach { Text(it, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun Row(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(modifier, content = content)
}
