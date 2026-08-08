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

private const val FC_BASE = "https://api.firecrawl.dev/v1"
private val fcHttp = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private fun fcCall(path: String, body: String, key: String, method: String = "POST"): String {
    return try {
        val req = Request.Builder()
            .url("$FC_BASE/$path")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
        when (method) {
            "GET" -> req.get().build()
            else -> req.post(body.toRequestBody("application/json".toMediaType())).build()
        }.let { built ->
            fcHttp.newCall(built).execute().use { it.body?.string()?.take(12000) ?: "{}" }
        }
    } catch (e: Exception) {
        """{"error":"${e.message?.take(200)}"}"""
    }
}

fun buildFirecrawlMcpTools(getApiKey: () -> String?): List<Tool> = buildList {

    // === scrape — 抓取单个URL ===
    add(Tool(
        name = "firecrawl_scrape",
        description = "抓取单个网页，返回干净的 Markdown 文本。支持 JavaScript 渲染。Params: url(必需), formats(格式数组，默认markdown), onlyMainContent(仅正文，默认true)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "要抓取的URL") })
                    put("onlyMainContent", buildJsonObject { put("type", "boolean"); put("description", "仅返回正文内容，默认true") })
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val url = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
            val onlyMain = args.jsonObject["onlyMainContent"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
            val key = getApiKey()
            if (key.isNullOrBlank()) return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Firecrawl API Key。获取: https://www.firecrawl.dev/"}"""
            ))
            val body = """{"url":"$url","formats":["markdown"],"onlyMainContent":$onlyMain}"""
            listOf(UIMessagePart.Text(fcCall("scrape", body, key)))
        },
    ))

    // === search — 搜索网页 ===
    add(Tool(
        name = "firecrawl_search",
        description = "搜索网页并返回每个页面的 Markdown 内容。Params: query(搜索关键词), limit(结果数量默认5,最大10)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", "string"); put("description", "搜索关键词") })
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "结果数量默认5") })
                },
                required = listOf("query")
            )
        },
        execute = { args ->
            val query = args.jsonObject["query"]?.jsonPrimitive?.contentOrNull ?: error("query required")
            val limit = args.jsonObject["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5
            val key = getApiKey()
            if (key.isNullOrBlank()) return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Firecrawl API Key"}"""
            ))
            val body = """{"query":"$query","limit":$limit,"scrapeOptions":{"formats":["markdown"]}}"""
            listOf(UIMessagePart.Text(fcCall("search", body, key)))
        },
    ))

    // === crawl — 爬取整个网站 ===
    add(Tool(
        name = "firecrawl_crawl",
        description = "爬取整个网站的所有页面，返回每个页面的 Markdown。异步操作。Params: url(网站根URL), limit(最大页数默认10,最大50)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "网站根URL") })
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "最大页数默认10") })
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val url = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
            val limit = args.jsonObject["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
            val key = getApiKey()
            if (key.isNullOrBlank()) return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Firecrawl API Key"}"""
            ))
            val body = """{"url":"$url","limit":$limit,"scrapeOptions":{"formats":["markdown"]}}"""
            listOf(UIMessagePart.Text(fcCall("crawl", body, key)))
        },
    ))

    // === map — 获取网站所有URL ===
    add(Tool(
        name = "firecrawl_map",
        description = "获取网站的所有 URL 列表（站点地图）。Params: url(网站根URL), limit(最大URL数默认100)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "网站根URL") })
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "最大URL数默认100") })
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val url = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
            val limit = args.jsonObject["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100
            val key = getApiKey()
            if (key.isNullOrBlank()) return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Firecrawl API Key"}"""
            ))
            val body = """{"url":"$url","limit":$limit}"""
            listOf(UIMessagePart.Text(fcCall("map", body, key)))
        },
    ))

    // === extract — AI提取结构化数据 ===
    add(Tool(
        name = "firecrawl_extract",
        description = "用 AI 从网页中提取结构化数据。Params: url(网址), prompt(提取指令如'提取所有产品名称和价格')。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "网页URL") })
                    put("prompt", buildJsonObject { put("type", "string"); put("description", "提取指令") })
                },
                required = listOf("url", "prompt")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: error("url required")
            val prompt = o["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
            val key = getApiKey()
            if (key.isNullOrBlank()) return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Firecrawl API Key"}"""
            ))
            val escPrompt = prompt.replace("\"", "\\\"").replace("\n", " ")
            val body = """{"urls":["$url"],"prompt":"$escPrompt"}"""
            listOf(UIMessagePart.Text(fcCall("extract", body, key)))
        },
    ))
}
