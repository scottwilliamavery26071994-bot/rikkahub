# HANDOFF.md — RikkaHub 项目状态

> 版本：2.4.5 | 最后更新：2026-08-08

---

## 当前任务

- ✅ GitHub 代码分析 Agent（内置 MCP）—— 已完成代码编写，待编译验证
- 等待 GitHub Actions 编译验证
- 发行版 v2.4.5 已发布到 GitHub Releases

---

## 已完成的内容

### 一、聊天界面恢复
- 从 `rikkahub/rikkahub` 源仓库恢复原始 ChatList.kt (849行) / ChatPage.kt (827行) / ChatVM.kt (353行)
- 添加 ChatFont.kt 字体支持
- 适配所有回调函数签名

### 二、本地模型（已删除 🗑️）
- 完整移除：LocalModel sealed 子类、5 个 localmodel 源文件、ProviderConfigure UI、DI 注册、所有 when 分支引用
- 净减少 1049 行代码

### 三、三项强制兜底

| 能力 | 不支持时 |
|------|----------|
| 工具调用 | `<tool_call>` 提示词式 |
| 推理 | `<thinking>` 提示词式 + 中文强制 |
| 上下文超限 | 自动压缩 (保留最近6条) + 提示重试 |

### 四、余额自动检测
- BalanceAutoDetector.kt — 9 种接口 + 通用字段搜索
- OpenAI / Claude / Google 全部接入

### 五、系统工具页面（已恢复完整版 ✅）
- SettingSystemToolsPage.kt — 1289 行，30+ 工具卡片
- 每组带图标/权限检测/详细说明
- 安全提示 / 后台保活 / SAF 文件夹 / 位置服务+高德 API / 通知服务 /
  应用统计 / 探索周边 / Supabase 同步 / 相机 / Gadgetbridge / 闹钟 /
  定时器 / 电量 / 音乐控制 / 短信 / 手电筒 / Toast / 震动 / 亮度 /
  音量 / WiFi / 电话 / 分享 / 壁纸 / 唤醒屏幕 / 媒体扫描 / 发送通知 /
  存储信息 / 应用切换 / App 锁定 / 指纹验证
- 修复 Supabase 字段损坏（supabaseEnabled / supabaseUrl）

### 六、偏好设置页面组（已恢复 ✅）
- SettingPreferencesPage.kt — 偏好主页
- SettingPreferencesThemePage.kt — 主题偏好（动态色/AMOLED 暗色）
- SettingPreferencesGeneralPage.kt — 常规（交互/滚动/输入/TTS）
- SettingPreferencesUIPage.kt — UI（消息展示/字体/代码块/LaTeX/自定义字体）
- SettingPreferencesNotificationPage.kt — 通知

### 七、自定义主题管理（已恢复 ✅）
- SettingThemePage.kt — 创建/编辑/导入/导出 JSON 主题
- CustomThemeButton.kt — 主题预览组件

### 八、模型提示词编辑（已恢复 ✅）
- SettingModelPromptPage.kt — 各功能提示词（标题/翻译/建议/OCR/压缩）

### 九、五子棋升级 ✅
- 双模式：对战模式 + 残局破解
- 残局模式：自由摆放/擦除棋子，选求解方，Top-5 候选落子（攻防评分+高亮）
- 拍照识别：15×15 网格亮度采样，自动识别真实棋盘
- GomokuGame 引擎扩展：solveEndgame() / recognizeBoard() / checkAnyWin()

### 十、设置页重组
- SettingPage.kt — 合并上游分组 + 保留 fork 独有入口
- 通用设置：颜色模式 → 偏好设置 → 插件管理 → 安全设置 → 助手 → 扩展
- 模型与服务：默认模型 → 提供商 → 搜索 → 语音 → MCP → Web →
  系统工具 → 微信 Bot → API 探索器 → 五子棋 → 工具箱 → 工作流 → 健康数据

