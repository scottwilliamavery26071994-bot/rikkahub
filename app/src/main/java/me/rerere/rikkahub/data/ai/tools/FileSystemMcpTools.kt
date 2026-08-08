package me.rerere.rikkahub.data.ai.tools

import android.content.Context
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fileInfo(f: File): String = buildString {
    append("${if (f.isDirectory) "📁" else "📄"} ${f.name}")
    append(" | ${if (f.isDirectory) "${f.list()?.size ?: 0} 项" else "${f.length() / 1024.0} KB"}")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    append(" | ${sdf.format(Date(f.lastModified()))}")
    if (f.isHidden) append(" | 隐藏")
    if (f.canExecute()) append(" | 可执行")
}

private fun dirTree(dir: File, prefix: String, maxDepth: Int, currentDepth: Int, maxItems: Int): String {
    if (currentDepth > maxDepth) return ""
    val sb = StringBuilder()
    val files = dir.listFiles()?.sortedBy { it.name }?.take(maxItems) ?: return ""
    files.forEachIndexed { i, f ->
        val isLast = i == files.size - 1
        val connector = if (isLast) "└── " else "├── "
        sb.append("$prefix$connector${if (f.isDirectory) "📁" else "📄"} ${f.name}")
        if (f.isFile) sb.append(" (${f.length() / 1024.0}KB)")
        sb.append("\n")
        if (f.isDirectory && currentDepth < maxDepth) {
            val newPrefix = prefix + if (isLast) "    " else "│   "
            sb.append(dirTree(f, newPrefix, maxDepth, currentDepth + 1, maxItems))
        }
    }
    return sb.toString()
}

