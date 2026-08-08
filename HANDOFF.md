# HANDOFF.md — RikkaHub 项目状态

## 当前任务

1. **聊天界面恢复** — 从 `rikkahub/rikkahub` 源仓库拉取原始 ChatList/ChatPage/ChatVM，与当前代码库适配
2. **本地模型支持** — ONNX Runtime 推理引擎 + 模型下载/文件选择
3. **系统工具页面** — 用 CardGroup 风格实现 27 个开关
4. **编译通过** — 等待 GitHub Actions 验证

---

## 已完成的内容

### 聊天界面
- ✅ 从源仓库恢复 ChatList.kt (849行)、ChatPage.kt (827行)、ChatVM.kt (353行)
- ✅ 添加 ChatFont.kt 字体支持
- ✅ 适配函数签名（onTranslate/Locale, onToolApproval/Boolean, onToolAnswer/toolCallId）

### 本地模型
- ✅ LocalModelEngine.kt — ONNX Runtime 推理
- ✅ LocalTokenizer.kt — BPE 分词器
- ✅ LocalModelProvider.kt — Provider 接口
- ✅ LocalModelDownloader.kt — 下载管理（callbackFlow）
- ✅ GgufModelEngine.kt — GGUF 推理占位
- ✅ ProviderSetting.LocalModel — 独立 sealed 子类
- ✅ 文件选择器 UI（SAF）+ 自定义 URL 下载
- ✅ 选择/下载自动保存到 Settings
- ✅ 模型选择后自动同步到模型列表
- ✅ DeepSeek R1 0528 ONNX 模型下载（验证通过）
  - model.onnx (418KB) + model.onnx.data (7.3GB)
  - 下载超时设为 30 分钟

### 三项强制兜底
- ✅ 不支持工具调用 → 提示词式 `<tool_call>`
- ✅ 不支持推理 → 提示词式 `<thinking>`（含中文强制）
- ✅ 上下文超限 → 自动压缩

### 余额自动检测
- ✅ BalanceAutoDetector.kt — 9 种接口 + 通用字段搜索
- ✅ OpenAI/Claude/Google 全部接入
- ✅ ProviderSetting 强制 apiKey/baseUrl 抽象属性

### 系统工具页面
- ✅ CardGroup + ListItem + Switch 风格
- ✅ 27 个开关分 6 组（设备信息/设备控制/通知交互/媒体文件/应用管理/高级）
- ✅ TopAppBar + BackButton 与设置页一致

### 类型系统
- ✅ apiKey/baseUrl 升级为 ProviderSetting 抽象属性
- ✅ 补齐 6 个文件 LocalModel when 分支
- ✅ 所有 Provider 签名统一

---

## 卡住的问题

1. **源仓库设置页面依赖缺失** — SettingPreferencesUIPage、SettingThemePage 等需要的字符串资源和数据字段（chatCustomFontPath、bubbleOpacity 等）项目里没有，暂时删除
2. **7.3GB 模型下载** — 手机 WiFi 下载约需 20-30 分钟
3. **GGUF 格式** — llama.cpp 原生库无法自动打包（仓库 800MB+，无公开 CDN），需手动放 libllama.so

---

## 下一步计划

1. GitHub Actions 编译验证当前代码
2. 补全源仓库缺失的字符串资源和数据字段，恢复完整设置页面
3. 深入了解项目结构，确保本地模型功能完整可用
4. 考虑支持更多 ONNX 模型格式

---

## 踩过的坑

| 坑 | 解决 |
|----|------|
| data class 不能继承 data class | LocalModel 改为独立子类 |
| ONNX Result 遍历方式错误 | 用 iterator 代替 result.get(0).get() |
| flow{} 内不能嵌套 withContext | 改为 callbackFlow + launch(Dispatchers.IO) |
| 虚构 HuggingFace URL 导致下载无效文件 | 改为真实验证过的 URL |
| 源仓库页面复制后缺失字符串资源 | 回退页面，保留系统工具页 |
| CardGroup 非 Composable 直接调用 | 用 CardGroup { item{} } 语法正确使用 |
| `sw()` 辅助函数缺少 @Composable | 添加注解修复 |
| ProviderConfigure 加入 onInstantSave 后签名变化 | 所有调用点显式传参 |
| ClaudeProvider getBalance 破坏 listModels | 修复函数签名重叠 |
