package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val exHttp = OkHttpClient.Builder()
    .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
    .followRedirects(true).build()

private fun exCall(url: String, headers: Map<String,String> = emptyMap(), body: String? = null): String {
    return try {
        val b = Request.Builder().url(url)
            .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept","application/json, text/plain, */*")
            .header("Accept-Language","zh-CN,zh;q=0.9")
        headers.forEach{(k,v)->b.header(k,v)}
        val req = if(body!=null) b.post(body.toRequestBody("application/json".toMediaType())).build()
                  else b.get().build()
        exHttp.newCall(req).execute().use { it.body?.string()?.take(10000) ?: "{}" }
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

fun buildExecutionMcpTools(): List<Tool> = buildList {

    // === 失信被执行人查询 ===
    add(Tool(name="shixin_search",
        description="查询失信被执行人（老赖）信息。数据来源：中国执行信息公开网。Params: name(姓名/企业名), id_card(身份证/统一信用代码,可选), page(页码默认1)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("name",buildJsonObject{put("type","string");put("description","姓名或企业名称")})
            put("id_card",buildJsonObject{put("type","string");put("description","身份证号或统一信用代码(可选)")})
            put("page",buildJsonObject{put("type","integer");put("description","页码，默认1")})
        },required=listOf("name")) },
        execute={ args ->
            val o=args.jsonObject
            val name=o["name"]?.jsonPrimitive?.contentOrNull?:error("name")
            val card=o["id_card"]?.jsonPrimitive?.contentOrNull?:""
            val page=o["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:1
            // 中国执行信息公开网API
            val result=exCall(
                "https://zxgk.court.gov.cn/zhixing/newdetail",
                headers=mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body="pname=$name&cardNum=$card&currentPage=$page&pageSize=10"
            )
            // 备用API
            val result2=exCall(
                "https://api.allorigins.win/raw?url="+java.net.URLEncoder.encode(
                    "https://sp0.baidu.com/8aQDcjqpAAV3otqbppnN2DJv/api.php?resource_id=6899&query=$name&pn=${(page-1)*10}&rn=10&ie=utf-8&oe=utf-8",
                    "UTF-8"
                )
            )
            listOf(UIMessagePart.Text(buildJsonObject{
                put("name",name); put("source","中国执行信息公开网")
                put("raw",result.take(3000))
                put("backup",result2.take(3000))
                put("tip","数据仅供参考，请以法院官网为准")
            }.toString()))
        },
    ))

    // === 限制消费令查询 ===
    add(Tool(name="xianzhi_search",
        description="查询限制消费令（限高令）信息。Params: name(姓名/企业名), id_card(身份证/统一信用代码,可选)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("name",buildJsonObject{put("type","string");put("description","姓名或企业名称")})
            put("id_card",buildJsonObject{put("type","string");put("description","身份证号(可选)")})
        },required=listOf("name")) },
        execute={ args ->
            val o=args.jsonObject
            val name=o["name"]?.jsonPrimitive?.contentOrNull?:error("name")
            val card=o["id_card"]?.jsonPrimitive?.contentOrNull?:""
            val result=exCall(
                "https://api.allorigins.win/raw?url="+java.net.URLEncoder.encode(
                    "https://sp0.baidu.com/8aQDcjqpAAV3otqbppnN2DJv/api.php?resource_id=6900&query=$name&pn=0&rn=10&ie=utf-8&oe=utf-8",
                    "UTF-8"
                )
            )
            listOf(UIMessagePart.Text(buildJsonObject{
                put("name",name); put("type","限制消费令查询")
                put("raw",result.take(3000))
                put("tip","数据仅供参考")
            }.toString()))
        },
    ))

    // === 综合查询 ===
    add(Tool(name="execution_search",
        description="综合查询个人/企业被执行信息。包含失信被执行人、限制消费、被执行人记录。Params: name(必填), id_card(可选)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("name",buildJsonObject{put("type","string");put("description","姓名或企业名称")})
            put("id_card",buildJsonObject{put("type","string");put("description","身份证号或统一信用代码(可选)")})
        },required=listOf("name")) },
        execute={ args ->
            val o=args.jsonObject
            val name=o["name"]?.jsonPrimitive?.contentOrNull?:error("name")
            val card=o["id_card"]?.jsonPrimitive?.contentOrNull?:""
            val cardParam=if(card.isNotBlank()) "&cardNum=$card" else ""
            // 综合查询
            val results= mutableListOf<String>()
            // 1. 失信
            results.add("=== 失信被执行人 ===")
            results.add(exCall("https://zxgk.court.gov.cn/zhixing/newdetail",
                mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                "pname=$name${cardParam}&currentPage=1&pageSize=10").take(2000))
            // 2. 被执行人
            results.add("=== 被执行人 ===")
            results.add(exCall("https://zxgk.court.gov.cn/zhixing/newdetail",
                mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                "pname=$name${cardParam}&currentPage=1&pageSize=10&jf=1").take(2000))
            listOf(UIMessagePart.Text(results.joinToString("\n\n")))
        },
    ))
}
