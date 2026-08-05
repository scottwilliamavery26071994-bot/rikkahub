/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（SettingApiExplorerPage：API 探索器）
 */

package me.rerere.rikkahub.ui.pages.setting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.api.ApiEndpoint
import me.rerere.rikkahub.data.api.ApiExploreResult
import me.rerere.rikkahub.data.api.ApiExplorer

/**
 * API 探索器：输入网址，自动发现其中的 API 接口.
 */
@Composable
fun SettingApiExplorerPage(onBack: () -> Unit = {}) {
    var url by remember { mutableStateOf("https://") }
    var timeout by remember { mutableStateOf("10000") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApiExploreResult?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("API 探索器") },
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
                .padding(16.dp),
        ) {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("目标网址") }, singleLine = true)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    timeout,
                    { timeout = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("超时(ms)") },
                    singleLine = true,
                )
                androidx.compose.material3.Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            result = ApiExplorer.explore(url.trim(), timeout.toIntOrNull() ?: 10000)
                            loading = false
                        }
                    },
                ) { Text("探索") }
            }

            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            val r = result
            if (r != null) {
                Spacer(Modifier.height(8.dp))
                Text("页面: ${r.pageTitle.ifBlank { r.pageUrl }}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("外部脚本: ${r.externalScripts}  发现接口: ${r.endpoints.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.error.isNotBlank()) {
                    Text("错误: ${r.error}", color = MaterialTheme.colorScheme.error)
                }
                if (r.pageTextPreview.isNotBlank()) {
                    Text("页面文本预览: ${r.pageTextPreview.take(200)}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(r.endpoints) { ep -> EndpointCard(ep) }
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(ep: ApiEndpoint) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ep.method,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ep.method == "GET") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text("来源: ${ep.source}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ep.status != null) {
                    Text("HTTP ${ep.status}", color = if ((ep.status ?: 0) in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
            Text(ep.url, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ep.responsePreview.isNotBlank()) {
                Text(ep.responsePreview.take(200), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
