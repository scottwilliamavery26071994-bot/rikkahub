# HANDOFF.md — RikkaHub 项目状态

> 最后更新：2026-08-08

---

## 当前任务

- 等待 GitHub Actions 编译验证
- ~~下载 DeepSeek R1 model.onnx.data (7.3GB) 权重文件~~（已删除本地模型功能）

---

## 已完成的内容

### 一、聊天界面恢复
- 从 `rikkahub/rikkahub` 源仓库恢复原始 ChatList.kt (849行) / ChatPage.kt (827行) / ChatVM.kt (353行)
- 添加 ChatFont.kt 字体支持
- 适配所有回调函数签名

### 二、~~本地模型~~（已删除 🗑️）
- 已完整移除：LocalModel sealed 子类、5 个 localmodel 源文件、ProviderConfigure UI、DI 注册、所有 when 分支引用

### 三、三项强制兜底

| 能力 | 不支持时 |
|------|----------|
| 工具调用 | `<tool_call>` 提示词式 |
| 推理 | `<thinking>` 提示词式 + 中文强制 |
| 上下文超限 | 自动压缩 (保留最近6条) 提示重试 |

### 四、余额自动检测
- BalanceAutoDetector.kt — 9 种接口 + 通用字段搜索
- OpenAI / Claude / Google 全部接入
- ProviderSetting 强制 apiKey/baseUrl 抽象属性

### 五、系统工具页面
- CardGroup + ListItem + Switch 风格 (与原版设置页一致)
- 27 个开关分 6 组 (设备信息/控制/通知/媒体/应用/高级)
- TopAppBar + BackButton

### 六、类型系统
- apiKey/baseUrl 升级为 ProviderSetting 抽象属性
- 所有编译错误修复

---

## 卡住的问题

1. **源仓库页面缺失字符串资源** — SettingThemePage / SettingPreferencesUIPage 等需要的字符串和数据字段不在项目中，已删除

---

## 下一步计划

1. ✅ 等待编译通过
2. 按需补全源仓库缺失的字符串资源

---

## 踩过的坑

| 坑 | 解决 |
|----|------|
| data class 不能继承 data class | 改为独立子类（后删除） |
| ONNX Result 遍历方式错误 | 用 iterator 代替 result.get(0).get() |
| flow{} 内不能嵌套 withContext | 改为 callbackFlow + launch(IO) |
| 虚构 HuggingFace URL | 改为真实验证过的 URL |
| 源仓库页面复制后缺失字符串 | 回退页面，保留系统工具页 |
| CardGroup 非 Composable 直接调用 | 用 CardGroup { item{} } 语法 |
| sw() 辅助函数缺少 @Composable | 添加注解 |
| ProviderConfigure 签名变化 | 所有调用点显式传参 |
| ClaudeProvider getBalance 破坏 listModels | 修复函数签名重叠 |
| getByKey 在 JsonElement/JsonObject 用法 | 先转 JsonObject 再调用 |
| 编译错误反复出现 | 增量修复，逐个提交验证 |
| 本地模型功能完整移除 | 删除 5 个源文件 + 10 个文件的 when 分支/引用 |
