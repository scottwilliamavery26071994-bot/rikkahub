package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings

/**
 * 本地待办工具（#1113）：AI 可创建/列出/完成/删除待办事项。
 * 纯本地存储（随 Settings 持久化），不依赖任何 API。
 */
fun createTodoTools(
    settings: Settings,
    onUpdateTodos: (List<TodoItem>) -> Unit,
): List<Tool> = listOf(
    Tool(
        name = "todo_list",
        description = "List the current todo items. Use when the user asks about their tasks/todos.",
        parameters = {
            InputSchema.Obj(properties = buildJsonObject { })
        },
        execute = {
            val items = settings.todos
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("count", items.size)
                    put("todos", buildJsonArray {
                        items.forEach { t ->
                            add(buildJsonObject {
                                put("id", t.id)
                                put("title", JsonPrimitive(t.title))
                                put("done", t.done)
                            })
                        }
                    })
                }.toString()
            ))
        },
        needsApproval = false,
    ),
    Tool(
        name = "todo_add",
        description = "Add a new todo item. Use when the user asks to remember a task or create a todo.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "The task description")
                    }
                },
                required = listOf("title")
            )
        },
        execute = { params ->
            val title = params.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
            val item = TodoItem(title = title)
            onUpdateTodos(settings.todos + item)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("action", JsonPrimitive("add"))
                    put("id", item.id)
                    put("message", JsonPrimitive("Todo added"))
                }.toString()
            ))
        },
        needsApproval = false,
    ),
    Tool(
        name = "todo_done",
        description = "Mark a todo item as done (or undone). Use when the user completes a task.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "The todo id from todo_list")
                    }
                    putJsonObject("done") {
                        put("type", "boolean")
                        put("description", "true = mark done, false = reopen")
                    }
                },
                required = listOf("id")
            )
        },
        execute = { params ->
            val id = params.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val done = params.jsonObject["done"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
            val updated = settings.todos.map { if (it.id == id) it.copy(done = done) else it }
            onUpdateTodos(updated)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("action", JsonPrimitive("done"))
                    put("id", JsonPrimitive(id))
                    put("done", done)
                }.toString()
            ))
        },
        needsApproval = false,
    ),
    Tool(
        name = "todo_delete",
        description = "Delete a todo item. Use when the user removes a task.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "The todo id from todo_list")
                    }
                },
                required = listOf("id")
            )
        },
        execute = { params ->
            val id = params.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            onUpdateTodos(settings.todos.filter { it.id != id })
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("action", JsonPrimitive("delete"))
                    put("id", JsonPrimitive(id))
                }.toString()
            ))
        },
        needsApproval = false,
    ),
)

@kotlinx.serialization.Serializable
data class TodoItem(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val title: String,
    val done: Boolean = false,
)
