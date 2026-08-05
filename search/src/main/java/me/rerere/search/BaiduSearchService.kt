/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import org.jsoup.Jsoup
import java.net.URLEncoder

/** 百度搜索 - 无需 API Key，直接抓取网页结果（与浏览器搜索一致） */
object BaiduSearchService : SearchService<SearchServiceOptions.BaiduOptions> {
    override val name: String = "百度"

    @Composable
    override fun Description() {
        Text("无需 API Key，直接搜索（与浏览器一致）")
    }

    override fun parameters(options: SearchServiceOptions.BaiduOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.BaiduOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BaiduOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            // 建会话拿真实 BAIDUID cookie (比随机更不易触发验证)
            fun buildDoc(): org.jsoup.nodes.Document {
                val session = Jsoup.connect("https://www.baidu.com/")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .execute()
                val baiduid = session.cookie("BAIDUID")?.takeIf { it.isNotBlank() }
                    ?: (java.util.UUID.randomUUID().toString().replace("-", "") + ":FG=1")
                val url = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, "UTF-8") + "&rn=10"
                val conn = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://www.baidu.com/")
                    .cookie("BAIDUID", baiduid)
                    .cookie("BIDUPSID", baiduid)
                    .timeout(10000)
                // 若用户在应用内 WebView/浏览器过过百度验证, CookieManager 里有 BDUSS 等会话,
                // 带上后可直接通过验证
                val sessionCookie = SearchService.appContext?.let { ctx ->
                    runCatching {
                        android.webkit.CookieManager.getInstance().getCookie("https://www.baidu.com")
                    }.getOrNull()
                } ?: ""
                if (sessionCookie.isNotBlank()) {
                    conn.header("Cookie", sessionCookie)
                }
                return conn.get()
            }

            fun parse(doc: org.jsoup.nodes.Document): List<SearchResultItem> =
                doc.select(".result, .c-container, .result-op, div[class*=result]").mapNotNull { element ->
                    val titleEl = element.selectFirst("h3 > a, h3 a") ?: return@mapNotNull null
                    val title = titleEl.text().ifBlank { return@mapNotNull null }
                    val link = titleEl.attr("href")
                        .let { h ->
                            if (h.startsWith("http")) h
                            else if (h.startsWith("//")) "https:$h"
                            else if (h.startsWith("/link")) "https://www.baidu.com$h"
                            else h
                        }
                        .takeIf { it.startsWith("http") }
                        ?: return@mapNotNull null
                    val snippet = element.selectFirst(".c-abstract, .c-span-last, .c-color-text, .c-line-clamp, .content-right_8Zs40, .content-right_2s-H4, span[class*=content]")?.text()
                        ?: ""
                    SearchResultItem(title = title, url = link, text = snippet)
                }

            var doc = buildDoc()
            var results = parse(doc)
            // 触发百度安全验证或结果为空时重试一次 (百度随机验证, 重试常能成功)
            if (results.isEmpty() && doc.title()?.contains("百度安全验证") == true) {
                doc = buildDoc()
                results = parse(doc)
            }
            if (results.isEmpty()) {
                error("百度触发安全验证: 请在应用内打开网页 www.baidu.com 搜索一次并完成滑块验证, 之后百度搜索即可正常使用")
            }
            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BaiduOptions
    ): Result<ScrapedResult> = Result.failure(Exception("Scraping is not supported"))
}
