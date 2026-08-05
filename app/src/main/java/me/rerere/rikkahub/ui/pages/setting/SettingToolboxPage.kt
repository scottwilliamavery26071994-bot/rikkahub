/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（SettingToolboxPage：开发者工具箱）
 */

package me.rerere.rikkahub.ui.pages.setting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.api.Toolbox

private val TOOL_NAMES = listOf("Base64", "时间戳", "密码", "JSON", "颜色", "进制", "正则")

/**
 * 开发者工具箱页面.
 */
@Composable
fun SettingToolboxPage(onBack: () -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("工具箱") },
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
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab) {
                TOOL_NAMES.forEachIndexed { index, name ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(name) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> Base64Tool()
                    1 -> TimestampTool()
                    2 -> PasswordTool()
                    3 -> JsonTool()
                    4 -> ColorTool()
                    5 -> RadixTool()
                    6 -> RegexTool()
                }
            }
        }
    }
}

@Composable
private fun Base64Tool() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("输入文本") }, minLines = 3)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button({ output = Toolbox.base64Encode(input) }) { Text("编码") }
        Button({ output = Toolbox.base64Decode(input) }) { Text("解码") }
    }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("结果") }, minLines = 3, readOnly = true)
}

@Composable
private fun TimestampTool() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("日期(如 2024-01-01 12:00:00) 或 时间戳") })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button({
            output = runCatching {
                val t = input.trim().toLongOrNull()
                if (t != null) "时间戳 $t → " + Toolbox.timestampToDate(t)
                else "日期 → " + Toolbox.dateToTimestamp(input.trim())
            }.getOrDefault("格式错误")
        }) { Text("转换") }
    }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("结果") }, readOnly = true)
}

@Composable
private fun PasswordTool() {
    var length by remember { mutableStateOf("16") }
    var output by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(length, { length = it }, modifier = Modifier.weight(1f), label = { Text("长度") })
        Button({ output = Toolbox.generatePassword(length.toIntOrNull() ?: 16, true, true, true, true) }) { Text("生成") }
    }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("密码（大小写+数字+符号）") }, readOnly = true)
}

@Composable
private fun JsonTool() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("JSON") }, minLines = 5)
    Button({ output = Toolbox.jsonPretty(input) }) { Text("格式化") }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("结果") }, minLines = 5, readOnly = true)
}

@Composable
private fun ColorTool() {
    var c1 by remember { mutableStateOf("#FF0000") }
    var c2 by remember { mutableStateOf("#0000FF") }
    var ratio by remember { mutableStateOf("0.5") }
    var output by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(c1, { c1 = it }, modifier = Modifier.weight(1f), label = { Text("颜色1") })
        OutlinedTextField(c2, { c2 = it }, modifier = Modifier.weight(1f), label = { Text("颜色2") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(ratio, { ratio = it }, modifier = Modifier.weight(1f), label = { Text("比例 0-1") })
        Button({ output = Toolbox.mixColors(c1, c2, ratio.toDoubleOrNull() ?: 0.5) }) { Text("混合") }
    }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("混合结果") }, readOnly = true)
    if (output.startsWith("#")) {
        Text("预览色值: $output", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RadixTool() {
    var value by remember { mutableStateOf("255") }
    var fromBase by remember { mutableStateOf("10") }
    var toBase by remember { mutableStateOf("16") }
    var output by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value, { value = it }, modifier = Modifier.weight(2f), label = { Text("数值") })
        OutlinedTextField(fromBase, { fromBase = it }, modifier = Modifier.weight(1f), label = { Text("原进制") })
        OutlinedTextField(toBase, { toBase = it }, modifier = Modifier.weight(1f), label = { Text("目标进制") })
    }
    Button({
        output = Toolbox.radixConvert(value, fromBase.toIntOrNull() ?: 10, toBase.toIntOrNull() ?: 16)
    }) { Text("转换") }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("结果") }, readOnly = true)
}

@Composable
private fun RegexTool() {
    var text by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("文本") }, minLines = 4)
    OutlinedTextField(pattern, { pattern = it }, Modifier.fillMaxWidth(), label = { Text("正则") })
    Button({
        val matches = Toolbox.regexTest(text, pattern)
        output = if (matches.isEmpty()) "无匹配" else "匹配 ${matches.size} 处:\n" + matches.joinToString("\n")
    }) { Text("测试") }
    OutlinedTextField(output, { }, Modifier.fillMaxWidth(), label = { Text("结果") }, minLines = 4, readOnly = true)
}
