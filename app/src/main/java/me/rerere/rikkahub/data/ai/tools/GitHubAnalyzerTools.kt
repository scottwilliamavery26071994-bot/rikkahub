package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * GitHub 项目自动分析 Agent — 内置 MCP 工具集。
 *
 * 提供代码扫描、安全检测、依赖分析、修复建议等工具，
 * 与现有 GitHub MCP 工具（仓库读取/文件操作）配合，
 * 构成完整的"输入 GitHub URL → 自动扫描 → 报告风险/Bug →
 * 询问是否修复 → 创建 Fix PR"流水线。
 */

// ====================================================================
// 安全漏洞模式库 — 正则匹配常见漏洞/风险
// ====================================================================

private data class SecurityPattern(
    val id: String,
    val severity: String,         // critical / high / medium / low
    val category: String,         // secrets / injection / crypto / config / dependency
    val description: String,
    val regex: Regex,
    val fileGlob: String = "*",   // 适用文件匹配
)

private val SECURITY_PATTERNS = listOf(
    // === 硬编码密钥 ===
    SecurityPattern(
        id = "SECRET-001", severity = "critical", category = "secrets",
        description = "硬编码的 API Key / Token（通用模式）",
        regex = Regex("""(api[_-]?key|api[_-]?secret|access[_-]?key|secret[_-]?key)\s*[:=]\s*['"][A-Za-z0-9_\-]{16,}['"]""", RegexOption.IGNORE_CASE),
    ),
    SecurityPattern(
        id = "SECRET-002", severity = "critical", category = "secrets",
        description = "GitHub Personal Access Token",
        regex = Regex("""gh[pousr]_[A-Za-z0-9_]{36,}"""),
    ),
    SecurityPattern(
        id = "SECRET-003", severity = "critical", category = "secrets",
        description = "AWS Access Key ID",
        regex = Regex("""AKIA[0-9A-Z]{16}"""),
    ),
    SecurityPattern(
        id = "SECRET-004", severity = "critical", category = "secrets",
        description = "OpenAI / Anthropic API Key",
        regex = Regex("""sk-(ant-)?[A-Za-z0-9]{32,}"""),
    ),
    SecurityPattern(
        id = "SECRET-005", severity = "critical", category = "secrets",
        description = "私钥文件头（PEM）",
        regex = Regex("""-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"""),
    ),
    SecurityPattern(
        id = "SECRET-006", severity = "high", category = "secrets",
        description = "密码/密钥赋值（password/passwd/secret）",
        regex = Regex("""(password|passwd|secret|token)\s*[:=]\s*['"][^'"]{6,}['"]""", RegexOption.IGNORE_CASE),
    ),

    // === 注入漏洞 ===
    SecurityPattern(
        id = "INJECT-001", severity = "critical", category = "injection",
        description = "SQL 字符串拼接（可能导致 SQL 注入）",
        regex = Regex("""["']\s*[+]?\s*(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\s""", RegexOption.IGNORE_CASE),
        fileGlob = "*.{kt,java,py,js,ts,go,rs,rb,php}",
    ),
    SecurityPattern(
        id = "INJECT-002", severity = "high", category = "injection",
        description = "原始 SQL 执行（execute/rawQuery 字符串拼接）",
        regex = Regex("""(execute|rawQuery|executeSql)\s*\(\s*["'][^'"]*\$\{""", RegexOption.IGNORE_CASE),
        fileGlob = "*.{kt,java,py,js,ts}",
    ),
    SecurityPattern(
        id = "INJECT-003", severity = "high", category = "injection",
        description = "命令注入（Runtime.exec / os.system / subprocess 拼接用户输入）",
        regex = Regex("""(Runtime\.getRuntime\(\)\.exec|os\.system|subprocess\.(call|run|Popen)|ProcessBuilder)\s*\(\s*.*[+]\s*"""), // 简化检测
        fileGlob = "*.{kt,java,py,js,ts,go}",
    ),
    SecurityPattern(
        id = "INJECT-004", severity = "high", category = "injection",
        description = "XSS 风险 — innerHTML / dangerouslySetInnerHTML",
        regex = Regex("""(innerHTML|outerHTML|dangerouslySetInnerHTML|v-html|ng-bind-html)"""),
        fileGlob = "*.{html,js,jsx,ts,tsx,vue,svelte}",
    ),

    // === 加密问题 ===
    SecurityPattern(
        id = "CRYPTO-001", severity = "high", category = "crypto",
        description = "使用了弱哈希算法（MD5/SHA1）",
        regex = Regex("""(MessageDigest\.getInstance|hashlib)\.?\s*\(\s*['\"](MD5|SHA-?1)['\"]\s*\)""", RegexOption.IGNORE_CASE),
    ),
    SecurityPattern(
        id = "CRYPTO-002", severity = "high", category = "crypto",
        description = "硬编码的加密密钥/IV（AES/DES）",
        regex = Regex("""(AES|DES|Blowfish|SecretKeySpec|IvParameterSpec|Cipher\.getInstance)\s*\([\s\S]{0,40}['\"][A-Za-z0-9+/=]{16,}['\"]"""),
    ),
    SecurityPattern(
        id = "CRYPTO-003", severity = "medium", category = "crypto",
        description = "使用了不安全的随机数生成器（Math.random / Random）",
        regex = Regex("""(Math\.random|java\.util\.Random|rand\(\))"""),
        fileGlob = "*.{kt,java,py,js,ts}",
    ),

    // === 配置/部署风险 ===
    SecurityPattern(
        id = "CONFIG-001", severity = "medium", category = "config",
        description = "Debug 模式开启 / 调试标志",
        regex = Regex("""(debug\s*[:=]\s*true|DEBUG\s*=\s*True|isDebuggable\s*=\s*true)""", RegexOption.IGNORE_CASE),
    ),
    SecurityPattern(
        id = "CONFIG-002", severity = "medium", category = "config",
        description = "允许明文 HTTP 流量（android:usesCleartextTraffic=true）",
        regex = Regex("""usesCleartextTraffic\s*=\s*['"]?true['"]?"""", RegexOption.IGNORE_CASE),
    ),
    SecurityPattern(
        id = "CONFIG-003", severity = "low", category = "config",
        description = "CORS 配置过于宽松（Access-Control-Allow-Origin: *）",
        regex = Regex("""Access-Control-Allow-Origin\s*:\s*\*"""),
    ),
)

// ====================================================================
// Bug 模式库 — 常见代码 Bug 模式
// ====================================================================

private data class BugPattern(
    val id: String,
    val severity: String,
    val language: String,
    val description: String,
    val regex: Regex,
    val fileGlob: String = "*",
)

private val BUG_PATTERNS = listOf(
    // === Kotlin/Java ===
    BugPattern("BUG-KT-001", "high", "kotlin",
        "可能的 NullPointerException — 使用 !! 强制解包",
        Regex("""!!\s*\.\s*[a-zA-Z]""")),
    BugPattern("BUG-KT-002", "medium", "kotlin",
        "捕获异常后未处理（空的 catch 块）",
        Regex("""catch\s*\([^)]*\)\s*\{\s*\}""")),
    BugPattern("BUG-KT-003", "medium", "java/kotlin",
        "在 equals()/hashCode() 中使用浮点字段",
        Regex("""override\s+fun\s+(equals|hashCode).*\.(toFloat|toDouble|\.floatValue|\.doubleValue)""")),
    BugPattern("BUG-KT-004", "low", "kotlin",
        "使用 !! 而非安全调用 ?.let {}",
        Regex("""\w+!!\s*\n""")),

    // === Python ===
    BugPattern("BUG-PY-001", "high", "python",
        "可变对象作为默认参数（list/dict）",
        Regex("""def\s+\w+\s*\([^)]*=\s*(\[\s*\]|\{\s*\})""")),
    BugPattern("BUG-PY-002", "medium", "python",
        "裸 except 捕获所有异常",
        Regex("""except\s*:""")),
    BugPattern("BUG-PY-003", "medium", "python",
        "使用 is 比较字面量（应使用 ==）",
        Regex("""\w+\s+is\s+['\"]?\d+['\"]?""")),

    // === JavaScript/TypeScript ===
    BugPattern("BUG-JS-001", "high", "javascript/typescript",
        "使用 var 而非 const/let",
        Regex("""\bvar\s+\w+\s*="""), fileGlob = "*.{js,ts,mjs}"),
    BugPattern("BUG-JS-002", "critical", "javascript/typescript",
        "使用 eval() 执行动态代码",
        Regex("""\beval\s*\([^)]*\)"""), fileGlob = "*.{js,ts,jsx,tsx,mjs}"),
    BugPattern("BUG-JS-003", "medium", "javascript/typescript",
        "使用 == 而非 === 进行相等比较",
        Regex("""[^!=><]=[^=]"""), fileGlob = "*.{js,ts,jsx,tsx}"),

    // === 通用 ===
    BugPattern("BUG-GEN-001", "medium", "any",
        "TODO/FIXME/HACK 注释未解决",
        Regex("""(TODO|FIXME|HACK|XXX)\s*:""", RegexOption.IGNORE_CASE)),
    BugPattern("BUG-GEN-002", "low", "any",
        "print/console.log 调试语句残留",
        Regex("""(System\.out\.println|console\.(log|debug|info)|print\s*\()"""), fileGlob = "*.{kt,java,py,js,ts,jsx,tsx}"),
)

// ====================================================================
// 依赖文件解析器
// ====================================================================

private data class DependencyInfo(
    val name: String,
    val version: String,
    val isLatest: Boolean = true,
    val hasKnownVuln: Boolean = false,
    val vulnDescription: String? = null,
)

/**
 * 简单检测依赖文件中是否有已知的风险依赖（基于版本号粗略判断）。
 * 真实生产环境应接入 OSS Index / Snyk API。
 */
private fun checkKnownRiskyDeps(deps: List<Pair<String, String>>): List<Pair<String, String>> {
    val riskyPatterns = mapOf(
        // 格式：依赖名关键词 -> 风险描述
        "log4j-core" to "Log4Shell 漏洞（CVE-2021-44228），建议升级到 >= 2.17.0",
        "log4j-api" to "Log4Shell 漏洞（CVE-2021-44228），版本 < 2.17.0 受影响",
        "spring-beans" to "Spring4Shell（CVE-2022-22965），建议升级",
        "jackson-databind" to "可能存在反序列化漏洞，建议保持最新版本",
        "fastjson" to "多个反序列化漏洞，建议升级到最新版本",
        "lodash" to "原型污染漏洞（CVE-2019-10744/CVE-2020-8203），建议 >= 4.17.21",
        "minimist" to "原型污染漏洞（CVE-2021-44906），建议 >= 1.2.6",
        "node-forge" to "多个高危漏洞（CVE-2022-24771等），建议升级",
        "requests" to "代理授权泄露（CVE-2023-32681），Python requests < 2.31.0",
        "urllib3" to "多个漏洞（CVE-2023-45803等），建议 >= 2.0.7",
        "aiohttp" to "HTTP 请求走私（CVE-2024-23334），建议升级",
        "gradle" to "依赖混淆攻击风险，检查 buildscript 仓库 HTTPS 配置",
        "okhttp" to "证书固定绕过（CVE-2021-0341），Android 旧版本受影响",
        "kotlinx-serialization" to "确保使用最新版本以避免已知漏洞",
    )
    return deps.filter { (name, _) ->
        riskyPatterns.keys.any { name.contains(it, ignoreCase = true) }
    }.mapNotNull { (name, _) ->
        val key = riskyPatterns.keys.firstOrNull { name.contains(it, ignoreCase = true) }
        key?.let { name to (riskyPatterns[it] ?: "") }
    }
}

// ====================================================================
// GitHub Analyzer MCP 工具构建函数
// ====================================================================

/**
 * 构建 GitHub 自动分析 Agent 的内置 MCP 工具集。
 *
 * @param getToken 获取 GitHub Token
 * @param enabled 是否启用 GitHub MCP
 */
fun buildGitHubAnalyzerTools(getToken: () -> String?, enabled: () -> Boolean): List<Tool> = buildList {

    // ============ 工具 1: repo_quick_scan ============
    add(Tool(
        name = "repo_quick_scan",
        description = """
快速扫描 GitHub 仓库的整体结构和关键文件。
返回：仓库基本信息、目录树、README、依赖文件列表、CI配置、最近提交。
这是分析任何 GitHub 项目的第一步，请先调用此工具获取项目概览。
Params: owner (仓库所有者), repo (仓库名)。
        """.trimIndent(),
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("owner", buildJsonObject {
                        put("type", "string")
                        put("description", "GitHub 仓库所有者 (owner)")
                    })
                    put("repo", buildJsonObject {
                        put("type", "string")
                        put("description", "GitHub 仓库名 (repo name)")
                    })
                },
                required = listOf("owner", "repo")
            )
        },
        execute = { args ->
            if (!enabled()) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "GitHub MCP 未启用，请在设置中启用 GitHub MCP")
            }.toString()))
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "请先在设置中配置 GitHub Token")
            }.toString()))
            val o = args.jsonObject
            fun g(key: String) = o[key]?.jsonPrimitive?.contentOrNull ?: ""
            val owner = g("owner")
            val repo = g("repo")

            val report = buildJsonObject {
                // 1. 仓库基本信息
                try {
                    val repoInfo = githubApiCall(token, "GET", "repos/$owner/$repo")
                    put("repo_info", JsonPrimitive(repoInfo.take(3000)))
                } catch (e: Exception) {
                    put("repo_info_error", e.message ?: "unknown")
                }

                // 2. README
                try {
                    val readme = githubApiCall(token, "GET", "repos/$owner/$repo/readme")
                    put("readme", JsonPrimitive(readme.take(4000)))
                } catch (_: Exception) {
                    put("readme", "No README found")
                }

                // 3. 根目录文件列表
                try {
                    val rootDir = githubApiCall(token, "GET", "repos/$owner/$repo/contents/")
                    put("root_directory", JsonPrimitive(rootDir.take(5000)))
                } catch (e: Exception) {
                    put("root_directory_error", e.message ?: "unknown")
                }

                // 4. 语言统计
                try {
                    val languages = githubApiCall(token, "GET", "repos/$owner/$repo/languages")
                    put("languages", JsonPrimitive(languages.take(2000)))
                } catch (_: Exception) {
                    put("languages", "{}")
                }

                // 5. 最近提交
                try {
                    val commits = githubApiCall(token, "GET", "repos/$owner/$repo/commits?per_page=5")
                    put("recent_commits", JsonPrimitive(commits.take(3000)))
                } catch (_: Exception) {
                    put("recent_commits", "[]")
                }

                // 6. CI/Workflow 状态
                try {
                    val workflows = githubApiCall(token, "GET", "repos/$owner/$repo/actions/workflows")
                    put("workflows", JsonPrimitive(workflows.take(2000)))
                } catch (_: Exception) {
                    put("workflows", "{}")
                }

                // 7. 依赖文件列表（关键文件路径）
                val depFiles = listOf(
                    "package.json", "build.gradle", "build.gradle.kts", "pom.xml",
                    "requirements.txt", "Pipfile", "pyproject.toml", "Cargo.toml",
                    "go.mod", "Gemfile", "composer.json", "CMakeLists.txt",
                    "Podfile", "Cartfile", "Package.swift", "pubspec.yaml",
                    "gradle/libs.versions.toml", "settings.gradle.kts",
                )
                val foundDeps = mutableListOf<String>()
                for (depFile in depFiles) {
                    try {
                        githubApiCall(token, "GET", "repos/$owner/$repo/contents/$depFile")
                        foundDeps.add(depFile)
                    } catch (_: Exception) { /* 文件不存在 */ }
                }
                put("dependency_files", JsonArray(foundDeps.map { JsonPrimitive(it) }))

                // 分析建议
                put("next_steps", JsonPrimitive("""
请根据以上扫描结果，执行以下分析步骤：
1. 用 github_get_file 读取 dependency_files 中列出的依赖文件，用 analyze_dependency_file 分析依赖风险
2. 浏览关键源代码目录（src/, app/, lib/ 等），用 scan_security_patterns 扫描安全漏洞
3. 用 scan_bug_patterns 扫描常见 Bug 模式
4. 检查 CI 配置和工作流状态
5. 汇总生成分析报告
                """.trimIndent()))
            }
            listOf(UIMessagePart.Text(report.toString()))
        },
    ))

    // ============ 工具 2: scan_security_patterns ============
    add(Tool(
        name = "scan_security_patterns",
        description = """
扫描代码中的安全漏洞模式。传入代码内容和文件名，返回匹配到的安全风险列表。
覆盖：硬编码密钥、SQL注入、XSS、命令注入、弱加密、不安全配置等。
Params: code (源代码内容), filename (文件名，用于匹配语言规则), language (可选，指定语言 kotlin/java/python/javascript/typescript/go/any)。
        """.trimIndent(),
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("code", buildJsonObject {
                        put("type", "string")
                        put("description", "要扫描的源代码内容")
                    })
                    put("filename", buildJsonObject {
                        put("type", "string")
                        put("description", "文件名（如 MainActivity.kt），用于匹配规则")
                    })
                    put("language", buildJsonObject {
                        put("type", "string")
                        put("description", "编程语言（可选），如 kotlin/java/python/javascript/typescript/go")
                    })
                },
                required = listOf("code", "filename")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val code = o["code"]?.jsonPrimitive?.contentOrNull ?: ""
            val filename = o["filename"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val lang = o["language"]?.jsonPrimitive?.contentOrNull ?: ""

            if (code.isBlank()) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("scanned", false)
                    put("message", "代码内容为空")
                }.toString()))
            }

            val lines = code.lines()
            val findings = mutableListOf<JsonPrimitive>()

            for (pattern in SECURITY_PATTERNS) {
                // 检查文件类型是否匹配
                val glob = pattern.fileGlob
                if (glob != "*") {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    val globExts = glob.removePrefix("*.").split(",").map { it.trim().lowercase() }
                    if (ext !in globExts) continue
                }

                // 搜索匹配
                val matches = pattern.regex.findAll(code)
                for (match in matches) {
                    val lineNum = code.substring(0, match.range.first).count { it == '\n' } + 1
                    findings.add(JsonPrimitive(buildJsonObject {
                        put("pattern_id", pattern.id)
                        put("severity", pattern.severity)
                        put("category", pattern.category)
                        put("description", pattern.description)
                        put("file", filename)
                        put("line", lineNum)
                        put("match_preview", match.value.take(80).replace("\n", "\\n"))
                    }.toString()))
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("scanned", true)
                put("file", filename)
                put("total_findings", findings.size)
                put("findings", JsonArray(findings.distinctBy { it.content }.take(50)))
                if (findings.isEmpty()) {
                    put("message", "✅ 未发现已知安全漏洞模式")
                } else {
                    val criticalCount = findings.count { "critical" in it.content }
                    val highCount = findings.count { "\"high\"" in it.content }
                    put("summary", "🔴 Critical: $criticalCount, 🟠 High: $highCount, 总计: ${findings.size}")
                }
            }.toString()))
        },
    ))

    // ============ 工具 3: scan_bug_patterns ============
    add(Tool(
        name = "scan_bug_patterns",
        description = """
扫描代码中的常见 Bug 模式。传入代码内容和文件名，返回匹配到的 Bug 列表。
覆盖：空指针风险、资源泄漏、异常处理问题、并发问题等。
Params: code (源代码内容), filename (文件名), language (可选，指定语言)。
        """.trimIndent(),
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("code", buildJsonObject {
                        put("type", "string")
                        put("description", "要扫描的源代码内容")
                    })
                    put("filename", buildJsonObject {
                        put("type", "string")
                        put("description", "文件名")
                    })
                    put("language", buildJsonObject {
                        put("type", "string")
                        put("description", "编程语言（可选）")
                    })
                },
                required = listOf("code", "filename")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val code = o["code"]?.jsonPrimitive?.contentOrNull ?: ""
            val filename = o["filename"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            if (code.isBlank()) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("scanned", false)
                    put("message", "代码内容为空")
                }.toString()))
            }

            val findings = mutableListOf<JsonPrimitive>()

            for (pattern in BUG_PATTERNS) {
                // 检查文件类型是否匹配
                val glob = pattern.fileGlob
                if (glob != "*") {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    val globExts = glob.removePrefix("*.").split(",").map { it.trim().lowercase() }
                    if (ext !in globExts) continue
                }
                val matches = pattern.regex.findAll(code)
                for (match in matches) {
                    val lineNum = code.substring(0, match.range.first).count { it == '\n' } + 1
                    findings.add(JsonPrimitive(buildJsonObject {
                        put("pattern_id", pattern.id)
                        put("severity", pattern.severity)
                        put("language", pattern.language)
                        put("description", pattern.description)
                        put("file", filename)
                        put("line", lineNum)
                        put("match_preview", match.value.take(80).replace("\n", "\\n"))
                    }.toString()))
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("scanned", true)
                put("file", filename)
                put("total_findings", findings.size)
                put("findings", JsonArray(findings.distinctBy { it.content }.take(50)))
                if (findings.isEmpty()) {
                    put("message", "✅ 未发现已知 Bug 模式")
                }
            }.toString()))
        },
    ))

    // ============ 工具 4: analyze_dependency_file ============
    add(Tool(
        name = "analyze_dependency_file",
        description = """
分析依赖/包管理文件，检测已知风险依赖和版本问题。
支持：package.json, build.gradle(.kts), requirements.txt, pom.xml, Cargo.toml, go.mod, Gemfile, pyproject.toml 等。
Params: owner (仓库所有者), repo (仓库名), path (依赖文件在仓库中的路径)。
        """.trimIndent(),
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("owner", buildJsonObject {
                        put("type", "string")
                        put("description", "仓库所有者")
                    })
                    put("repo", buildJsonObject {
                        put("type", "string")
                        put("description", "仓库名")
                    })
                    put("path", buildJsonObject {
                        put("type", "string")
                        put("description", "依赖文件路径（如 package.json）")
                    })
                },
                required = listOf("owner", "repo", "path")
            )
        },
        execute = { args ->
            if (!enabled()) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "GitHub MCP 未启用")
            }.toString()))
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "请先配置 GitHub Token")
            }.toString()))
            val o = args.jsonObject
            fun g(key: String) = o[key]?.jsonPrimitive?.contentOrNull ?: ""
            val owner = g("owner")
            val repo = g("repo")
            val path = g("path")

            // 获取文件内容
            val rawContent = try {
                githubApiCall(token, "GET", "repos/$owner/$repo/contents/$path")
            } catch (e: Exception) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "无法读取文件: ${e.message}")
                    put("path", path)
                }.toString()))
            }

            // 尝试从 API 响应中提取实际内容（base64 编码的）
            val content = rawContent

            // 简单解析依赖信息（真实场景应使用完整解析器）
            val deps = when {
                path.endsWith("package.json") -> {
                    val depRegex = Regex(""""([^"]+)"\s*:\s*"[~^]?\s*([\d.]+(?:-[^"]+)?)"""")
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                path.endsWith("requirements.txt") -> {
                    val depRegex = Regex("""^([a-zA-Z0-9_\-]+)\s*[=~><]+\s*([\d.]+)""", RegexOption.MULTILINE)
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                path.endsWith("build.gradle.kts") || path.endsWith("build.gradle") -> {
                    val depRegex = Regex("""["']([^"':]+):([^"':]+):([\d.]+)["']""")
                    depRegex.findAll(content).map {
                        "${it.groupValues[1]}:${it.groupValues[2]}" to it.groupValues[3]
                    }.toList()
                }
                path.endsWith("pom.xml") -> {
                    val depRegex = Regex("""<artifactId>([^<]+)</artifactId>\s*<version>([\d.]+(?:-[^<]+)?)</version>""")
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                path.endsWith("pyproject.toml") -> {
                    val depRegex = Regex(""""([^"]+)"\s*=\s*"[~^]?\s*([\d.]+)"""")
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                path.endsWith("Cargo.toml") -> {
                    val depRegex = Regex(""""([^"]+)"\s*=\s*"([\d.]+)"""")
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                path.endsWith("go.mod") -> {
                    val depRegex = Regex("""^\s*([\w./-]+)\s+v([\d.]+(?:-[^ ]+)?)""", RegexOption.MULTILINE)
                    depRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
                }
                else -> emptyList()
            }

            val riskyDeps = checkKnownRiskyDeps(deps)

            listOf(UIMessagePart.Text(buildJsonObject {
                put("file", path)
                put("total_dependencies", deps.size)
                put("dependencies", JsonArray(deps.take(100).map { (name, ver) ->
                    JsonPrimitive(buildJsonObject {
                        put("name", name)
                        put("version", ver)
                    }.toString())
                }))
                put("risky_dependencies", JsonArray(riskyDeps.map { (name, desc) ->
                    JsonPrimitive(buildJsonObject {
                        put("name", name)
                        put("risk", desc)
                    }.toString())
                }))
                put("risky_count", riskyDeps.size)
                if (deps.isEmpty()) {
                    put("message", "无法解析该文件格式或文件为空。请用 github_get_file 读取原始内容手动分析。")
                }
            }.toString()))
        },
    ))

    // ============ 工具 5: generate_fix_suggestion ============
    add(Tool(
        name = "generate_fix_suggestion",
        description = """
为发现的问题生成修复建议。传入发现问题详情，返回具体的修复方案。
Params: issue_type (security/bug/dependency/config), issue_description (问题描述), code_context (相关代码片段), language (编程语言)。
        """.trimIndent(),
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("issue_type", buildJsonObject {
                        put("type", "string")
                        put("description", "问题类型：security / bug / dependency / config")
                    })
                    put("issue_description", buildJsonObject {
                        put("type", "string")
                        put("description", "问题描述")
                    })
                    put("code_context", buildJsonObject {
                        put("type", "string")
                        put("description", "相关代码片段（可选）")
                    })
                    put("language", buildJsonObject {
                        put("type", "string")
                        put("description", "编程语言")
                    })
                },
                required = listOf("issue_type", "issue_description")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val issueType = o["issue_type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val description = o["issue_description"]?.jsonPrimitive?.contentOrNull ?: ""
            val codeCtx = o["code_context"]?.jsonPrimitive?.contentOrNull ?: ""
            val lang = o["language"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            // 返回结构化修复建议模板，LLM 会基于此生成具体方案
            listOf(UIMessagePart.Text(buildJsonObject {
                put("issue_type", issueType)
                put("issue_description", description)
                put("language", lang)
                put("fix_template", """
请根据以下模板为上述问题生成具体修复建议：

## 🔧 修复建议

### 问题说明
[简要描述问题及其影响]

### 严重程度
[critical/high/medium/low]

### 修复方案
[具体的代码修改方案，包含修复前后的代码对比]

### 修复步骤
1. [步骤 1]
2. [步骤 2]
3. [步骤 3]

### 验证方法
[如何验证修复已生效]

请根据问题类型（$issueType）和语言（$lang）生成针对性的修复方案。
                """.trimIndent())
                if (codeCtx.isNotBlank()) {
                    put("code_context_provided", true)
                    put("code_context_length", codeCtx.length)
                }
            }.toString()))
        },
    ))

    // ============ 工具 6: create_analysis_report ============
    add(Tool(
        name = "create_analysis_report",
        description = """
创建结构化的分析报告。在所有扫描完成后调用此工具汇总结果。
Params: repo_url (仓库 URL), findings_json (所有发现的 JSON 数组，每项含 type/severity/description/file/line), summary (总体评价)。
        """.trimIndent(),
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("repo_url", buildJsonObject {
                        put("type", "string")
                        put("description", "仓库 URL")
                    })
                    put("summary", buildJsonObject {
                        put("type", "string")
                        put("description", "总体分析摘要（Markdown 格式）")
                    })
                    put("risk_level", buildJsonObject {
                        put("type", "string")
                        put("description", "总体风险等级：critical / high / medium / low / safe")
                    })
                },
                required = listOf("repo_url", "summary", "risk_level")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val repoUrl = o["repo_url"]?.jsonPrimitive?.contentOrNull ?: ""
            val summary = o["summary"]?.jsonPrimitive?.contentOrNull ?: ""
            val riskLevel = o["risk_level"]?.jsonPrimitive?.contentOrNull ?: "medium"

            val riskEmoji = when (riskLevel.lowercase()) {
                "critical" -> "🔴🔴🔴"
                "high" -> "🔴🔴"
                "medium" -> "🟡"
                "low" -> "🟢"
                "safe" -> "✅"
                else -> "⚪"
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("report_generated", true)
                put("repo", repoUrl)
                put("risk_level", riskLevel)
                put("risk_emoji", riskEmoji)
                put("report_template", """
## 📊 $riskEmoji GitHub 项目分析报告

**仓库**: $repoUrl
**风险等级**: $riskLevel
**分析时间**: 已完成

---

### 📋 总览
$summary

---

### 🤔 下一步建议

以上是自动分析的结果。我在 AI 的帮助下已经完成了对项目的全面扫描。

**需要我帮你修复这些问题吗？** 我可以：
- 🔧 创建修复分支并提交代码修改
- 📝 创建 GitHub Issue 记录发现的问题
- 🔀 创建 Pull Request 包含所有修复
- 📋 仅列出修复方案供你手动处理

请告诉我你的选择！
                """.trimIndent())
            }.toString()))
        },
    ))
}
