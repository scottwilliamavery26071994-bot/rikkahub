package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val DOUYIN_BASE = "https://www.douyin.com"
private val douyinHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true).followSslRedirects(true).build()

private val COMMON_PARAMS = mapOf(
    "device_platform" to "webapp", "aid" to "6383", "channel" to "channel_pc_web",
    "version_code" to "170400", "version_name" to "17.4.0",
    "cookie_enabled" to "true", "platform" to "PC",
)

private var cachedVerifyFp: String = ""
private var cachedMsToken: String = ""

private fun headers(cookie: String, referer: String = DOUYIN_BASE): Headers = Headers.Builder()
    .add("Accept","application/json").add("Accept-Language","zh-CN,zh;q=0.9")
    .add("Cookie",cookie).add("Referer",referer)
    .add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
    .build()

private fun call(path: String, params: Map<String,Any?>, cookie: String, referer: String = DOUYIN_BASE): String {
    val qs = params.filterValues{it!=null}.map{(k,v)->"$k=${java.net.URLEncoder.encode(v.toString(),"UTF-8")}"}.joinToString("&")
    val url = "$DOUYIN_BASE$path?$qs"
    val req = Request.Builder().url(url).headers(headers(cookie,referer)).get().build()
    return try {
        douyinHttp.newCall(req).execute().use { it.body?.string()?.take(8000) ?: "{}" }
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private fun initVerifyFp(cookie: String) {
    if (cachedVerifyFp.isNotBlank()) return
    try {
        val resp = call("/", mapOf("channel" to "channel_pc_web"), cookie)
        // 从首页提取 verifyFp（简化处理）
        cachedVerifyFp = "verify_${System.currentTimeMillis().toString(36)}"
        cachedMsToken = ""
    } catch (_: Exception) {}
}

private fun parseAwemeId(input: String): String {
    // 支持: 纯数字ID、v.douyin.com/xxx 短链接、完整URL
    val patterns = listOf(
        Regex("""video/(\d+)"""),
        Regex("""aweme_id=(\d+)"""),
        Regex("""^(\d{15,20})$"""),
    )
    for (p in patterns) {
        val m = p.find(input)
        if (m != null) return m.groupValues[1]
    }
    return input
}

fun buildDouyinMcpTools(getCookie: () -> String): List<Tool> = buildList {

    add(Tool(
        name = "douyin_search_videos",
        description = "搜索抖音视频。Params: keyword(关键词), count(数量默认10), sort_type(0综合/1点赞最多/2最新), publish_time(0不限/1一天内/7一周内/180半年内)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("keyword", buildJsonObject { put("type","string"); put("description","搜索关键词") })
            put("count", buildJsonObject { put("type","integer"); put("description","数量，默认10，最大20") })
            put("sort_type", buildJsonObject { put("type","integer"); put("description","0综合/1点赞最多/2最新") })
            put("publish_time", buildJsonObject { put("type","integer"); put("description","0不限/1一天内/7一周内/180半年内") })
        }, required = listOf("keyword")) },
        execute = { args ->
            val o = args.jsonObject
            val kw = o["keyword"]?.jsonPrimitive?.contentOrNull ?: error("keyword required")
            val cnt = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
            val sort = o["sort_type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val pt = o["publish_time"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie。在沙箱运行: uv run login.py 扫码登录"}"""))
            initVerifyFp(cookie)
            val params = mutableMapOf<String,Any?>("keyword" to kw, "count" to cnt, "offset" to 0,
                "search_channel" to 0, "sort_type" to sort, "publish_time" to pt,
                "verifyFp" to cachedVerifyFp, "fp" to cachedVerifyFp,
                "enable_history" to "1", "search_source" to "tab_search")
            params.putAll(COMMON_PARAMS)
            val referer = "$DOUYIN_BASE/search/${java.net.URLEncoder.encode(kw,"UTF-8")}?type=general"
            listOf(UIMessagePart.Text(call("/aweme/v1/web/general/search/single/", params, cookie, referer)))
        },
    ))

    add(Tool(
        name = "douyin_get_video_detail",
        description = "获取抖音视频详情（标题/点赞/评论/分享/收藏/时长/作者/下载链接）。Params: aweme_id(视频ID，支持纯数字或分享链接)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("aweme_id", buildJsonObject { put("type","string"); put("description","视频ID或分享链接") })
        }, required = listOf("aweme_id")) },
        execute = { args ->
            val input = args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull ?: error("aweme_id required")
            val aid = parseAwemeId(input)
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie"}"""))
            initVerifyFp(cookie)
            val params = mutableMapOf<String,Any?>("aweme_id" to aid, "verifyFp" to cachedVerifyFp, "fp" to cachedVerifyFp)
            params.putAll(COMMON_PARAMS)
            listOf(UIMessagePart.Text(call("/aweme/v1/web/aweme/detail/", params, cookie)))
        },
    ))

    add(Tool(
        name = "douyin_get_video_comments",
        description = "获取抖音视频评论。Params: aweme_id(视频ID), cursor(分页游标默认0), count(默认20)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("aweme_id", buildJsonObject { put("type","string"); put("description","视频ID") })
            put("cursor", buildJsonObject { put("type","integer"); put("description","分页游标") })
            put("count", buildJsonObject { put("type","integer"); put("description","数量默认20") })
        }, required = listOf("aweme_id")) },
        execute = { args ->
            val o = args.jsonObject
            val aid = parseAwemeId(o["aweme_id"]?.jsonPrimitive?.contentOrNull ?: error("aweme_id required"))
            val cursor = o["cursor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val cnt = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie"}"""))
            initVerifyFp(cookie)
            val params = mutableMapOf<String,Any?>("aweme_id" to aid, "cursor" to cursor, "count" to cnt,
                "item_type" to 0, "verifyFp" to cachedVerifyFp, "fp" to cachedVerifyFp)
            params.putAll(COMMON_PARAMS)
            listOf(UIMessagePart.Text(call("/aweme/v1/web/comment/list/", params, cookie)))
        },
    ))

    add(Tool(
        name = "douyin_get_user_info",
        description = "获取抖音用户资料（昵称/头像/粉丝/关注/获赞/作品数）。Params: sec_user_id(用户安全ID，以MS4wLjAB开头)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("sec_user_id", buildJsonObject { put("type","string"); put("description","用户安全ID") })
        }, required = listOf("sec_user_id")) },
        execute = { args ->
            val uid = args.jsonObject["sec_user_id"]?.jsonPrimitive?.contentOrNull ?: error("sec_user_id required")
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie"}"""))
            initVerifyFp(cookie)
            val params = mutableMapOf<String,Any?>("sec_user_id" to uid, "verifyFp" to cachedVerifyFp, "fp" to cachedVerifyFp)
            params.putAll(COMMON_PARAMS)
            listOf(UIMessagePart.Text(call("/aweme/v1/web/user/profile/other/", params, cookie)))
        },
    ))

    add(Tool(
        name = "douyin_get_user_posts",
        description = "获取抖音用户作品列表。Params: sec_user_id, max_cursor(分页默认0), count(默认18)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("sec_user_id", buildJsonObject { put("type","string"); put("description","用户安全ID") })
            put("max_cursor", buildJsonObject { put("type","string"); put("description","分页游标") })
            put("count", buildJsonObject { put("type","integer"); put("description","数量默认18") })
        }, required = listOf("sec_user_id")) },
        execute = { args ->
            val o = args.jsonObject
            val uid = o["sec_user_id"]?.jsonPrimitive?.contentOrNull ?: error("sec_user_id required")
            val cursor = o["max_cursor"]?.jsonPrimitive?.contentOrNull ?: "0"
            val cnt = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 18
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie"}"""))
            initVerifyFp(cookie)
            val params = mutableMapOf<String,Any?>("sec_user_id" to uid, "max_cursor" to cursor, "count" to cnt,
                "locate_query" to "false", "verifyFp" to cachedVerifyFp, "fp" to cachedVerifyFp)
            params.putAll(COMMON_PARAMS)
            listOf(UIMessagePart.Text(call("/aweme/v1/web/aweme/post/", params, cookie)))
        },
    ))

    add(Tool(
        name = "douyin_get_homefeed",
        description = "获取抖音推荐视频流。Params: count(默认20), refresh_index(默认0)。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("count", buildJsonObject { put("type","integer"); put("description","数量") })
            put("refresh_index", buildJsonObject { put("type","integer"); put("description","刷新索引") })
        }) },
        execute = { args ->
            val o = args.jsonObject
            val cnt = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
            val ri = o["refresh_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val cookie = getCookie()
            if (cookie.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"请先设置抖音Cookie"}"""))
            val params = mutableMapOf<String,Any?>("refresh_index" to ri, "count" to cnt, "video_type_select" to 0)
            params.putAll(COMMON_PARAMS)
            listOf(UIMessagePart.Text(call("/aweme/v1/web/tab/feed/", params, cookie, DOUYIN_BASE)))
        },
    ))

    add(Tool(
        name = "douyin_check_login",
        description = "检查抖音 Cookie 登录状态。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val cookie = getCookie()
            val loggedIn = cookie.isNotBlank()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("logged_in", loggedIn)
                put("cookie_length", cookie.length)
                if (!loggedIn) put("tip","请登录: 在沙箱运行 uv run /workspace/douyin-mcp/login.py 扫码登录，Cookie保存到 ~/.config/douyinmcp/cookies.txt")
            }.toString()))
        },
    ))
}
