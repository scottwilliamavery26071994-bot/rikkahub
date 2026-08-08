package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

private const val C7_BASE = "https://context7.com/api/v1"
private val c7Http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private fun c7Post(path: String, body: String): String {
    return try {
        c7Http.newCall(
            Request.Builder()
                .url("$C7_BASE/$path")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { it.body?.string()?.take(12000) ?: "{}" }
    } catch (e: Exception) {
        """{"error":"${e.message?.take(200)}"}"""
    }
}

fun buildContext7McpTools(): List<Tool> = listOf(
    Tool(
        name = "context7_resolve_library",
        description = "解析库名称为 Context7 兼容的库 ID。用于在获取文档前先确定要查询的库。Params: libraryName(库名称如'react', 'next.js', 'kotlin coroutines')。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("libraryName", buildJsonObject {
                        put("type", "string")
                        put("description", "要查找的库名称")
                    })
                },
                required = listOf("libraryName")
            )
        },
        execute = { args ->
            val name = args.jsonObject["libraryName"]?.jsonPrimitive?.contentOrNull ?: error("libraryName required")
            val body = """{"libraryName":"$name"}"""
            val resp = c7Post("search", body)
            listOf(UIMessagePart.Text(resp))
        },
    ),

    Tool(
        name = "context7_get_docs",
        description = "获取库的最新文档。返回 Markdown 格式的文档内容。Params: libraryId(Context7库ID，从context7_resolve_library获取), topic(主题如'hooks','routing',可选), tokens(最大token数默认10000)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("libraryId", buildJsonObject {
                        put("type", "string")
                        put("description", "Context7库ID")
                    })
                    put("topic", buildJsonObject {
                        put("type", "string")
                        put("description", "要查询的主题(可选)")
                    })
                    put("tokens", buildJsonObject {
                        put("type", "integer")
                        put("description", "最大token数默认10000")
                    })
                },
                required = listOf("libraryId")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val libId = o["libraryId"]?.jsonPrimitive?.contentOrNull ?: error("libraryId required")
            val topic = o["topic"]?.jsonPrimitive?.contentOrNull ?: ""
            val tokens = o["tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10000
            val body = buildJsonObject {
                put("libraryId", libId)
                put("tokens", tokens)
                if (topic.isNotBlank()) put("topic", topic)
            }.toString()
            val resp = c7Post("pages", body)
            listOf(UIMessagePart.Text(resp))
        },
    ),
)
