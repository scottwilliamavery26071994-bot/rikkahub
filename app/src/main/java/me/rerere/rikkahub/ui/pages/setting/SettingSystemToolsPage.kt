package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingSystemToolsPage() {
    val vm: SettingVM = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemTools = settings.systemToolsSetting

    fun update(block: (me.rerere.rikkahub.data.datastore.SystemToolsSetting) -> me.rerere.rikkahub.data.datastore.SystemToolsSetting) {
        vm.updateSettings(settings.copy(systemToolsSetting = block(systemTools)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("系统工具", style = MaterialTheme.typography.titleLarge)
        Text("启用的工具将作为 AI 可调用的功能", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // 设备信息
        SectionTitle("设备信息")
        SwitchRow("存储信息", systemTools.storageInfoEnabled) { update(systemTools.copy(storageInfoEnabled = it)) }
        SwitchRow("WiFi信息", systemTools.wifiInfoEnabled) { update(systemTools.copy(wifiInfoEnabled = it)) }
        SwitchRow("SIM卡/运营商", systemTools.telephonyInfoEnabled) { update(systemTools.copy(telephonyInfoEnabled = it)) }
        SwitchRow("电池信息", systemTools.batteryEnabled) { update(systemTools.copy(batteryEnabled = it)) }

        // 设备控制
        SectionTitle("设备控制")
        SwitchRow("手电筒", systemTools.torchEnabled) { update(systemTools.copy(torchEnabled = it)) }
        SwitchRow("振动", systemTools.vibrateEnabled) { update(systemTools.copy(vibrateEnabled = it)) }
        SwitchRow("屏幕亮度", systemTools.brightnessEnabled) { update(systemTools.copy(brightnessEnabled = it)) }
        SwitchRow("音量控制", systemTools.volumeEnabled) { update(systemTools.copy(volumeEnabled = it)) }
        SwitchRow("唤醒屏幕", systemTools.wakeScreenEnabled) { update(systemTools.copy(wakeScreenEnabled = it)) }
        SwitchRow("设置壁纸", systemTools.setWallpaperEnabled) { update(systemTools.copy(setWallpaperEnabled = it)) }

        // 通知和交互
        SectionTitle("通知和交互")
        SwitchRow("发送通知", systemTools.postNotificationEnabled) { update(systemTools.copy(postNotificationEnabled = it)) }
        SwitchRow("Toast提示", systemTools.toastEnabled) { update(systemTools.copy(toastEnabled = it)) }
        SwitchRow("分享", systemTools.shareEnabled) { update(systemTools.copy(shareEnabled = it)) }

        // 媒体和文件
        SectionTitle("媒体和文件")
        SwitchRow("媒体扫描", systemTools.scanMediaEnabled) { update(systemTools.copy(scanMediaEnabled = it)) }
        SwitchRow("相机OCR", systemTools.cameraOcrEnabled) { update(systemTools.copy(cameraOcrEnabled = it)) }
        SwitchRow("短信读取", systemTools.smsEnabled) { update(systemTools.copy(smsEnabled = it)) }

        // 应用管理
        SectionTitle("应用管理")
        SwitchRow("应用切换", systemTools.appSwitchEnabled) { update(systemTools.copy(appSwitchEnabled = it)) }
        SwitchRow("应用使用统计", systemTools.appUsageEnabled) { update(systemTools.copy(appUsageEnabled = it)) }
        SwitchRow("应用锁", systemTools.appLockEnabled) { update(systemTools.copy(appLockEnabled = it)) }

        // 高级
        SectionTitle("高级")
        SwitchRow("通知查询", systemTools.notificationQueryEnabled) { update(systemTools.copy(notificationQueryEnabled = it)) }
        SwitchRow("闹钟", systemTools.alarmEnabled) { update(systemTools.copy(alarmEnabled = it)) }
        SwitchRow("计时器", systemTools.timerEnabled) { update(systemTools.copy(timerEnabled = it)) }
        SwitchRow("音乐控制", systemTools.musicEnabled) { update(systemTools.copy(musicEnabled = it)) }
        SwitchRow("附近探索", systemTools.locationExploreEnabled) { update(systemTools.copy(locationExploreEnabled = it)) }
        SwitchRow("指纹验证", systemTools.fingerprintEnabled) { update(systemTools.copy(fingerprintEnabled = it)) }
        SwitchRow("设备事件追踪", systemTools.deviceEventTrackingEnabled) { update(systemTools.copy(deviceEventTrackingEnabled = it)) }
        SwitchRow("Gadgetbridge健康", systemTools.gadgetbridgeEnabled) { update(systemTools.copy(gadgetbridgeEnabled = it)) }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
