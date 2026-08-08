package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSystemToolsPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val t = settings.systemToolsSetting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统工具") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("设备信息") }) {
                sw("存储信息", t.storageInfoEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(storageInfoEnabled = it))) }
                sw("WiFi信息", t.wifiInfoEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(wifiInfoEnabled = it))) }
                sw("SIM卡/运营商", t.telephonyInfoEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(telephonyInfoEnabled = it))) }
                sw("电池信息", t.batteryEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(batteryEnabled = it))) }
            }}

            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("设备控制") }) {
                sw("手电筒", t.torchEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(torchEnabled = it))) }
                sw("振动", t.vibrateEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(vibrateEnabled = it))) }
                sw("屏幕亮度", t.brightnessEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(brightnessEnabled = it))) }
                sw("音量控制", t.volumeEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(volumeEnabled = it))) }
                sw("唤醒屏幕", t.wakeScreenEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(wakeScreenEnabled = it))) }
                sw("设置壁纸", t.setWallpaperEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(setWallpaperEnabled = it))) }
            }}

            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("通知和交互") }) {
                sw("发送通知", t.postNotificationEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(postNotificationEnabled = it))) }
                sw("Toast提示", t.toastEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(toastEnabled = it))) }
                sw("分享", t.shareEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(shareEnabled = it))) }
            }}

            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("媒体和文件") }) {
                sw("媒体扫描", t.scanMediaEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(scanMediaEnabled = it))) }
                sw("相机OCR", t.cameraOcrEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(cameraOcrEnabled = it))) }
                sw("短信读取", t.smsEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(smsEnabled = it))) }
            }}

            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("应用管理") }) {
                sw("应用切换", t.appSwitchEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(appSwitchEnabled = it))) }
                sw("应用使用统计", t.appUsageEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(appUsageEnabled = it))) }
                sw("应用锁", t.appLockEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(appLockEnabled = it))) }
            }}

            item { CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("高级") }) {
                sw("通知查询", t.notificationQueryEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(notificationQueryEnabled = it))) }
                sw("闹钟", t.alarmEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(alarmEnabled = it))) }
                sw("计时器", t.timerEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(timerEnabled = it))) }
                sw("音乐控制", t.musicEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(musicEnabled = it))) }
                sw("附近探索", t.locationExploreEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(locationExploreEnabled = it))) }
                sw("指纹验证", t.fingerprintEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(fingerprintEnabled = it))) }
                sw("设备事件追踪", t.deviceEventTrackingEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(deviceEventTrackingEnabled = it))) }
                sw("Gadgetbridge健康", t.gadgetbridgeEnabled) { vm.updateSettings(settings.copy(systemToolsSetting = t.copy(gadgetbridgeEnabled = it))) }
            }}
        }
    }
}

private fun CardGroupScope.sw(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    item(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}
