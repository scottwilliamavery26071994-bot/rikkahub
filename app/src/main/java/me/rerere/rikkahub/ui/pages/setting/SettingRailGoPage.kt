/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（SettingRailGoPage：火车票/车站查询）
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
import androidx.compose.material3.HorizontalDivider
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
import me.rerere.rikkahub.data.api.RailGoApi
import me.rerere.rikkahub.data.api.RailStation
import me.rerere.rikkahub.data.api.RailStationResult
import me.rerere.rikkahub.data.api.RailTrainResult

/**
 * RailGo 火车查询页面：搜索车站 → 查车次 → 查时刻表.
 */
@Composable
fun SettingRailGoPage(onBack: () -> Unit = {}) {
    var keyword by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RailStation>>(emptyList()) }
    var selectedStation by remember { mutableStateOf<RailStation?>(null) }
    var stationResult by remember { mutableStateOf<RailStationResult?>(null) }
    var trainDetail by remember { mutableStateOf<RailTrainResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search(kw: String) {
        if (kw.isBlank()) return
        scope.launch {
            loading = true
            searchResults = RailGoApi.searchStation(kw)
            loading = false
        }
    }

    fun queryStation(station: RailStation) {
        selectedStation = station
        trainDetail = null
        scope.launch {
            loading = true
            stationResult = RailGoApi.queryStation(station.telecode)
            loading = false
        }
    }

    fun queryTrain(trainNo: String) {
        scope.launch {
            loading = true
            trainDetail = RailGoApi.queryTrain(trainNo)
            loading = false
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("火车查询") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("输入站名/拼音/电报码") },
                    singleLine = true,
                )
                androidx.compose.material3.Button(onClick = { search(keyword) }) {
                    Text("查询")
                }
            }

            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (selectedStation == null) {
                // 搜索结果 + 常用车站
                Spacer(Modifier.height(12.dp))
                Text("常用车站", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(RailGoApi.COMMON_STATIONS) { station ->
                        StationRow(station) { queryStation(station) }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    HorizontalDivider()
                    Text("搜索结果", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(searchResults) { station ->
                            StationRow(station) { queryStation(station) }
                        }
                    }
                }
            } else {
                // 车站车次列表
                val result = stationResult
                if (result != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${result.name}（${result.telecode}） 当前共 ${result.trainList.size} 趟车次",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (result.error.isNotBlank()) {
                        Text("错误: ${result.error}", color = MaterialTheme.colorScheme.error)
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(result.trainList) { trainNo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { queryTrain(trainNo) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(trainNo, style = MaterialTheme.typography.titleMedium)
                                    Text("点按查看时刻表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (trainDetail != null) {
                    TrainDetailCard(trainDetail!!)
                }
                androidx.compose.material3.TextButton(onClick = { selectedStation = null; stationResult = null; trainDetail = null }) {
                    Text("← 返回车站列表")
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: RailStation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(station.name, style = MaterialTheme.typography.bodyLarge)
            Text("${station.telecode}  ${station.pinyin}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrainDetailCard(detail: RailTrainResult) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("车次: ${detail.trainNo}", style = MaterialTheme.typography.titleMedium)
            Text("路局: ${detail.bureauName}  车型: ${detail.car}  配属: ${detail.carOwner}")
            if (detail.rundays.isNotEmpty()) {
                Text("开行: ${detail.rundays.joinToString(" ")}")
            }
            if (detail.timetable.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("时刻表", style = MaterialTheme.typography.titleSmall)
                detail.timetable.forEach { line ->
                    Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (detail.error.isNotBlank()) {
                Text("错误: ${detail.error}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