fun buildFileSystemMcpTools(context: Context): List<Tool> = buildList {

    // === 读文件 ===
    add(Tool(
        name = "fs_read_file",
        description = "读取文本文件内容。Params: path(文件路径)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","文件路径") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            if (!f.exists()) return@Tool listOf(UIMessagePart.Text("""{"error":"文件不存在: $path"}"""))
            if (f.isDirectory) return@Tool listOf(UIMessagePart.Text("""{"error":"是目录不是文件: $path"}"""))
            val content = try { f.readText().take(8000) } catch (e: Exception) { "读取失败: ${e.message}" }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("path", path); put("size", f.length()); put("content", content)
            }.toString()))
        },
    ))

    // === 读多个文件 ===
    add(Tool(
        name = "fs_read_multiple",
        description = "批量读取多个文件。Params: paths(JSON数组如[\"a.txt\",\"b.txt\"])。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("paths", buildJsonObject { put("type","string"); put("description","文件路径JSON数组") })
            }, required = listOf("paths"))
        },
        execute = { args ->
            val pathsStr = args.jsonObject["paths"]?.jsonPrimitive?.contentOrNull ?: error("paths required")
            val paths = kotlinx.serialization.json.Json.parseToJsonElement(pathsStr).jsonArray.map { it.jsonPrimitive.content }
            val results = paths.map { path ->
                val f = File(path)
                val content = if (f.exists() && f.isFile) f.readText().take(4000) else "不存在或非文件"
                "--- $path ---\n$content"
            }
            listOf(UIMessagePart.Text(results.joinToString("\n\n")))
        },
    ))

    // === 写文件 ===
    add(Tool(
        name = "fs_write_file",
        description = "写入文本文件。Params: path(文件路径), content(内容)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","文件路径") })
                put("content", buildJsonObject { put("type","string"); put("description","文件内容") })
            }, required = listOf("path","content"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
            val f = File(path)
            f.parentFile?.mkdirs()
            f.writeText(content)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("path", path); put("bytes", content.length)
            }.toString()))
        },
    ))

    // === 创建目录 ===
    add(Tool(
        name = "fs_create_directory",
        description = "创建目录（含父目录）。Params: path(目录路径)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","目录路径") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            val ok = f.mkdirs()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", ok || f.exists()); put("path", path)
            }.toString()))
        },
    ))

    // === 列目录 ===
    add(Tool(
        name = "fs_list_directory",
        description = "列出目录内容。Params: path(目录路径)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","目录路径") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            if (!f.exists()) return@Tool listOf(UIMessagePart.Text("""{"error":"目录不存在"}"""))
            if (!f.isDirectory) return@Tool listOf(UIMessagePart.Text("""{"error":"不是目录"}"""))
            val files = f.listFiles()?.sortedBy { it.name } ?: emptyList()
            val text = if (files.isEmpty()) "空目录" else files.joinToString("\n") { fileInfo(it) }
            listOf(UIMessagePart.Text("📁 ${f.absolutePath}\n$text"))
        },
    ))

    // === 目录树 ===
    add(Tool(
        name = "fs_directory_tree",
        description = "显示目录树结构。Params: path(目录路径), depth(深度默认3), maxItems(每层最大项数默认20)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","目录路径") })
                put("depth", buildJsonObject { put("type","integer"); put("description","深度默认3") })
                put("maxItems", buildJsonObject { put("type","integer"); put("description","每层最大项数") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val depth = o["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 3
            val maxItems = o["maxItems"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
            val f = File(path)
            if (!f.exists() || !f.isDirectory) return@Tool listOf(UIMessagePart.Text("""{"error":"目录不存在"}"""))
            val tree = "📁 ${f.name}\n" + dirTree(f, "", depth, 1, maxItems)
            listOf(UIMessagePart.Text(tree))
        },
    ))

    // === 移动/重命名 ===
    add(Tool(
        name = "fs_move_file",
        description = "移动或重命名文件/目录。Params: source(源路径), destination(目标路径)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("source", buildJsonObject { put("type","string"); put("description","源路径") })
                put("destination", buildJsonObject { put("type","string"); put("description","目标路径") })
            }, required = listOf("source","destination"))
        },
        execute = { args ->
            val o = args.jsonObject
            val src = o["source"]?.jsonPrimitive?.contentOrNull ?: error("source required")
            val dst = o["destination"]?.jsonPrimitive?.contentOrNull ?: error("destination required")
            val ok = File(src).renameTo(File(dst))
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", ok); put("source", src); put("destination", dst)
            }.toString()))
        },
    ))

    // === 搜索文件 ===
    add(Tool(
        name = "fs_search_files",
        description = "搜索文件/目录。Params: path(搜索根目录), pattern(文件名匹配模式，支持*通配符)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","搜索根目录") })
                put("pattern", buildJsonObject { put("type","string"); put("description","文件名匹配如*.kt或*.txt") })
            }, required = listOf("path","pattern"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val pattern = o["pattern"]?.jsonPrimitive?.contentOrNull ?: error("pattern required")
            val root = File(path)
            if (!root.exists()) return@Tool listOf(UIMessagePart.Text("""{"error":"目录不存在"}"""))
            val regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex(RegexOption.IGNORE_CASE)
            val results = mutableListOf<String>()
            root.walkTopDown().take(500).forEach { f ->
                if (regex.matches(f.name)) results.add(fileInfo(f))
            }
            listOf(UIMessagePart.Text(if (results.isEmpty()) "未找到匹配文件" else "找到 ${results.size} 个结果:\n${results.take(50).joinToString("\n")}"))
        },
    ))

    // === 文件信息 ===
    add(Tool(
        name = "fs_get_file_info",
        description = "获取文件/目录详细信息。Params: path(路径)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","路径") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            if (!f.exists()) return@Tool listOf(UIMessagePart.Text("""{"error":"不存在"}"""))
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            listOf(UIMessagePart.Text(buildJsonObject {
                put("name", f.name); put("path", f.absolutePath)
                put("isDirectory", f.isDirectory); put("isFile", f.isFile)
                put("size", f.length()); put("canRead", f.canRead())
                put("canWrite", f.canWrite()); put("canExecute", f.canExecute())
                put("isHidden", f.isHidden); put("lastModified", sdf.format(Date(f.lastModified())))
            }.toString()))
        },
    ))

    // === 删除文件 ===
    add(Tool(
        name = "fs_delete_file",
        description = "删除文件或目录（目录递归删除）。Params: path(路径)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","路径") })
            }, required = listOf("path"))
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            listOf(UIMessagePart.Text(buildJsonObject { put("success", ok); put("path", path) }.toString()))
        },
    ))

    // === 复制文件 ===
    add(Tool(
        name = "fs_copy_file",
        description = "复制文件。Params: source(源路径), destination(目标路径)。",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("source", buildJsonObject { put("type","string"); put("description","源路径") })
                put("destination", buildJsonObject { put("type","string"); put("description","目标路径") })
            }, required = listOf("source","destination"))
        },
        execute = { args ->
            val o = args.jsonObject
            val src = o["source"]?.jsonPrimitive?.contentOrNull ?: error("source required")
            val dst = o["destination"]?.jsonPrimitive?.contentOrNull ?: error("destination required")
            val srcFile = File(src); val dstFile = File(dst)
            dstFile.parentFile?.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
            listOf(UIMessagePart.Text(buildJsonObject { put("success", true); put("source", src); put("destination", dst) }.toString()))
        },
    ))

    // === 追加写入 ===
    add(Tool(
        name = "fs_append_file",
        description = "追加内容到文件末尾。Params: path(文件路径), content(追加内容)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type","string"); put("description","文件路径") })
                put("content", buildJsonObject { put("type","string"); put("description","追加内容") })
            }, required = listOf("path","content"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
            val f = File(path)
            f.parentFile?.mkdirs()
            f.appendText(content)
            listOf(UIMessagePart.Text(buildJsonObject { put("success", true); put("path", path); put("size", f.length()) }.toString()))
        },
    ))
}
