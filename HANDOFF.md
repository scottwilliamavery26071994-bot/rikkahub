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
2. **WebView XSS 防护增强**：`PluginWebViewPage.kt` 添加 `setAllowFileAccess(false)` 等 3 项
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

## 4. 下一步计划

1. **[进行中] 确认 `26ba781` 构建通过**
   - 通过 → 下载 APK 产物验证
   - 失败 → 下载日志定位（注意 suspend / 类型推断 / import 三类高频问题）
2. **验证功能**：
   - MCP 服务器列表显示（内置 5 个 + 外部）
   - 消息发送正常（saveConversation 递归修复）
   - 上下文超限提醒：构造超长对话触发 finish_reason=length
   - OAuth 失效提示：失效 MCP 服务器调用工具
3. **收尾清理**：
   - `rikkahub-optimizations.zip`（约 80MB）曾误 add，确认不在最终提交中（已确认 git log 无此文件）
   - 检查 `.gitignore` 是否有必要补充
4. **可选优化**（若需要）：
   - `ConversationDAO` 与 `LightConversationEntity` 的循环引用长期存在，若未来要加回事务注解，需先抽离该类型到独立文件

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
