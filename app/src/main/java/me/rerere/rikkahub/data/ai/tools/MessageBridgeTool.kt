package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.JsonElement
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
import me.rerere.rikkahub.data.datastore.PendingMessage
import me.rerere.rikkahub.data.datastore.PollingTask
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.addPendingMessage
import me.rerere.rikkahub.data.datastore.addPollingTask
import me.rerere.rikkahub.data.datastore.getPendingMessages
import me.rerere.rikkahub.data.datastore.getPollingTasks
import me.rerere.rikkahub.data.datastore.savePendingMessages
import org.koin.java.KoinJavaComponent

private fun getSettingsStore(): SettingsStore = run {
    KoinJavaComponent.getKoin().get()
}

fun createReadPendingMessagesTool(context: Context): Tool = Tool(
    name = "read_pending_messages",
    description = "读取消息桥中的待处理消息。外部系统通过轮询写入的消息存放在这里。读取后消息标记为已读，不会重复处理。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("source") {
                    put("type", "string")
                    put("description", "按来源筛选消息（可选）")
                }
                putJsonObject("max_count") {
                    put("type", "integer")
                    put("description", "最多读取条数，默认10")
                }
            }
        )
    },
    execute = { params ->
        val obj = params.jsonObject
        val source = obj["source"]?.jsonPrimitive?.contentOrNull
        val maxCount = obj["max_count"]?.jsonPrimitive?.intOrNull ?: 10
        val store = getSettingsStore()
        val msgs = store.getPendingMessages()
        val filtered = if (source != null) msgs.filter { it.source == source && it.direction == "incoming" }
            else msgs.filter { it.direction == "incoming" }
        val unread = filtered.filter { it.status == "pending" }.take(maxCount)
        if (unread.isNotEmpty()) {
            val ids = unread.map { it.id }.toSet()
            val updated = msgs.map { if (it.id in ids) it.copy(status = "read", readAt = System.currentTimeMillis()) else it }
            store.savePendingMessages(updated)
        }
        val result = if (unread.isEmpty()) {
            "消息桥中没有待处理消息。来源: ${source ?: "全部"}"
        } else {
            val sb = StringBuilder("读取到 ${unread.size} 条待处理消息：\n")
            unread.forEachIndexed { i, msg ->
                sb.appendLine("--- 消息 ${i + 1}/${unread.size} ---")
                sb.appendLine("来源: ${msg.source}")
                sb.appendLine("内容: ${msg.content}")
            }
            sb.toString()
        }
        listOf(UIMessagePart.Text(result))
    },
    needsApproval = false,
)

fun createSendMessageBridgeTool(context: Context): Tool = Tool(
    name = "send_message_bridge",
    description = "通过消息桥发送一条异步消息到外部。消息写入待发送区后等待外部系统拉取，不会进入主对话上下文。用于 AI 间通讯、跨端协作等场景。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("target") {
                    put("type", "string")
                    put("description", "目标标识，如 another_ai、codex、dingtalk，用于外部系统按目标拉取")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "消息内容")
                }
            },
            required = listOf("target", "content")
        )
    },
    execute = { params ->
        val obj = params.jsonObject
        val target = obj["target"]?.jsonPrimitive?.contentOrNull ?: error("target is required")
        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
        val store = getSettingsStore()
        store.addPendingMessage(PendingMessage(
            source = target,
            content = content,
            direction = "outgoing",
        ))
        listOf(UIMessagePart.Text("消息已写入消息桥待发送区（目标: $target），外部系统可拉取。"))
    },
    needsApproval = false,
)

fun createRegisterPollingTaskTool(context: Context): Tool = Tool(
    name = "register_polling_task",
    description = "注册一个消息桥轮询任务。AI 可指定外部 URL 和轮询间隔，系统会在后台周期性地拉取消息并存入待处理区。下次用户唤醒 AI 时，AI 可通过 read_pending_messages 读取。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("source") {
                    put("type", "string")
                    put("description", "消息源名称，如 wechat_monitor / codex_queue / email_checker")
                }
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "拉取的 API URL，返回 JSON 数组 [{source, content}]")
                }
                putJsonObject("interval_seconds") {
                    put("type", "integer")
                    put("description", "轮询间隔（秒），最小 30 秒")
                }
                putJsonObject("headers") {
                    put("type", "string")
                    put("description", "可选：HTTP 请求头，JSON 格式，如 {\"Authorization\":\"Bearer xxx\"}")
                }
            },
            required = listOf("source", "url", "interval_seconds")
        )
    },
    execute = { params ->
        val obj = params.jsonObject
        val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: error("source is required")
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
        val interval = (obj["interval_seconds"]?.jsonPrimitive?.intOrNull ?: 60).coerceAtLeast(30)
        val headers = obj["headers"]?.jsonPrimitive?.contentOrNull
        val store = getSettingsStore()
        store.addPollingTask(PollingTask(source, url, interval, headers))
        listOf(UIMessagePart.Text("轮询任务已注册: $source（每 $interval 秒拉取 $url）。消息将自动存入待处理区，下次唤醒时可读取。"))
    },
    needsApproval = false,
)