### 十一、其他
- 版本号：1.0 → 2.4.5（versionCode 20405）
- GitHub 发行版 v2.4.5 已发布，旧版 v0.1.0 已删除
- 关于页面 GitHub 链接已修正为本仓库
- 火车查询（RailGo）已删除
- 清理 4 个上游迁移遗留的损坏 .bak 文件
- 新增 46 条中/英/繁字符串资源

### 十二、GitHub 代码分析 Agent（内置 MCP）🔍

**新增文件：**
- `GitHubAnalyzerTools.kt`（~800行）— 6 个内置分析工具

**修改文件：**
- `McpManager.kt` — `getBuiltinServerTools()` 注册分析工具 + `getBuiltinServerInfos()` 新增"GitHub 代码分析 Agent"卡片

**6 个 MCP 工具：**

| 工具 | 功能 |
|------|------|
| `repo_quick_scan` | 一键扫描仓库：基本信息+README+目录树+语言+提交+CI+依赖文件列表 |
| `scan_security_patterns` | 安全漏洞扫描：硬编码密钥/SQL注入/XSS/命令注入/弱加密/不安全配置 |
| `scan_bug_patterns` | Bug 模式检测：空指针/空catch/eval/var滥用/TODO未解决等 |
| `analyze_dependency_file` | 依赖风险分析：支持 9 种包管理文件，内置已知漏洞数据库 |
| `generate_fix_suggestion` | 修复建议模板生成 |
| `create_analysis_report` | 结构化分析报告生成 + 询问用户是否修复 |

**安全漏洞规则库（17 条）：** Secrets(6) + Injection(4) + Crypto(3) + Config(4)
**Bug 检测规则库（10 条）：** Kotlin(4) + Python(3) + JS(3) + 通用(2)

---

## 卡住的问题

1. **Material3 版本差异** — 上游用 `1.5.0-alpha25`（支持 SheetValue），
   我们用 `1.5.0-alpha19`。SettingThemePage 已改为 `rememberModalBottomSheetState` 兼容方案。
2. **RouteActivity.kt 需谨慎编辑** — sed 批量删除易损坏结构（孤立括号/重复注解），
   已两次修复。后续建议手动精确编辑。

---

## 下一步计划

1. ✅ 等待编译验证通过
2. 如需更多上游功能，按需迁移

---

## 踩过的坑

| 坑 | 解决 |
|----|------|
| data class 不能继承 data class | 改为独立子类（后删除） |
| ONNX Result 遍历方式错误 | 用 iterator 代替 result.get(0).get() |
| flow{} 内不能嵌套 withContext | 改为 callbackFlow + launch(IO) |
| 虚构 HuggingFace URL | 改为真实验证过的 URL |
| 源仓库页面缺失字符串资源 | 已补全 46 条，8 页面全部恢复 |
| CardGroup 非 Composable 直接调用 | 用 CardGroup { item{} } 语法 |
| sw() 辅助函数缺少 @Composable | 添加注解 |
| ProviderConfigure 签名变化 | 所有调用点显式传参 |
| ClaudeProvider getBalance 破坏 listModels | 修复函数签名重叠 |
| getByKey 在 JsonElement/JsonObject 用法 | 先转 JsonObject 再调用 |
| 编译错误反复出现 | 增量修复，逐个提交验证 |
| 本地模型功能完整移除 | 删除 5 源文件 + 10 文件引用 |
| 系统工具页 Supabase 字段全损 | .bak 文件 5 处损坏，修复后恢复 |
| SettingPage 覆盖丢失 fork 入口 | 补回 10 个独有导航项 |
| FileFolders.FONTS 缺失 | FilesManager.kt 添加常量 |
| Material3 API 不兼容 | rememberBottomSheetState → ModalBottomSheetState |
| sed 删除 RailGo 遗留孤立括号 | 手动删除 2 个多余 `}` |
| sed 删除遗留重复 @Serializable | 手动删除重复注解 |
