# RikkaHub 交接文档 (HANDOFF)

> 最后更新：2026-08-08
> 仓库：`scottwilliamavery26071994-bot/rikkahub`（master 分支）
> 当前 HEAD：`26ba781`（已推送远程，构建进行中）
> 最近成功构建：`d1cf6ab`（success）

---

## 1. 当前任务

**核心任务**：移植 Kelivo 的 MCP 服务器功能到 RikkaHub Android 应用。
- 五种内置服务器：GitHub / Fetch / Files / Images / Memory（共 67 个工具）
- 技术栈：Kotlin + Jetpack Compose（仅 Android）
- 触发构建：推送到 master 后 GitHub Actions 自动构建 debug APK

**当前阶段**：
1. ✅ 代码优化收尾（已完成）
2. ✅ 构建失败修复（`d1cf6ab` 已通过）
3. ✅ 上下文超限提醒功能（已完成）
4. ✅ callTool OAuth 失效友好提示（已完成，`26ba781` 构建验证中）
5. ⏳ 等待 `26ba781` 构建结果 → 验证 APK 产物

---

## 2. 已完成内容

### 2.1 已修复的 Bug（均已提交）

| 提交 | 内容 |
|------|------|
| `1fb425d` | MCP 服务器统一显示：修复"有服务器却显示未找到"的空状态判断逻辑 |
| `126b3d8` | 修复 `ChatService.saveConversation` 无限递归导致栈溢出 + 消息发送不显示（落库后调用 `updateConversation` 更新 `session.state`） |
| `918539a` | 修复 `PluginWebViewPage.uriToBase64` 流未关闭的资源泄漏 |
| `8c96633` | 修复 `McpManager.kt` 重复 `clients[id]` 赋值导致编译错误 |

### 2.2 已实施的优化（`1d856ef`，9 项）

1. **异常日志信息泄露修复**：`e.message` → `e.javaClass.simpleName`（ChatService 日志）
2. **WebView XSS 防护增强**：⚠️ **实际未生效**（见问题 A8）——`PluginWebViewPage.kt` 仍为 `allowFileAccess = true`，之前脚本未找到目标代码，需手动补做
3. ~~数据库事务优化~~：⚠️ **已回滚**（见坑 #1：`@Transaction` 导致 Room KSP 失败，`64ff177` 移除）
4. **printStackTrace 替换为结构化日志**：`Log.e(TAG, "Operation failed", e)`（ChatService 3 处、GenerationHandler）
5. **协程超时管理**：`launchWithConversationReference` 添加 `withTimeout(30_000)` + `TimeoutCancellationException` 处理
6. **内存管理优化**：`McpManager.kt` 添加 `MAX_CLIENTS=20` + `cleanupInactiveClients()` 定期清理
7. **网络超时**：`githubHttpClient` 已有 `connectTimeout(15)/readTimeout(60)`，此前误加的 `Request.Builder.timeout()` 已移除
8. **TimeoutCancellationException 导入** + 保留 `withTimeoutOrNull` 导入（两者并存）

### 2.3 构建失败修复链（`51604f7` → `d1cf6ab`）

| 提交 | 修复内容 |
|------|----------|
| `51604f7` | Mutex/withLock 导入；实现 cleanupInactiveClients；移除 Request.Builder.timeout()；恢复 withTimeoutOrNull 导入 |
| `d1cf6ab` | **cleanupInactiveClients 改为 `suspend fun`**（内部调用 withLock/close 是 suspend）→ ✅ 构建成功 |

### 2.4 上下文超限提醒功能（`ac1c52d` + `4b514a3`）

**设计原则：发送后检测，不预判**（用户明确要求）。

1. **新增 `GenerationChunk.Reminder(text)` 类型**（GenerationHandler.kt:88）
   - 非对话内容，不写入对话历史
2. **输出截断提醒**（GenerationHandler.kt:241）：模型返回 `finish_reason == "length"` 时 emit Reminder → Toast「⚠️ 模型回复已达输出长度上限，当前回复可能不完整」
   - 不阻塞原有自动补全逻辑（`continue` 保留）
