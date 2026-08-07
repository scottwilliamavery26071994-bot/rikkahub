package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 自定义 HTTP 请求工具（#602）：AI 可发送 GET/POST 请求（携带 headers/body）。
 *
 * 安全：复用 web_browse 的 SSRF 防护，禁止访问内网/回环地址。
 */
fun createHttpPostTool(context: Context): Tool {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    return Tool(
        name = "http_request",
        description = "Send a custom HTTP request (GET or POST) to a URL with optional headers and body. " +
            "Use when the user needs to call an API, test an endpoint, or fetch data from a service. " +
            "Private/internal network addresses are blocked for security.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("method") {
                        put("type", "string")
                        put("description", "HTTP method: GET or POST (default GET)")
                    }
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "Target URL")
                    }
                    putJsonObject("headers") {
                        put("type", "string")
                        put("description", "Optional JSON object of headers, e.g. {\"Authorization\":\"Bearer xxx\"}")
                    }
                    putJsonObject("body") {
                        put("type", "string")
                        put("description", "Request body for POST (raw string)")
                    }
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val params = args.jsonObject
            val url = params["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
            val method = params["method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
            val headersRaw = params["headers"]?.jsonPrimitive?.contentOrNull
            val body = params["body"]?.jsonPrimitive?.contentOrNull

            // SSRF 防护：禁止内网/回环地址
            if (isPrivateNetworkUrl(url)) {
                return@Tool listOf(UIMessagePart.Text("不允许访问内网地址"))
            }

            val requestBuilder = Request.Builder().url(url)
            if (!headersRaw.isNullOrBlank()) {
                runCatching {
                    val headersObj = me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(headersRaw).jsonObject
                    headersObj.forEach { (k, v) ->
                        requestBuilder.header(k, v.jsonPrimitive.contentOrNull ?: "")
                    }
                }
            }

            if (method == "POST") {
                val mediaType = "application/json".toMediaType()
                requestBuilder.post((body ?: "").toRequestBody(mediaType))
            } else {
                requestBuilder.get()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            val responseBody = response.body?.string()?.take(4000) ?: ""
            response.close()

            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", code in 200..299)
                    put("status", code)
                    put("body", JsonPrimitive(responseBody))
                }.toString()
            ))
        },
    )
}

/** SSRF 防护：判断 URL 是否指向内网/回环地址 */
internal suspend fun isPrivateNetworkUrl(url: String): Boolean = withContext(Dispatchers.IO) {
    val host = runCatching { java.net.URI(url).host }.getOrNull()
    if (host == null) {
        // 无法解析 host 视为不安全
        return@withContext true
    }
    runCatching {
        val addr = java.net.InetAddress.getByName(host)
        val raw = addr.address
        addr.isLoopbackAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            (raw.size == 16 && (raw[0].toInt() and 0xfe) == 0xfc) || // IPv6 ULA fc00::/7
            (raw.size == 4 && raw[0].toInt() == 169 && raw[1].toInt() == 254) // 169.254.x.x
    }.getOrDefault(true)
}
