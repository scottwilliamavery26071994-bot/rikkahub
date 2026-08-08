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

private val sbHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private fun sbCall(
    baseUrl: String,
    path: String,
    method: String = "GET",
    apiKey: String = "",
    body: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
): String {
    return try {
        val req = Request.Builder().url("$baseUrl$path")
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) req.header("apikey", apiKey).header("Authorization", "Bearer $apiKey")
        extraHeaders.forEach { (k, v) -> req.header(k, v) }
        when (method) {
            "GET" -> req.get()
            "POST" -> req.post((body ?: "{}").toRequestBody("application/json".toMediaType()))
            "PATCH" -> req.patch((body ?: "{}").toRequestBody("application/json".toMediaType()))
            "DELETE" -> req.delete()
            "PUT" -> req.put((body ?: "{}").toRequestBody("application/json".toMediaType()))
            else -> req.get()
        }.let { built ->
            sbHttp.newCall(built.build()).execute().use {
                val text = it.body?.string() ?: "{}"
                """{"status":${it.code},"data":${if (text.startsWith("{") || text.startsWith("[")) text else "\"${text.take(5000).replace("\"", "\\\"")}\""}}"""
            }
        }
    } catch (e: Exception) {
        """{"error":"${e.message?.take(200)}"}"""
    }
}