3. **服务端 context 错误提醒**（ChatService.kt onFailure）：错误信息含 `context length / maximum context / prompt is too long / too many tokens / input is too long / exceeded the maximum` 等关键词 → Toast「⚠️ 内容超过上下文窗口上限，建议压缩上下文或开新对话」
4. **UI 处理**（ChatService.kt 两处 collect）：Toast + `session.processingStatus` 展示，应用后台不影响对话内容
5. ~~发送前估算提醒~~：已按用户要求移除（不做"可能超限"预判）

### 2.5 callTool OAuth 失效检测（`26ba781`）

**背景**：用户发对话时遇到 `403 invalid client`（GitHub API 返回的非标准 JSON）。

**修复**（McpManager.kt `callTool`）：
1. 捕获调用异常，检测 OAuth 失效关键词（invalid client / invalid grant / invalid_token / token expired / 401 unauthorized 等）
2. 存在 refresh_token 时自动 `ensureFreshToken` 刷新令牌并**重试一次**
3. 仍失败返回**友好中文提示**：`⚠️ MCP 服务器「xxx」的 OAuth 授权已失效，请在 设置 → MCP 服务器 中重新授权后重试`
4. 非 OAuth 错误返回原始错误信息

---

## 3. 卡住的问题

### ✅ 已解决：构建持续失败（9 次失败 → 成功）

**根因链**（均已修复）：
1. `TimeoutCancellationExceptionOrNull` 不存在的类名（`7b1a324` 修复）
2. `@Transaction` 注解触发 Room KSP 全量依赖解析，`ConversationDAO` 引用的 `LightConversationEntity`（定义在 `ConversationRepository.kt`）导致 `MissingType`/循环依赖（`64ff177` 移除注解）
3. `Mutex` 未导入（`51604f7` 修复）
4. `cleanupInactiveClients()` 未实现（`51604f7` 实现）
5. `Request.Builder.timeout()` 方法不存在（`51604f7` 移除）
6. `withTimeoutOrNull` 导入被误删（`51604f7` 恢复）
7. `cleanupInactiveClients` 内部调用 suspend 函数但方法非 suspend（`d1cf6ab` 修复）→ **构建成功**

### ⏳ 验证中：`26ba781` 构建

- callTool OAuth 失效检测提交，构建 in_progress，等待确认。

### 🔍 观察项：403 "invalid client"（用户环境）

- 来源：发对话时调用 OAuth MCP 服务器，token 失效 + 刷新失败
- 代码已做友好提示 + 自动刷新重试
- 用户侧操作：设置 → MCP 服务器 → 重新授权 / 检查 clientId/clientSecret
- 注意：`x-oauth-scopes: repo, workflow` 的 PAT 验证正常，非 PAT 问题

---

## 3.5 当前代码问题清单（2026-08-08 全面扫描）

> 按严重程度排序。**A=功能缺陷/隐患，B=质量遗留，C=观察项。**

### A. 功能缺陷 / 隐患（建议优先处理）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| A1 | **`client.connect()` 在 try 块外** | McpManager.kt:238 | `if (client.transport == null) client.connect(...)` 若 connect 失败抛异常，不会被 callTool 的 OAuth 错误处理捕获，直接冒泡显示原始错误。应移入 try 内 |
| A2 | **内置 MCP 工具数显示不一致** | McpManager.kt `getBuiltinServerInfos()` / `getBuiltinServerTools()` | UI 宣称 5 个服务器（GitHub/Fetch/Files/Images/Memory，toolCount 4/3/2/4），但实际工具全在 `buildGitHubTools` 一个函数里（含 Fetch@2052、Images@2149），`githubTools.size` 是全部工具数 → GitHub 卡片工具数显示重复；且 `githubMcpEnabled=false` 时 GitHub 工具不可用但 Fetch/Images 仍可用（不一致） |
| A3 | **`ConversationDAO` ↔ `ConversationRepository` 循环引用** | ConversationDAO.kt:17 / ConversationRepository.kt:610 | `LightConversationEntity` 定义在 Repository 底部，DAO 引用它。**后果：不能加 `@Transaction`**（Room KSP 报 MissingType）。若未来需要事务注解，必须先把该类型抽到独立文件 |
| A4 | **群聊生成无 context 超限检测** | ChatService.kt 群聊 onFailure（~1434 行） | 主对话 onFailure 已加 context overflow 关键词检测（1129 行），群聊 onFailure 只有 `Log.e`，未检测/未提醒 |
| A5 | **OAuth 刷新失败静默** | McpManager.kt:907 | `ensureFreshToken` 刷新失败仅 `Log.w` 后返回旧 config，不更新状态、不提示用户；用户下次调用工具仍失败，只能靠 callTool 兜底（A1 修复后才会走到） |
| A6 | **"错误处理增强"（SocketException 重试）未生效** | ChatService.kt sendMessage catch | 此前脚本替换均提示"未找到目标代码"，实际 catch 结构不同，该优化从未真正实现。如需要需手动实现 |
| A7 | **`rikkahub-optimizations.zip`（44MB）混入 git 历史** | 提交 `7b1a324` | 曾误 `git add -A` 提交，当前 master 历史仍包含。工作区文件仍在。建议从历史中清除（git filter-branch / BFG）并加入 .gitignore |
| A8 | **WebView XSS 防护未生效** | PluginWebViewPage.kt:467 | 文档声称做了 `setAllowFileAccess(false)` 等 3 项防护，实际代码仍是 `allowFileAccess = true`（仅 mixedContentMode=NEVER_ALLOW 存在）。之前修改脚本未找到目标代码，需手动补做 |

