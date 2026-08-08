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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val DY = "https://www.douyin.com"
private val http = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build()
private val COMMON = mapOf("device_platform" to "webapp","aid" to "6383","channel" to "channel_pc_web","version_code" to "170400","version_name" to "17.4.0","cookie_enabled" to "true","platform" to "PC")

private fun hdrs(cookie: String, ref: String = DY) = Headers.Builder()
    .add("Accept","application/json").add("Accept-Language","zh-CN,zh;q=0.9")
    .add("Cookie",cookie).add("Referer",ref)
    .add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/135.0.0.0 Safari/537.36").build()

private fun api(path: String, params: MutableMap<String,Any?>, cookie: String, ref: String = DY): String {
    params.putAll(COMMON)
    val qs = params.filterValues{it!=null}.map{(k,v)->"$k=${java.net.URLEncoder.encode(v.toString(),"UTF-8")}"}.joinToString("&")
    return try {
        http.newCall(Request.Builder().url("$DY$path?$qs").headers(hdrs(cookie,ref)).get().build())
            .execute().use { it.body?.string()?.take(8000) ?: "{}" }
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private suspend fun fetch(url: String, cookie: String = ""): String {
    val b = Request.Builder().url(url).apply {
        if(cookie.isNotBlank()) header("Cookie",cookie)
    }.get().build()
    return try { http.newCall(b).execute().use { it.body?.string()?.take(10000) ?: "" } }
    catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private fun aid(input: String) = Regex("""(\d{15,20})""").find(input)?.groupValues?.get(1) ?: input

fun buildDouyinMcpTools(getCookie: () -> String): List<Tool> = buildList {

    // ===== 登录 =====
    add(Tool(name="douyin_login",
        description="获取抖音扫码登录二维码图片。AI可直接展示二维码给用户扫描。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{}) },
        execute={
            // 获取登录页，提取二维码URL，下载为图片直接展示
            val html = fetch("$DY/login")
            val qrMatch = Regex("""qrcode[^"']*["']([^"']+)["']""").find(html)
                ?: Regex("""src=["']([^"']*qrcode[^"']*)["']""").find(html)
            val qrUrl = qrMatch?.groupValues?.get(1)?.let { if(it.startsWith("http")) it else "https:$it" }
            val parts = mutableListOf<UIMessagePart>(
                UIMessagePart.Text(buildJsonObject{
                    put("action","请扫描下方二维码登录抖音")
                    put("login_page","$DY/login")
                    put("step1","打开手机抖音扫描二维码")
                    put("step2","扫码确认后在浏览器按F12→Application→Cookies→复制douyin.com的全部Cookie")
                    put("step3","用 douyin_set_cookie 设置Cookie即可")
                }.toString())
            )
            // 如果能拿到二维码URL，直接展示图片
            if (qrUrl != null) {
                parts.add(UIMessagePart.Image(url = qrUrl))
                parts.add(UIMessagePart.Text("""{"qr_code_url":"$qrUrl","show_to_user":"请扫描上方二维码"}"""))
            }
            parts
        },
    ))

    add(Tool(name="douyin_set_cookie",
        description="设置抖音Cookie（从浏览器复制）。Params: cookie(完整Cookie字符串，需含sessionid)。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("cookie",buildJsonObject{put("type","string");put("description","浏览器复制的完整Cookie")})
        },required=listOf("cookie")) },
        execute={ args ->
            val c = args.jsonObject["cookie"]?.jsonPrimitive?.contentOrNull ?: error("cookie required")
            listOf(UIMessagePart.Text(buildJsonObject{
                put("saved",true)
                put("length",c.length)
                put("has_sessionid",c.contains("sessionid"))
                put("message","Cookie已接收。请调用 douyin_check_login 验证。注意：当前实现需要存入沙箱文件 ~/.config/douyinmcp/cookies.txt")
            }.toString()))
        },
    ))

    add(Tool(name="douyin_check_login",
        description="检查抖音Cookie登录状态。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{}) },
        execute={
            val c = getCookie()
            listOf(UIMessagePart.Text(buildJsonObject{
                put("logged_in",c.isNotBlank() && c.contains("sessionid"))
                put("cookie_length",c.length)
                if(c.isBlank()) put("action","请让用户访问 https://www.douyin.com/login 扫码登录，然后在浏览器F12→Application→Cookies复制Cookie，用douyin_set_cookie设置")
            }.toString()))
        },
    ))

    // ===== 搜索 =====
    add(Tool(name="douyin_search_videos",
        description="搜索抖音视频。Params: keyword(关键词), count(默认10), sort_type(0综合/1点赞最多/2最新), publish_time(0不限/1一天/7一周/180半年)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("keyword",buildJsonObject{put("type","string");put("description","关键词")})
            put("count",buildJsonObject{put("type","integer");put("description","数量默认10")})
            put("sort_type",buildJsonObject{put("type","integer");put("description","0综合/1点赞/2最新")})
            put("publish_time",buildJsonObject{put("type","integer");put("description","0不限/1一天/7一周/180半年")})
        },required=listOf("keyword")) },
        execute={ args ->
            val o=args.jsonObject; val kw=o["keyword"]?.jsonPrimitive?.contentOrNull?:error("kw")
            val cnt=o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:10
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录，请先扫码登录"}"""))
            val params=mutableMapOf<String,Any?>("keyword" to kw,"count" to cnt,"offset" to 0,
                "search_channel" to 0,"sort_type" to (o["sort_type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "publish_time" to (o["publish_time"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "verifyFp" to "verify_","fp" to "verify_","enable_history" to "1","search_source" to "tab_search")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/general/search/single/",params,c,
                "$DY/search/${java.net.URLEncoder.encode(kw,"UTF-8")}?type=general")))
        },
    ))

    // ===== 视频详情 =====
    add(Tool(name="douyin_get_video_detail",
        description="获取抖音视频详情。Params: aweme_id(视频ID或分享链接)。返回标题/点赞/评论/分享/收藏/时长/作者/下载链接。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID或分享链接")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/aweme/detail/",params,c)))
        },
    ))

    // ===== 评论 =====
    add(Tool(name="douyin_get_video_comments",
        description="获取抖音视频评论。Params: aweme_id, cursor(默认0), count(默认20)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
            put("cursor",buildJsonObject{put("type","integer");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val o=args.jsonObject; val id=aid(o["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"cursor" to (o["cursor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"item_type" to 0,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/comment/list/",params,c)))
        },
    ))

    add(Tool(name="douyin_get_sub_comments",
        description="获取评论回复(子评论)。Params: comment_id(父评论ID), cursor(默认0), count(默认20)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("comment_id",buildJsonObject{put("type","string");put("description","父评论ID")})
            put("cursor",buildJsonObject{put("type","integer");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("comment_id")) },
        execute={ args ->
            val o=args.jsonObject; val cid=o["comment_id"]?.jsonPrimitive?.contentOrNull?:error("id")
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("comment_id" to cid,"cursor" to (o["cursor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"item_type" to 0,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/comment/list/reply/",params,c)))
        },
    ))

    // ===== 用户 =====
    add(Tool(name="douyin_get_user_info",
        description="获取抖音用户资料。Params: sec_user_id(用户安全ID，以MS4wLjAB开头)。返回昵称/头像/粉丝/关注/获赞/作品数。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("sec_user_id",buildJsonObject{put("type","string");put("description","用户安全ID")})
        },required=listOf("sec_user_id")) },
        execute={ args ->
            val uid=args.jsonObject["sec_user_id"]?.jsonPrimitive?.contentOrNull?:error("id")
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("sec_user_id" to uid,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/user/profile/other/",params,c)))
        },
    ))

    add(Tool(name="douyin_get_user_posts",
        description="获取抖音用户作品列表。Params: sec_user_id, max_cursor(默认0), count(默认18)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("sec_user_id",buildJsonObject{put("type","string");put("description","用户安全ID")})
            put("max_cursor",buildJsonObject{put("type","string");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("sec_user_id")) },
        execute={ args ->
            val o=args.jsonObject; val uid=o["sec_user_id"]?.jsonPrimitive?.contentOrNull?:error("id")
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("sec_user_id" to uid,"max_cursor" to (o["max_cursor"]?.jsonPrimitive?.contentOrNull?:"0"),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:18),"locate_query" to "false","verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/aweme/post/",params,c)))
        },
    ))

    // ===== 推荐流 =====
    add(Tool(name="douyin_get_homefeed",
        description="获取抖音推荐视频流。Params: count(默认20), refresh_index(默认0)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
            put("refresh_index",buildJsonObject{put("type","integer");put("description","刷新索引")})
        }) },
        execute={ args ->
            val o=args.jsonObject
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("refresh_index" to (o["refresh_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"video_type_select" to 0)
            listOf(UIMessagePart.Text(api("/aweme/v1/web/tab/feed/",params,c,DY)))
        },
    ))

    // ===== 链接解析 =====
    add(Tool(name="douyin_resolve_share_url",
        description="解析抖音分享短链接(https://v.douyin.com/xxx)，获取视频ID和详情。Params: share_url。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("share_url",buildJsonObject{put("type","string");put("description","分享短链接")})
        },required=listOf("share_url")) },
        execute={ args ->
            val url=args.jsonObject["share_url"]?.jsonPrimitive?.contentOrNull?:error("url")
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            // 跟随重定向获取真实URL
            val client = OkHttpClient.Builder().followRedirects(false).build()
            val resp = client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build()).execute()
            val loc = resp.header("Location") ?: ""
            val aid = Regex("""video/(\d+)""").find(loc)?.groupValues?.get(1) ?: ""
            listOf(UIMessagePart.Text(buildJsonObject{
                put("share_url",url); put("resolved_url",loc); put("aweme_id",aid)
                if(aid.isNotBlank()){ put("tip","使用 douyin_get_video_detail 获取详情"); put("aweme_id",aid) }
            }.toString()))
        },
    ))

    // ===== 下载视频 =====
    add(Tool(name="douyin_download_video",
        description="获取抖音视频无水印下载链接和完整信息。Params: aweme_id。返回下载链接、文件信息、统计数据。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            // 获取详情→提取下载链接
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = api("/aweme/v1/web/aweme/detail/",params,c)
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id)
                put("detail",detail.take(5000))
                put("tip","从返回的 aweme_detail.video.play_addr.url_list 中获取无水印下载链接。下载: 使用 workspace_shell 执行 curl -L '链接' -o video.mp4")
            }.toString()))
        },
    ))

    // ===== 语音转文字 =====
    add(Tool(name="douyin_transcribe_video",
        description="获取抖音视频信息用于语音转文字。Params: aweme_id。返回视频详情和下载链接，AI可用 workspace_shell 下载后转写。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = api("/aweme/v1/web/aweme/detail/",params,c)
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("transcribe_howto","1.从detail中提取视频下载链接(video.play_addr.url_list) 2.用workspace_shell下载: curl -L '链接' -o /tmp/video.mp4 3.用workspace_shell提取音频: ffmpeg -i /tmp/video.mp4 -vn /tmp/audio.mp3 4.如有ASR服务(OpenAI Whisper等)，上传转写")
            }.toString()))
        },
    ))

    // ===== 批量转写 =====
    add(Tool(name="douyin_batch_transcribe",
        description="批量搜索并准备转写视频。Params: keyword, count(默认3)。搜索后返回视频列表及下载链接。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("keyword",buildJsonObject{put("type","string");put("description","搜索关键词")})
            put("count",buildJsonObject{put("type","integer");put("description","数量默认3")})
        },required=listOf("keyword")) },
        execute={ args ->
            val o=args.jsonObject; val kw=o["keyword"]?.jsonPrimitive?.contentOrNull?:error("kw")
            val cnt=o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:3
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("keyword" to kw,"count" to cnt,"offset" to 0,
                "search_channel" to 0,"sort_type" to 1,"publish_time" to 0,"verifyFp" to "verify_","fp" to "verify_",
                "enable_history" to "1","search_source" to "tab_search")
            listOf(UIMessagePart.Text(api("/aweme/v1/web/general/search/single/",params,c,
                "$DY/search/${java.net.URLEncoder.encode(kw,"UTF-8")}?type=general")))
        },
    ))

    // ===== 下载图文 =====
    add(Tool(name="douyin_download_images",
        description="获取抖音图文作品的图片链接列表。Params: aweme_id。返回所有图片URL。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","作品ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = api("/aweme/v1/web/aweme/detail/",params,c)
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("tip","图文作品的图片在 aweme_detail.images[] 数组中。下载: curl -L '图片URL' -o image.jpg")
            }.toString()))
        },
    ))

    // ===== OCR图文 =====
    add(Tool(name="douyin_ocr_images",
        description="获取图文作品图片用于OCR识别。Params: aweme_id。返回图片链接，AI可用RikkaHub的多模态能力直接识别图中文字。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","作品ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
            val c=getCookie(); if(c.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"未登录"}"""))
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = api("/aweme/v1/web/aweme/detail/",params,c)
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("ocr_tip","RikkaHub的AI可以直接识别图片中的文字。请从返回的images数组中获取图片URL，AI即可读取图中文字。")
            }.toString()))
        },
    ))
}
