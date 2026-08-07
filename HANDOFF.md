# RikkaHub 交接文档 (HANDOFF)

> 最后更新：2026-08-08
> 仓库：`scottwilliamavery26071994-bot/rikkahub`（master 分支）
> 当前 HEAD：`64ff177`（已推送远程，等待 Actions 构建结果）

---

## 1. 当前任务

**核心任务**：移植 Kelivo 的 MCP 服务器功能到 RikkaHub Android 应用。
- 五种内置服务器：GitHub / Fetch / Files / Images / Memory
- 共 67 个工具
- 技术栈：Kotlin + Jetpack Compose（仅 Android）
- 触发构建：推送到 master 后 GitHub Actions 自动构建 debug APK

**当前阶段**：代码优化收尾 → 解决构建失败 → 验证产物。

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

1. **异常日志信息泄露修复**：`e.message` → `e.javaClass.simpleName`
   - 文件：`ChatService.kt`（auto compress / generateTitle 日志）
2. **WebView XSS 防护增强**：`PluginWebViewPage.kt` 添加 `setAllowFileAccess(false)` 等 3 项
3. **数据库事务优化**：`ConversationDAO.kt` 为 insert/update/delete/deleteById 添加 `@Transaction`
4. **printStackTrace 替换为结构化日志**：`Log.e(TAG, "Operation failed", e)`
   - 文件：`ChatService.kt`（3 处）、`GenerationHandler.kt`
5. **协程超时管理**：`launchWithConversationReference` 添加 `withTimeout(30_000)` + `TimeoutCancellationException` 处理
6. **内存管理优化**：`McpManager.kt` 添加 `MAX_CLIENTS` 上限 + 定期清理不活跃连接
7. **网络请求超时优化**：`GitHubMcpTools.kt` 设置 `timeout(Duration.ofSeconds(30))`
8. **TimeoutCancellationException 导入**
9. **错误处理增强**（部分未生效，见下文坑 #4）

---

## 3. 卡住的问题（正在解决）

### ⚠️ 主问题：GitHub Actions 构建持续失败

**现象**：从 `0949f0f` 之后的所有构建均失败（最后一个成功构建：`918539a`）。

**最新构建错误（run #356，提交 `7b1a324`）**：
```
e: [ksp] /home/runner/work/rikkahub/rikkahub/app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt:20:
[MissingType]: Element 'me.rerere.rikkahub.data.db.dao.ConversationDAO' references a type that is not present

e: [ksp] androidx.room.RoomKspProcessor was unable to process 'me.rerere.rikkahub.data.db.AppDatabase'
because not all of its dependencies could be resolved.
Check for compilation errors or a circular dependency with generated code.

FAILURE: Build failed with an exception.
Execution failed for task ':app:kspReleaseKotlin'.
> KSP failed with exit code: PROCESSING_ERROR
```

**根因分析**：
- `ConversationDAO.kt` 第 17 行 import 了 `me.rerere.rikkahub.data.repository.LightConversationEntity`
- 该类型定义在 `ConversationRepository.kt` 底部（非独立文件）
- `ConversationRepository` 又依赖 `ConversationDAO` → **DAO ↔ Repository 循环引用**
- 添加 `@Transaction` 注解后，Room KSP 处理器必须解析 DAO 的全部依赖类型，循环依赖导致 `MissingType`

**当前处理**：
- 已提交 `64ff177`：**移除 `ConversationDAO.kt` 中全部 4 个 `@Transaction` 注解**（`@Insert/@Update/@Delete` 本身默认就是事务性的，移除无功能损失）
- 已推送远程，**正在等待 Actions 构建结果确认**

**备选方案（若仍失败）**：
- 将 `LightConversationEntity` 从 `ConversationRepository.kt` 抽离到独立文件（如 `data/repository/LightConversationEntity.kt`），消除循环引用
- 或在 DAO 中改用 `ConversationEntity` 查询再映射

---

## 4. 下一步计划

1. **[进行中] 确认 `64ff177` 构建是否通过**
   - 通过 → 进入第 2 步
   - 失败 → 执行备选方案（抽取 LightConversationEntity）并重新构建
