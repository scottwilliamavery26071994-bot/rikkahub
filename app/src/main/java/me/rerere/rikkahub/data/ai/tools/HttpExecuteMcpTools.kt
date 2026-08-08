package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val hHttp = OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS)
    .followRedirects(true).build()

fun buildHttpExecuteMcpTools(): List<Tool> = listOf(
    Tool(name="http_execute",
        description="执行任意HTTP请求。Params: url(必需), method(GET/POST/PUT/DELETE/PATCH默认GET), headers(JSON格式请求头,可选), body(请求体,可选)。返回状态码/响应头/响应体。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("url",buildJsonObject{put("type","string");put("description","请求URL")})
            put("method",buildJsonObject{put("type","string");put("description","HTTP方法 GET/POST/PUT/DELETE/PATCH")})
            put("headers",buildJsonObject{put("type","string");put("description","请求头JSON 如{\"Authorization\":\"Bearer xxx\"}")})
            put("body",buildJsonObject{put("type","string");put("description","请求体")})
        },required=listOf("url")) },
        execute={ args ->
            val o=args.jsonObject
            val url=o["url"]?.jsonPrimitive?.contentOrNull?:error("url")
            val method=(o["method"]?.jsonPrimitive?.contentOrNull?:"GET").uppercase()
            val headersStr=o["headers"]?.jsonPrimitive?.contentOrNull
            val bodyStr=o["body"]?.jsonPrimitive?.contentOrNull
            try {
                val req=Request.Builder().url(url)
                // 解析headers
                if(headersStr!=null) try{ Json.parseToJsonElement(headersStr).jsonObject.forEach{(k,v)->req.header(k,v.jsonPrimitive.content)} }catch(_:Exception){}
                // body
                val body:RequestBody? = if(bodyStr!=null){
                    try{ Json.parseToJsonElement(bodyStr); bodyStr.toRequestBody("application/json".toMediaType()) }
                    catch(_:Exception){ bodyStr.toRequestBody("text/plain".toMediaType()) }
                } else if(method in listOf("POST","PUT","PATCH")) "".toRequestBody("application/json".toMediaType()) else null
                when(method){
                    "GET"->req.get()
                    "POST"->req.post(body!!)
                    "PUT"->req.put(body!!)
                    "DELETE"->if(body!=null) req.delete(body) else req.delete()
                    "PATCH"->req.patch(body!!)
                    else->req.method(method,body)
                }
                val resp=hHttp.newCall(req.build()).execute()
                val respBody=resp.body?.string()?.take(8000)?:""
                listOf(UIMessagePart.Text(buildJsonObject{
                    put("status",resp.code); put("url",url); put("method",method)
                    put("headers",buildJsonObject{ resp.headers.forEach{p->put(p.first,p.second)} })
                    put("body",respBody); put("body_length",respBody.length)
                }.toString()))
            } catch(e: Exception) { listOf(UIMessagePart.Text("""{"error":"${e.message?.take(300)}"}""")) }
        },
    )
)
