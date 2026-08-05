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

/** Google - 无需 API Key，直接抓取网页结果（与浏览器搜索一致） */
object GoogleSearchService : SearchService<SearchServiceOptions.GoogleOptions> {
    override val name: String = "Google"

    @Composable
    override fun Description() {
        Text("无需 API Key，直接搜索（与浏览器一致）")
    }

    override fun parameters(options: SearchServiceOptions.GoogleOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.GoogleOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GoogleOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&num=10&hl=zh-CN"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.google.com/")
                .timeout(8000)
                .get()

            // 兼容新旧结构的容器选择器
            val containers = doc.select(".tF2Cxc, .g, div[data-sncf]")
            val results = containers.mapNotNull { element ->
                val title = element.selectFirst("h3")?.text()?.ifBlank { null } ?: return@mapNotNull null
                val link = element.selectFirst("a")?.attr("href")
                    ?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                val snippet = element.selectFirst(".VwiC3b, .IsZvec, [data-sncf]")?.text() ?: ""
                SearchResultItem(title = title, url = link, text = snippet)
            }

            require(results.isNotEmpty()) { "Search failed: no results found" }
            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GoogleOptions
    ): Result<ScrapedResult> = Result.failure(Exception("Scraping is not supported"))
}
