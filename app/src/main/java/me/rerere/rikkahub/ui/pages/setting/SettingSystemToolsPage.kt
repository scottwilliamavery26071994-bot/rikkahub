package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSystemToolsPage(vm: SettingVM = org.koin.androidx.compose.koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemTools = settings.systemToolsSetting

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
            item("deviceInfo") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("设备信息") }
                ) {
                    SwitchItem("存储信息", systemTools.storageInfoEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(storageInfoEnabled = it)))
                    }
                    SwitchItem("WiFi信息", systemTools.wifiInfoEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(wifiInfoEnabled = it)))
                    }
                    SwitchItem("SIM卡/运营商", systemTools.telephonyInfoEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(telephonyInfoEnabled = it)))
                    }
                    SwitchItem("电池信息", systemTools.batteryEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(batteryEnabled = it)))
                    }
                }
            }

            item("deviceControl") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("设备控制") }
                ) {
                    SwitchItem("手电筒", systemTools.torchEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(torchEnabled = it)))
                    }
                    SwitchItem("振动", systemTools.vibrateEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(vibrateEnabled = it)))
                    }
                    SwitchItem("屏幕亮度", systemTools.brightnessEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(brightnessEnabled = it)))
                    }
                    SwitchItem("音量控制", systemTools.volumeEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(volumeEnabled = it)))
                    }
                    SwitchItem("唤醒屏幕", systemTools.wakeScreenEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(wakeScreenEnabled = it)))
                    }
                    SwitchItem("设置壁纸", systemTools.setWallpaperEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(setWallpaperEnabled = it)))
                    }
                }
            }

            item("notifications") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("通知和交互") }
                ) {
                    SwitchItem("发送通知", systemTools.postNotificationEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(postNotificationEnabled = it)))
                    }
                    SwitchItem("Toast提示", systemTools.toastEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(toastEnabled = it)))
                    }
                    SwitchItem("分享", systemTools.shareEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(shareEnabled = it)))
                    }
                }
            }

            item("media") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("媒体和文件") }
                ) {
                    SwitchItem("媒体扫描", systemTools.scanMediaEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(scanMediaEnabled = it)))
                    }
                    SwitchItem("相机OCR", systemTools.cameraOcrEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(cameraOcrEnabled = it)))
                    }
                    SwitchItem("短信读取", systemTools.smsEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(smsEnabled = it)))
                    }
                }
            }

            item("appManage") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("应用管理") }
                ) {
                    SwitchItem("应用切换", systemTools.appSwitchEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(appSwitchEnabled = it)))
                    }
                    SwitchItem("应用使用统计", systemTools.appUsageEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(appUsageEnabled = it)))
                    }
                    SwitchItem("应用锁", systemTools.appLockEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(appLockEnabled = it)))
                    }
                }
            }

            item("advanced") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("高级") }
                ) {
                    SwitchItem("通知查询", systemTools.notificationQueryEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(notificationQueryEnabled = it)))
                    }
                    SwitchItem("闹钟", systemTools.alarmEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(alarmEnabled = it)))
                    }
                    SwitchItem("计时器", systemTools.timerEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(timerEnabled = it)))
                    }
                    SwitchItem("音乐控制", systemTools.musicEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(musicEnabled = it)))
                    }
                    SwitchItem("附近探索", systemTools.locationExploreEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(locationExploreEnabled = it)))
                    }
                    SwitchItem("指纹验证", systemTools.fingerprintEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(fingerprintEnabled = it)))
                    }
                    SwitchItem("设备事件追踪", systemTools.deviceEventTrackingEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(deviceEventTrackingEnabled = it)))
                    }
                    SwitchItem("Gadgetbridge健康", systemTools.gadgetbridgeEnabled) {
                        vm.updateSettings(settings.copy(systemToolsSetting = systemTools.copy(gadgetbridgeEnabled = it)))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardGroup.SwitchItem(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}
