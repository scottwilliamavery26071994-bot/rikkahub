package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository

private const val SNIPPET_CONTEXT_CHARS = 120

/**
 * 搜索当前助手的历史对话内容。
 *
 * 在 [me.rerere.rikkahub.data.model.Assistant.enableRecentChatsReference] 开启时注入，
 * 让 AI 能按关键词检索其他对话窗口的消息内容（recent_chat 只给标题+日期，
 * 无法检索正文，导致 AI 幻觉调用不存在的工具）。
 */
fun createSearchConversationsTool(
    conversationRepo: ConversationRepository,
    settings: Settings,
): Tool = Tool(
    name = "conversation_search",
    description = "Search the content of this assistant's past conversations by keyword. " +
        "Scans message text across previous chat windows of the current assistant and returns " +
        "matching conversation titles with text snippets. Use this when you need to recall " +
        "details discussed in other conversations.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Keyword to search in past conversation content")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max result count, default 5")
                }
            },
            required = listOf("query")
        )
    },
    execute = { params ->
        val obj = params.jsonObject
        val query = obj["query"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text("{\"success\":false,\"error\":\"query is required\"}"))
        val limit = obj["limit"]?.jsonPrimitive?.intOrNull ?: 5

        val assistantId = settings.getCurrentAssistant().id
        val conversations = conversationRepo.getRecentConversations(assistantId, limit = 30)

        val results = buildJsonArray {
            conversations.forEach { conversation ->
                if (conversation.title.contains(query, ignoreCase = true)) {
                    add(buildJsonObject {
                        put("title", conversation.title)
                        put("conversation_id", conversation.id.toString())
                        put("snippet", "标题匹配")
                    })
                }
                conversation.currentMessages.forEach { message ->
                    val text = message.toText()
                    if (text.contains(query, ignoreCase = true)) {
                        add(buildJsonObject {
                            put("title", conversation.title)
                            put("conversation_id", conversation.id.toString())
                            put("snippet", extractSnippet(text, query))
                        })
                    }
                }
            }
        }

        val limited = results.take(limit)
        listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("count", limited.size)
                put("results", JsonArray(limited))
            }.toString()
        ))
    },
    needsApproval = false,
)

private fun extractSnippet(text: String, query: String): String {
    val lower = text.lowercase()
    val lowerQuery = query.lowercase()
    val index = lower.indexOf(lowerQuery)
    if (index < 0) return text.trim().take(SNIPPET_CONTEXT_CHARS * 2)
    val start = (index - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0)
    val end = (index + query.length + SNIPPET_CONTEXT_CHARS).coerceAtMost(text.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < text.length) "..." else ""
    return prefix + text.substring(start, end).trim() + suffix
}