### B. 代码质量遗留（优化未彻底）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| B1 | **printStackTrace 残留 51 处** | 全项目（核心 AI/MCP 8 处） | GenerationHandler.kt:364、McpManager.kt:140/147/415/498/550/659/741 等；其余分布在 UI/工具类。均未替换为结构化日志 |
| B2 | **e.message 日志泄露多处** | McpManager.kt:398/681/907/951、RequestLoggingInterceptor.kt:42/68、BrightnessTool.kt 等 | 日志直接输出异常 message，可能泄露路径/URL/请求体等敏感信息 |
| B3 | **runBlocking 12 处** | CameraTool:52、ExploreNearbyTool:91/113、WorkspaceDocumentsProvider:42、PluginSandbox:671、ChatService:47、HighlightCodeBlock:683 等 | 部分可能在主线程/IO 线程阻塞，注意 ANR 风险；Provider 回调场景可用但需评估 |
| B4 | **Thread.sleep 1 处** | SshTool.kt:449 | `try { Thread.sleep(50) }` 阻塞线程，应改 delay（如可 suspend） |
| B5 | **`clients.size` 锁外读取** | McpManager.kt:371（addClient 中） | `if (clients.size > MAX_CLIENTS * 0.8)` 在 `clientsMutex.withLock` 外读取，有轻微并发竞态（不影响编译，但 size 可能读到中间态） |

### C. 观察项 / 环境问题

| # | 问题 | 说明 |
|---|------|------|
| C1 | 用户环境 403 "invalid client" | OAuth 凭据失效，代码已加自动刷新重试 + 友好提示（26ba781），需实测验证 |
| C2 | GitHub API 偶发 403 非标准 JSON | 响应 `{ code: 403, message: "invalid client" }`（key 无引号），工具/客户端解析时注意容错；多为瞬时或 OAuth client 配置问题 |
| C3 | 沙箱连 GitHub 443 不稳定 | push 偶发超时，重试或 rebase 后 push |

---

## 4. 下一步计划

1. **[进行中] 确认 `26ba781` 构建通过**
   - 通过 → 下载 APK 产物验证
   - 失败 → 下载日志定位（注意 suspend / 类型推断 / import 三类高频问题）
2. **修复 A 类问题（优先）**：
   - A1：`client.connect()` 移入 try 块
   - A2：内置工具数统计修正（区分 GitHub/Fetch/Images）
   - A8：WebView `setAllowFileAccess(false)` 等防护补做
   - A4：群聊 onFailure 补 context 检测
   - A7：清理 ZIP 出 git 历史 + 加 .gitignore
3. **清理 B 类问题（低优先级）**：printStackTrace → Log.e、e.message 脱敏、Thread.sleep → delay
4. **验证功能**：
   - MCP 服务器列表显示（内置 5 个 + 外部）
   - 消息发送正常（saveConversation 递归修复）
   - 上下文超限提醒：构造超长对话触发 finish_reason=length
   - OAuth 失效提示：失效 MCP 服务器调用工具

---

## 5. 踩过的坑（重要经验）