2. **验证已推送的优化**：确认 APK 正常构建后，检查 MCP 服务器列表显示、消息发送功能
3. **确认错误处理增强优化**（当前未生效，见坑 #4）：检查 `ChatService.sendMessage` 的 catch 块是否被应用
4. **补充剩余优化项**（如果构建稳定后需要）：WebView 安全配置若未生效需重新确认
5. **清理本地垃圾文件**：`rikkahub-optimizations.zip` 曾被误提交到 git 历史，注意后续不要带入

---

## 5. 踩过的坑（重要经验）

1. **⚠️ `@Transaction` 注解会触发 Room KSP 全量依赖解析**
   - 在 DAO 引用跨包类型（尤其定义在 Repository 层、存在循环依赖）时，添加 `@Transaction` 会导致 `MissingType` / `RoomKspProcessor was unable to process`。
   - **教训**：Room DAO 中尽量只引用 Entity / 独立定义的类型；`@Insert/@Update/@Delete` 默认已事务化，无需重复加 `@Transaction`。

2. **⚠️ 误写不存在的导入类**
   - `TimeoutCancellationExceptionOrNull`（不存在）→ 应为 `kotlinx.coroutines.TimeoutCancellationException`
   - 两次提交 `7b1a324` / `1d856ef` 都是修这个，浪费了两次构建。
   - **教训**：写 import 前确认类名真实存在（`withTimeoutOrNull` 存在，但 `TimeoutCancellationExceptionOrNull` 不存在）。

3. **⚠️ 网络环境不稳定（沙箱连 GitHub 443 经常超时）**
   - 现象：`Failed to connect to github.com port 443`，push 失败。
   - 处理：多次重试；若报 `non-fast-forward` 先 `git pull --rebase origin master` 再 push。
   - **教训**：push 失败先看是网络还是 rejected；rejected 用 rebase 而不是 force push（避免覆盖他人提交）。

4. **⚠️ 错误处理增强优化可能未生效**
   - `ChatService.sendMessage` 的 catch 块优化（SocketException 重试）在多次脚本替换中均提示"未找到目标代码"，实际 catch 结构可能不是预期的样子。
   - **教训**：脚本化替换前先确认目标代码真实存在；找不到就说明结构不同，需要手动定位。

5. **⚠️ 分支多次 reset --hard 导致分叉**
   - 反复 `git reset --hard` 到旧提交 + 新提交，导致本地与 `origin/master` 分叉，push 被拒。
   - 处理：`git fetch origin && git reset --hard origin/master` 对齐。
   - **教训**：回滚用 `git revert` 更安全；reset --hard 后要立即同步远程。

6. **⚠️ 不要向仓库提交 ZIP 等打包产物**
   - `rikkahub-optimizations.zip`（约 80MB）曾被误 `git add -A` 提交，若后续操作不当可能进入历史。
   - **教训**：`git add` 前确认文件清单；大文件/产物应加入 `.gitignore`。

---

## 6. 关键文件索引

| 文件 | 说明 |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpManager.kt` | MCP 管理器（并发锁、连接池、内置服务器信息） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/BuiltinMcpTools.kt` | 内置 MCP 工具构建（GitHub/Fetch/Files/Memory） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/GitHubMcpTools.kt` | GitHub 工具（67 个中的大部分） |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 聊天服务（消息发送、会话保存） |
| `app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt` | 会话 DAO（近期 KSP 错误点） |
| `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt` | 会话仓库（含 LightConversationEntity 定义） |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMcpPage.kt` | MCP 设置页（内置+外部服务器统一显示） |
| `.github/workflows/build-apk.yml` | CI 构建工作流（Android Build Workflow） |

---

## 7. 构建/提交速查

```bash
# 查看最新构建状态
curl -s -H "Authorization: Bearer <TOKEN>" \
  "https://api.github.com/repos/scottwilliamavery26071994-bot/rikkahub/actions/runs?per_page=3"

# 下载构建日志（zip）
curl -s -L -H "Authorization: Bearer <TOKEN>" \
  "https://api.github.com/repos/scottwilliamavery26071994-bot/rikkahub/actions/runs/<RUN_ID>/logs" -o logs.zip

# 推送（网络不稳时先 rebase）
git pull --rebase origin master && git push origin master
```
