package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val PW_BASE = "http://127.0.0.1:9877"
private val pwHttp = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private fun pwCall(path: String, body: String? = null): String {
    return try {
        val req = if (body != null) {
            Request.Builder().url("$PW_BASE/$path")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        } else {
            Request.Builder().url("$PW_BASE/$path").get().build()
        }
        pwHttp.newCall(req).execute().use { it.body?.string()?.take(12000) ?: "{}" }
    } catch (e: Exception) {
        """{"error":"Playwright 服务未启动。请在沙箱执行: node /workspace/playwright-server.js &"}"""
    }
}

fun buildPlaywrightMcpTools(): List<Tool> = buildList {

    // === 导航 ===
    add(Tool(
        name = "browser_navigate",
        description = "导航到指定URL。Params: url(网址)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "要访问的URL") })
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val url = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
            listOf(UIMessagePart.Text(pwCall("navigate", """{"url":"$url"}""")))
        },
    ))

    // === 点击 ===
    add(Tool(
        name = "browser_click",
        description = "点击页面元素。Params: selector(CSS选择器如'button#submit'或文本'登录')。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject { put("type", "string"); put("description", "CSS选择器或文本内容") })
                },
                required = listOf("selector")
            )
        },
        execute = { args ->
            val sel = args.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: error("selector required")
            listOf(UIMessagePart.Text(pwCall("click", """{"selector":"${sel.replace("\"","\\\"")}"}""")))
        },
    ))

    // === 输入文字 ===
    add(Tool(
        name = "browser_type",
        description = "在输入框中输入文字。Params: selector(CSS选择器), text(要输入的文字), submit(是否回车提交,默认false)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject { put("type", "string"); put("description", "CSS选择器") })
                    put("text", buildJsonObject { put("type", "string"); put("description", "输入文字") })
                    put("submit", buildJsonObject { put("type", "boolean"); put("description", "回车提交") })
                },
                required = listOf("selector", "text")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val sel = o["selector"]?.jsonPrimitive?.contentOrNull ?: error("selector required")
            val text = o["text"]?.jsonPrimitive?.contentOrNull ?: error("text required")
            val submit = o["submit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            listOf(UIMessagePart.Text(pwCall("type", """{"selector":"${sel.replace("\"","\\\"")}","text":"${text.replace("\"","\\\"")}","submit":$submit}""")))
        },
    ))

    // === 截图 ===
    add(Tool(
        name = "browser_screenshot",
        description = "截取当前页面截图。返回截图文件路径。Params: fullPage(全页截图,默认false)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("fullPage", buildJsonObject { put("type", "boolean"); put("description", "是否全页截图") })
                }
            )
        },
        execute = { args ->
            val full = args.jsonObject["fullPage"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            listOf(UIMessagePart.Text(pwCall("screenshot", """{"fullPage":$full}""")))
        },
    ))

    // === 获取页面内容 ===
    add(Tool(
        name = "browser_content",
        description = "获取当前页面的文本内容或HTML。Params: format(text/html/markdown,默认text)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("format", buildJsonObject { put("type", "string"); put("description", "text/html/markdown") })
                }
            )
        },
        execute = { args ->
            val fmt = args.jsonObject["format"]?.jsonPrimitive?.contentOrNull ?: "text"
            listOf(UIMessagePart.Text(pwCall("content", """{"format":"$fmt"}""")))
        },
    ))

    // === 获取页面快照（可访问性树） ===
    add(Tool(
        name = "browser_snapshot",
        description = "获取页面的可访问性快照（用于AI理解页面结构）。返回页面上所有交互元素的列表。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            listOf(UIMessagePart.Text(pwCall("snapshot")))
        },
    ))

    // === 执行JavaScript ===
    add(Tool(
        name = "browser_evaluate",
        description = "在页面上执行JavaScript代码。Params: script(JS代码)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("script", buildJsonObject { put("type", "string"); put("description", "JavaScript代码") })
                },
                required = listOf("script")
            )
        },
        execute = { args ->
            val script = args.jsonObject["script"]?.jsonPrimitive?.contentOrNull ?: error("script required")
            listOf(UIMessagePart.Text(pwCall("evaluate", """{"script":"${script.replace("\"","\\\"").replace("\n"," ")}"}""")))
        },
    ))

    // === 填充表单 ===
    add(Tool(
        name = "browser_fill",
        description = "批量填充表单。Params: fields(JSON数组，每项含selector和value)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("fields", buildJsonObject { put("type", "string"); put("description", "JSON数组如[{\"selector\":\"#name\",\"value\":\"张三\"}]") })
                },
                required = listOf("fields")
            )
        },
        execute = { args ->
            val fields = args.jsonObject["fields"]?.jsonPrimitive?.contentOrNull ?: error("fields required")
            listOf(UIMessagePart.Text(pwCall("fill", """{"fields":$fields}""")))
        },
    ))

    // === 等待 ===
    add(Tool(
        name = "browser_wait",
        description = "等待页面元素出现或等待指定时间。Params: selector(CSS选择器,可选), timeout(超时毫秒,默认5000)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject { put("type", "string"); put("description", "等待的CSS选择器") })
                    put("timeout", buildJsonObject { put("type", "integer"); put("description", "超时毫秒") })
                }
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val sel = o["selector"]?.jsonPrimitive?.contentOrNull ?: ""
            val timeout = o["timeout"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5000
            val body = if (sel.isNotBlank()) """{"selector":"${sel.replace("\"","\\\"")}","timeout":$timeout}""" else """{"timeout":$timeout}"""
            listOf(UIMessagePart.Text(pwCall("wait", body)))
        },
    ))

    // === 获取/设置Cookie ===
    add(Tool(
        name = "browser_cookies",
        description = "获取或设置Cookie。Params: action(get/set), cookies(Cookie JSON, set时需要)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string"); put("description", "get或set") })
                    put("cookies", buildJsonObject { put("type", "string"); put("description", "Cookie JSON(set时需要)") })
                },
                required = listOf("action")
            )
        },
        execute = { args ->
            val action = args.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: "get"
            val cookies = args.jsonObject["cookies"]?.jsonPrimitive?.contentOrNull ?: "[]"
            val body = if (action == "set") """{"action":"set","cookies":$cookies}""" else """{"action":"get"}"""
            listOf(UIMessagePart.Text(pwCall("cookies", body)))
        },
    ))

    // === 关闭浏览器 ===
    add(Tool(
        name = "browser_close",
        description = "关闭浏览器。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            listOf(UIMessagePart.Text(pwCall("close")))
        },
    ))

    // === 状态检查 ===
    add(Tool(
        name = "browser_status",
        description = "检查 Playwright 浏览器服务状态。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            listOf(UIMessagePart.Text(pwCall("status")))
        },
    ))
}