1. **⚠️ `@Transaction` 注解触发 Room KSP 全量依赖解析**
   - DAO 引用跨包类型（定义在 Repository 层、存在循环依赖）时，加 `@Transaction` 会报 `MissingType` / `RoomKspProcessor was unable to process`。
   - `@Insert/@Update/@Delete` 默认已事务化，**无需重复加 @Transaction**。
   - 若确需事务注解：先把 `LightConversationEntity` 抽到独立文件消除循环引用。

2. **⚠️ 误写不存在的导入类**
   - `TimeoutCancellationExceptionOrNull` 不存在 → 应为 `kotlinx.coroutines.TimeoutCancellationException`；且 `withTimeoutOrNull` 是另一个独立导入，**两者并存**（代码中 30s 超时用 withTimeout+catch，5s join 用 withTimeoutOrNull）。

3. **⚠️ suspend 函数的传染性**
   - 调用 `Mutex.withLock { }` / `client.close()`（均为 suspend）的方法，自身必须是 `suspend fun`。`cleanupInactiveClients` 曾因漏加 suspend 导致构建失败。

4. **⚠️ OkHttp 无 `Request.Builder.timeout()`**
   - OkHttp 超时只能设置在 `OkHttpClient.Builder()`（connectTimeout/readTimeout）或 `Call.timeout()`。在 Request.Builder 上调 timeout 会 `Unresolved reference`。

5. **⚠️ 脚本化替换前先确认目标代码真实存在**
   - "错误处理增强"（SocketException 重试）多次脚本替换均提示"未找到目标代码"→ 实际 catch 结构不同，未生效，无需强求。

6. **⚠️ 网络环境不稳定（沙箱连 GitHub 443 经常超时）**
   - push 失败先看是网络还是 rejected；rejected 用 `git pull --rebase` 再 push，**不要 force push**。

7. **⚠️ 分支多次 reset --hard 导致分叉**
   - 回滚优先 `git revert`；reset --hard 后立即 `git fetch && git reset --hard origin/master` 对齐。

8. **⚠️ GitHub API 403 "invalid client" 不一定是代码问题**
   - PAT 验证（`repo, workflow` 权限）正常；该错误多为 OAuth 授权流程中 client 凭据失效，需在应用内重新授权。

9. **⚠️ 不要向仓库提交 ZIP 等打包产物**
   - `rikkahub-optimizations.zip` 曾误 add，后续 `git reset --hard` 已清除；提交前检查文件清单。

10. **⚠️ GitHub Actions 日志下载方式**
    - `curl -L -H "Authorization: Bearer <TOKEN>" "…/actions/runs/<RUN_ID>/logs" -o logs.zip`（需 `-L` 跟随 302 重定向）；解压后看 `Release_Build/7_Build Release APK.txt` 中的 `e: file:` 行。

---

## 6. 关键文件索引

| 文件 | 说明 |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpManager.kt` | MCP 管理器（并发锁、连接池、OAuth 授权/刷新、callTool） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthClient.kt` | MCP OAuth 2.1 客户端（DCR + PKCE + 令牌刷新） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpConfig.kt` | MCP 配置（含 clientId/clientSecret 字段） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/GitHubMcpTools.kt` | GitHub 工具（PAT 认证，connectTimeout 15s） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` | 生成处理器（GenerationChunk.Reminder、finish_reason=length 检测） |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 聊天服务（消息发送、context 错误检测、Reminder 展示） |
| `app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt` | 会话 DAO（引用 LightConversationEntity，勿加 @Transaction） |
| `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt` | 会话仓库（含 LightConversationEntity 定义，~610 行） |
| `.github/workflows/build-apk.yml` | CI 构建工作流（Android Build Workflow） |

---

## 7. 构建/提交速查

```bash
# 查看最新构建状态
curl -s -H "Authorization: Bearer <TOKEN>" \
  "https://api.github.com/repos/scottwilliamavery26071994-bot/rikkahub/actions/runs?per_page=5"

# 下载构建日志（zip，需 -L 跟随重定向）
curl -s -L -H "Authorization: Bearer <TOKEN>" \
  "https://api.github.com/repos/scottwilliamavery26071994-bot/rikkahub/actions/runs/<RUN_ID>/logs" -o logs.zip
unzip -o logs.zip -d logs && grep -E "e: file|FAILURE" "logs/Release_Build/7_Build Release APK.txt"

# 推送（网络不稳时先 rebase）
git pull --rebase origin master && git push origin master

# 分支分叉对齐
git fetch origin && git reset --hard origin/master
```