fun buildSupabaseMcpTools(
    getProjectUrl: () -> String?,
    getApiKey: () -> String?,
): List<Tool> = buildList {

    add(Tool(
        name = "supabase_query",
        description = "执行 SQL 查询（通过 Supabase RPC）。需要先在 Supabase 创建名为 exec_sql 的 PostgreSQL 函数。Params: sql(SQL语句), limit(返回行数限制,默认100)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("sql", buildJsonObject { put("type", "string"); put("description", "SQL语句") })
                },
                required = listOf("sql")
            )
        },
        execute = { args ->
            val sql = args.jsonObject["sql"]?.jsonPrimitive?.contentOrNull ?: error("sql required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"请先配置 Supabase 项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"请先配置 Supabase API Key"}"""))
            val escSql = sql.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/rpc/exec_sql", "POST", key, """{"sql":"$escSql"}""")))
        },
    ))

    add(Tool(
        name = "supabase_select",
        description = "查询表数据。Params: table(表名), columns(列名,默认*), filter(过滤条件如id=eq.1), limit(默认50), order(排序如id.desc)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("table", buildJsonObject { put("type", "string"); put("description", "表名") })
                    put("columns", buildJsonObject { put("type", "string"); put("description", "列名(默认*)") })
                    put("filter", buildJsonObject { put("type", "string"); put("description", "过滤如 id=eq.1") })
                },
                required = listOf("table")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val table = o["table"]?.jsonPrimitive?.contentOrNull ?: error("table required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            val cols = o["columns"]?.jsonPrimitive?.contentOrNull ?: "*"
            val limit = o["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50
            val params = mutableListOf("select=$cols", "limit=$limit")
            o["filter"]?.jsonPrimitive?.contentOrNull?.let { params.add(it) }
            o["order"]?.jsonPrimitive?.contentOrNull?.let { params.add("order=$it") }
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/$table?${params.joinToString("&")}", "GET", key)))
        },
    ))

    add(Tool(
        name = "supabase_insert",
        description = "向表中插入数据。Params: table(表名), data(JSON数组如[{\"name\":\"test\"}])。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("table", buildJsonObject { put("type", "string"); put("description", "表名") })
                    put("data", buildJsonObject { put("type", "string"); put("description", "JSON数组") })
                },
                required = listOf("table", "data")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val table = o["table"]?.jsonPrimitive?.contentOrNull ?: error("table required")
            val data = o["data"]?.jsonPrimitive?.contentOrNull ?: error("data required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/$table", "POST", key, data,
                mapOf("Prefer" to "return=representation"))))
        },
    ))

    add(Tool(
        name = "supabase_update",
        description = "更新表数据。Params: table(表名), filter(过滤条件如id=eq.1), data(JSON如{\"name\":\"new\"})。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("table", buildJsonObject { put("type", "string"); put("description", "表名") })
                    put("filter", buildJsonObject { put("type", "string"); put("description", "过滤条件") })
                    put("data", buildJsonObject { put("type", "string"); put("description", "JSON") })
                },
                required = listOf("table", "filter", "data")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val table = o["table"]?.jsonPrimitive?.contentOrNull ?: error("table required")
            val filter = o["filter"]?.jsonPrimitive?.contentOrNull ?: error("filter required")
            val data = o["data"]?.jsonPrimitive?.contentOrNull ?: error("data required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/$table?$filter", "PATCH", key, data,
                mapOf("Prefer" to "return=representation"))))
        },
    ))

    add(Tool(
        name = "supabase_delete",
        description = "删除表数据。Params: table(表名), filter(过滤条件如id=eq.1)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("table", buildJsonObject { put("type", "string"); put("description", "表名") })
                    put("filter", buildJsonObject { put("type", "string"); put("description", "过滤条件") })
                },
                required = listOf("table", "filter")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val table = o["table"]?.jsonPrimitive?.contentOrNull ?: error("table required")
            val filter = o["filter"]?.jsonPrimitive?.contentOrNull ?: error("filter required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/$table?$filter", "DELETE", key)))
        },
    ))

    add(Tool(
        name = "supabase_rpc",
        description = "调用 Supabase RPC 函数。Params: function(函数名), params(JSON参数)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("function", buildJsonObject { put("type", "string"); put("description", "函数名") })
                    put("params", buildJsonObject { put("type", "string"); put("description", "JSON参数") })
                },
                required = listOf("function")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val fn = o["function"]?.jsonPrimitive?.contentOrNull ?: error("function required")
            val params = o["params"]?.jsonPrimitive?.contentOrNull ?: "{}"
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/rpc/$fn", "POST", key, params)))
        },
    ))

    add(Tool(
        name = "supabase_list_users",
        description = "列出 Supabase Auth 用户。Params: limit(默认50), page(页码默认1)。需要 Service Role Key。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "数量") })
                    put("page", buildJsonObject { put("type", "integer"); put("description", "页码") })
                }
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val limit = o["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50
            val page = o["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/auth/v1/admin/users?per_page=$limit&page=$page", "GET", key)))
        },
    ))

    add(Tool(
        name = "supabase_list_buckets",
        description = "列出 Supabase Storage 存储桶。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/storage/v1/bucket", "GET", key)))
        },
    ))

    add(Tool(
        name = "supabase_upload",
        description = "上传文件到 Supabase Storage。Params: bucket(桶名), path(文件路径), content(文本内容)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("bucket", buildJsonObject { put("type", "string"); put("description", "桶名") })
                    put("path", buildJsonObject { put("type", "string"); put("description", "存储路径") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "文件内容") })
                },
                required = listOf("bucket", "path", "content")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val bucket = o["bucket"]?.jsonPrimitive?.contentOrNull ?: error("bucket required")
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            val escPath = java.net.URLEncoder.encode(path, "UTF-8")
            listOf(UIMessagePart.Text(sbCall(url, "/storage/v1/object/$bucket/$escPath", "POST", key, content,
                mapOf("Content-Type" to "text/plain", "x-upsert" to "true"))))
        },
    ))

    add(Tool(
        name = "supabase_schema",
        description = "获取数据库表结构。返回所有表名和列信息。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val url = getProjectUrl() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置项目URL"}"""))
            val key = getApiKey() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"未配置API Key"}"""))
            listOf(UIMessagePart.Text(sbCall(url, "/rest/v1/", "GET", key,
                extraHeaders = mapOf("Accept" to "application/openapi+json"))))
        },
    ))

    add(Tool(
        name = "supabase_status",
        description = "检查 Supabase 配置状态。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val url = getProjectUrl()
            val key = getApiKey()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("configured", !url.isNullOrBlank() && !key.isNullOrBlank())
                put("project_url", url ?: "")
                put("api_key_set", !key.isNullOrBlank())
                if (url.isNullOrBlank() || key.isNullOrBlank()) {
                    put("howto", "在RikkaHub设置中配置Supabase项目URL和API Key。获取: https://app.supabase.com → Settings → API")
                }
            }.toString()))
        },
    ))
}
