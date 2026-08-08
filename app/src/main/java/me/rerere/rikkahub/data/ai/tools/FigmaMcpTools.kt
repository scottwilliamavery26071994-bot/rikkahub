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

private const val FIGMA_API = "https://api.figma.com/v1"
private val figmaHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private fun figmaCall(path: String, token: String): String {
    return try {
        figmaHttp.newCall(
            Request.Builder()
                .url("$FIGMA_API/$path")
                .header("X-Figma-Token", token)
                .header("Accept", "application/json")
                .get()
                .build()
        ).execute().use { it.body?.string()?.take(12000) ?: "{}" }
    } catch (e: Exception) {
        """{"error":"${e.message?.take(200)}"}"""
    }
}

fun buildFigmaMcpTools(getToken: () -> String?): List<Tool> = buildList {

    // === 获取文件信息 ===
    add(Tool(
        name = "figma_get_file",
        description = "获取 Figma 文件完整数据。Params: file_key(文件key，从Figma URL中提取)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                },
                required = listOf("file_key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text(
                """{"error":"请先配置 Figma Token。获取: Figma → Settings → Account → Personal access tokens"}"""
            ))
            listOf(UIMessagePart.Text(figmaCall("files/$key", token)))
        },
    ))

    // === 获取文件节点 ===
    add(Tool(
        name = "figma_get_node",
        description = "获取 Figma 文件中特定节点的数据。Params: file_key, node_id(节点ID), depth(递归深度,可选)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                    put("node_id", buildJsonObject { put("type", "string"); put("description", "节点ID") })
                    put("depth", buildJsonObject { put("type", "integer"); put("description", "递归深度(可选)") })
                },
                required = listOf("file_key", "node_id")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val key = o["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val nodeId = o["node_id"]?.jsonPrimitive?.contentOrNull ?: error("node_id required")
            val depth = o["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            val path = "files/$key/nodes?ids=$nodeId" + (if (depth != null) "&depth=$depth" else "")
            listOf(UIMessagePart.Text(figmaCall(path, token)))
        },
    ))

    // === 获取图片导出 ===
    add(Tool(
        name = "figma_export_image",
        description = "导出 Figma 节点为图片。Params: file_key, node_id, format(png/jpg/svg/pdf,默认png), scale(缩放,默认1)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                    put("node_id", buildJsonObject { put("type", "string"); put("description", "节点ID") })
                    put("format", buildJsonObject { put("type", "string"); put("description", "png/jpg/svg/pdf") })
                    put("scale", buildJsonObject { put("type", "number"); put("description", "缩放比例") })
                },
                required = listOf("file_key", "node_id")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val key = o["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val nodeId = o["node_id"]?.jsonPrimitive?.contentOrNull ?: error("node_id required")
            val format = o["format"]?.jsonPrimitive?.contentOrNull ?: "png"
            val scale = o["scale"]?.jsonPrimitive?.contentOrNull ?: "1"
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            val path = "images/$key?ids=$nodeId&format=$format&scale=$scale"
            listOf(UIMessagePart.Text(figmaCall(path, token)))
        },
    ))

    // === 获取组件 ===
    add(Tool(
        name = "figma_get_components",
        description = "获取 Figma 文件的组件库。Params: file_key。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                },
                required = listOf("file_key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("files/$key/components", token)))
        },
    ))

    // === 获取样式 ===
    add(Tool(
        name = "figma_get_styles",
        description = "获取 Figma 文件的颜色/文本/效果样式。Params: file_key。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                },
                required = listOf("file_key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("files/$key/styles", token)))
        },
    ))

    // === 获取评论 ===
    add(Tool(
        name = "figma_get_comments",
        description = "获取 Figma 文件的评论。Params: file_key。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                },
                required = listOf("file_key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("files/$key/comments", token)))
        },
    ))

    // === 获取项目 ===
    add(Tool(
        name = "figma_get_projects",
        description = "获取 Figma 团队下的项目列表。Params: team_id(团队ID)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("team_id", buildJsonObject { put("type", "string"); put("description", "团队ID") })
                },
                required = listOf("team_id")
            )
        },
        execute = { args ->
            val teamId = args.jsonObject["team_id"]?.jsonPrimitive?.contentOrNull ?: error("team_id required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("teams/$teamId/projects", token)))
        },
    ))

    // === 获取用户信息 ===
    add(Tool(
        name = "figma_get_me",
        description = "获取当前 Figma 用户信息（验证Token有效性）。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("me", token)))
        },
    ))

    // === 获取变量 ===
    add(Tool(
        name = "figma_get_variables",
        description = "获取 Figma 文件的变量集合。Params: file_key。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_key", buildJsonObject { put("type", "string"); put("description", "Figma文件key") })
                },
                required = listOf("file_key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["file_key"]?.jsonPrimitive?.contentOrNull ?: error("file_key required")
            val token = getToken() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置Figma Token"}"""))
            listOf(UIMessagePart.Text(figmaCall("files/$key/variables/local", token)))
        },
    ))
}
