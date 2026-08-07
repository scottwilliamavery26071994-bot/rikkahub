package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.KnowledgeDoc
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant

private const val MAX_DOC_LENGTH = 8000

/**
 * RAG 知识库（#1657 基础版）。
 *
 * AI 可保存知识文档（标题 + 内容 + 标签），并按关键词检索。
 * 检索基于词频评分（TF），接口预留后续接入 sqlite-vec 向量检索。
 */
fun createKnowledgeBaseTools(
    settings: Settings,
    onSaveDocs: (List<KnowledgeDoc>) -> Unit,
): List<Tool> = listOf(
    Tool(
        name = "knowledge_save",
        description = "Save a document to the knowledge base for later retrieval. " +
            "Use when the user provides reference material (docs, notes, manuals, project info) " +
            "that should be remembered and searched later. Provide a clear title and tags.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Document title")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Document content (up to $MAX_DOC_LENGTH chars)")
                    }
                    putJsonObject("tags") {
                        put("type", "string")
                        put("description", "Comma-separated tags, e.g. \"project, config, api\"")
                    }
                },
                required = listOf("title", "content")
            )
        },
        execute = { params ->
            val obj = params.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
            val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
            val tags = obj["tags"]?.jsonPrimitive?.contentOrNull
                ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

            val doc = KnowledgeDoc(
                title = title,
                content = content.take(MAX_DOC_LENGTH),
                tags = tags,
            )
            // 更新知识库（通过回调写回 settings）
            onSaveDocs(settings.knowledgeDocs + doc)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("action", JsonPrimitive("save"))
                    put("id", doc.id)
                    put("title", JsonPrimitive(title))
                    put("message", JsonPrimitive("Document saved to knowledge base"))
                }.toString()
            ))
        },
        needsApproval = false,
    ),
    Tool(
        name = "knowledge_search",
        description = "Search the knowledge base by keywords. " +
            "Returns matching documents with title, tags and relevance. " +
            "Use when answering questions about previously saved reference material.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search keywords")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results, default 3")
                    }
                },
                required = listOf("query")
            )
        },
        execute = { params ->
            val obj = params.jsonObject
            val query = obj["query"]?.jsonPrimitive?.contentOrNull ?: error("query required")
            val limit = obj["limit"]?.jsonPrimitive?.intOrNull ?: 3

            val docs = settings.knowledgeDocs
            val scored = docs.map { doc ->
                // #1657: 词频 + 向量余弦混合评分（向量部分离线可用）
                val score = scoreDoc(doc, query) + VectorIndexHelper.cosineScore(query, doc.content) * 10
                doc to score
            }.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit)

            if (scored.isEmpty()) {
                return@Tool listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("count", 0)
                        put("message", JsonPrimitive("No matching documents in knowledge base"))
                    }.toString()
                ))
            }

            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("count", scored.size)
                    put("results", buildJsonArray {
                        scored.forEach { (doc, score) ->
                            add(buildJsonObject {
                                put("id", doc.id)
                                put("title", JsonPrimitive(doc.title))
                                put("tags", JsonArray(doc.tags.map { JsonPrimitive(it) }))
                                put("relevance", score)
                                put("content", JsonPrimitive(doc.content.take(2000)))
                            })
                        }
                    })
                }.toString()
            ))
        },
        needsApproval = false,
    ),
)

/** 词频评分：查询词在标题/标签/内容中出现的加权计数 */
private fun scoreDoc(doc: KnowledgeDoc, query: String): Int {
    val terms = query.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
    if (terms.isEmpty()) return 0
    var score = 0
    val titleLower = doc.title.lowercase()
    val contentLower = doc.content.lowercase()
    val tagLower = doc.tags.joinToString(" ").lowercase()
    terms.forEach { term ->
        if (titleLower.contains(term)) score += 5
        if (tagLower.contains(term)) score += 3
        if (contentLower.contains(term)) score += 1
    }
    return score
}

