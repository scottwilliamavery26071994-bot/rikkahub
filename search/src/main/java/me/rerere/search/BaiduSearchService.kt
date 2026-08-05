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
            val url = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, "UTF-8") + "&rn=10"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Upgrade-Insecure-Requests", "1")
                .referrer("https://www.baidu.com/")
                .cookie("BAIDUID", java.util.UUID.randomUUID().toString().replace("-", "") + ":FG=1")
                .timeout(10000)
                .get()

            val results = doc.select(".result, .c-container, .result-op, div[class*=result]").mapNotNull { element ->
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

            if (results.isEmpty()) {
                error("Search failed: no results found")
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
