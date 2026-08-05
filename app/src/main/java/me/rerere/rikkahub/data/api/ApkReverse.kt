/*
 * 灵犀 Lingxi
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * APK 逆向分析: 解包解析 AndroidManifest(AXML) + dex 类名/方法 + 字符串/接口
 */

package me.rerere.rikkahub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/**
 * APK 静态分析结果.
 */
data class ApkReverseResult(
    val fileName: String = "",
    val fileSize: Long = 0,
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val interfaces: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
    val dexCount: Int = 0,
    val error: String = "",
)

/**
 * APK 逆向分析工具: 解析二进制 AndroidManifest(AXML) + dex 类名 + 字符串/接口.
 */
object ApkReverse {
    private val URL_REGEX = Regex("https?://[a-zA-Z0-9._\\-]+(?:/[a-zA-Z0-9_/\\-]*)?")
    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val VERSION_REGEX = Regex("^\\d+(\\.\\d+)+$")

    /** 常见库前缀, 过滤不显示 */
    private val LIB_PREFIX = listOf(
        "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.",
        "okhttp3.", "retrofit2.", "org.json", "org.xml", "com.google.",
        "com.squareup.", "io.ktor.", "org.jetbrains.", "kotlin.",
    )

    /**
     * 分析 APK 文件.
     */
    suspend fun reverse(file: File): ApkReverseResult = withContext(Dispatchers.IO) {
        try {
            // 1. 解析二进制 AndroidManifest.xml (AXML)
            val manifestInfo = parseManifest(file)
            // 2. 提取 dex 字符串
            val strings = extractStrings(file)
            // 3. dex 类名
            val classes = extractClasses(strings)
            // 4. 权限
            val permissions = strings.filter { it.startsWith("android.permission.") }.distinct()
            // 5. 外部接口
            val interfaces = extractInterfaces(file)

            ApkReverseResult(
                fileName = file.name,
                fileSize = file.length(),
                packageName = manifestInfo.packageName.ifBlank {
                    strings.firstOrNull { PACKAGE_REGEX.matches(it) && !it.contains("android.") } ?: ""
                },
                versionName = manifestInfo.versionName.ifBlank {
                    strings.firstOrNull { VERSION_REGEX.matches(it) } ?: ""
                },
                versionCode = manifestInfo.versionCode,
                minSdk = manifestInfo.minSdk,
                targetSdk = manifestInfo.targetSdk,
                permissions = permissions,
                activities = manifestInfo.activities.distinct(),
                services = manifestInfo.services.distinct(),
                receivers = manifestInfo.receivers.distinct(),
                providers = manifestInfo.providers.distinct(),
                interfaces = interfaces,
                classes = classes,
                dexCount = countDex(file),
                error = "",
            )
        } catch (e: Exception) {
            ApkReverseResult(
                fileName = file.name, fileSize = file.length(),
                packageName = "", versionName = "", versionCode = "", minSdk = "", targetSdk = "",
                permissions = emptyList(), activities = emptyList(), services = emptyList(),
                receivers = emptyList(), providers = emptyList(), interfaces = emptyList(),
                classes = emptyList(), dexCount = 0, error = e.message ?: "未知错误",
            )
        }
    }

    // ==================== AndroidManifest AXML 解析 ====================

    private data class ManifestInfo(
        var packageName: String,
        var versionName: String,
        var versionCode: String,
        var minSdk: String,
        var targetSdk: String,
        val activities: MutableList<String> = mutableListOf(),
        val services: MutableList<String> = mutableListOf(),
        val receivers: MutableList<String> = mutableListOf(),
        val providers: MutableList<String> = mutableListOf(),
    )

    private fun parseManifest(file: File): ManifestInfo {
        val info = ManifestInfo("", "", "", "", "")
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return info
            zip.getInputStream(entry).use { input ->
                val bytes = input.readBytes()
                parseAXml(bytes, info)
            }
        }
        return info
    }

    private fun parseAXml(bytes: ByteArray, info: ManifestInfo) {
        var pos = 0
        fun u16(p: Int): Int = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
        fun u32(p: Int): Long =
            ((bytes[p].toLong() and 0xFF) shl 24) or ((bytes[p + 1].toLong() and 0xFF) shl 16) or
                ((bytes[p + 2].toLong() and 0xFF) shl 8) or (bytes[p + 3].toLong() and 0xFF)

        if (u32(0) != 0x00080003L) return  // 不是 AXML
        pos = 8
        // String pool
        var stringPoolOff = -1
        var stringDataOff = -1
        var stringCount = 0
        var isUtf8 = false
        val chunkType = u16(pos)
        if (chunkType == 0x0001) {
            val headerSize = u16(pos + 2)
            val chunkSize = u32(pos + 4).toInt()
            stringCount = u32(pos + 8).toInt()
            val flags = u32(pos + 16).toInt()
            val stringsStart = u32(pos + 20).toInt()
            isUtf8 = flags and 0x100 != 0
            stringPoolOff = pos + headerSize  // 索引表位置
            stringDataOff = pos + stringsStart // 字符串数据位置 (相对 chunk)
            pos += chunkSize
        }
        // String 读取
        fun readString(index: Int): String {
            if (index < 0 || index >= stringCount) return ""
            if (stringPoolOff < 0 || stringDataOff < 0) return ""
            val offset = u32(stringPoolOff + index * 4).toInt()
            val off = stringDataOff + offset
            if (off >= bytes.size) return ""
            return if (isUtf8) {
                // UTF-8: 长度(变长) + 实际长度(变长) + 字节
                var p = off
                fun readLen(): Int {
                    if (p >= bytes.size) return 0
                    val b = bytes[p].toInt() and 0xFF
                    p++
                    return if (b and 0x80 != 0) ((b and 0x7F) shl 8) or (bytes.getOrElse(p++) { 0 }.toInt() and 0xFF) else b
                }
                readLen()  // char len (忽略)
                val byteLen = readLen()
                if (byteLen <= 0 || p + byteLen > bytes.size) return ""
                String(bytes, p, byteLen, Charsets.UTF_8)
            } else {
                // UTF-16LE: 字符数(变长) + 字符
                var p = off
                fun readLen(): Int {
                    if (p >= bytes.size) return 0
                    val b = bytes[p].toInt() and 0xFF
                    p++
                    return if (b and 0x80 != 0) ((b and 0x7F) shl 8) or (bytes.getOrElse(p++) { 0 }.toInt() and 0xFF) else b
                }
                val len = readLen()
                val sb = StringBuilder()
                for (i in 0 until len) {
                    if (p + 1 >= bytes.size) break
                    val lo = bytes[p++].toInt() and 0xFF
                    val hi = bytes[p++].toInt() and 0xFF
                    sb.append((hi shl 8 or lo).toChar())
                }
                sb.toString()
            }
        }

        // 遍历元素
        while (pos + 8 <= bytes.size) {
            val type = u16(pos)
            val size = u32(pos + 4).toInt()
            if (size <= 0 || pos + size > bytes.size) break
            when (type) {
                0x0102 -> {  // StartElement
                    val nameIdx = u32(pos + 8).toInt()
                    val attrStart = u32(pos + 12).toInt()
                    val attrCount = u32(pos + 16).toInt()
                    val elementName = readString(nameIdx)
                    val attrs = mutableMapOf<String, String>()
                    var ap = pos + attrStart
                    for (i in 0 until attrCount) {
                        if (ap + 20 > bytes.size) break
                        val nameIdx2 = u32(ap + 4).toInt()
                        val rawIdx = u32(ap + 8).toInt()
                        val typedValue = u32(ap + 12)
                        val data = u32(ap + 16)
                        val dataType = ((typedValue shr 16) and 0xFF).toInt()
                        val attrName = readString(nameIdx2)
                        val value = when {
                            rawIdx >= 0 -> readString(rawIdx)
                            dataType == 0x12 -> if (data == 0L) "true" else "false" // TYPE_INT_BOOLEAN
                            dataType == 0x10 -> data.toString()                      // TYPE_INT_DEC
                            dataType == 0x11 -> "0x" + data.toString(16)            // TYPE_INT_HEX
                            else -> ""
                        }
                        attrs[attrName] = value
                        ap += 20
                    }
                    // 收集
                    when (elementName) {
                        "manifest" -> {
                            attrs["package"]?.let { info.packageName = it }
                            attrs["versionName"]?.let { info.versionName = it }
                            attrs["versionCode"]?.let { info.versionCode = it }
                        }
                        "uses-sdk" -> {
                            attrs["minSdkVersion"]?.let { info.minSdk = it }
                            attrs["targetSdkVersion"]?.let { info.targetSdk = it }
                        }
                        "activity" -> attrs["name"]?.let { info.activities += it }
                        "service" -> attrs["name"]?.let { info.services += it }
                        "receiver" -> attrs["name"]?.let { info.receivers += it }
                        "provider" -> attrs["name"]?.let { info.providers += it }
                    }
                }
            }
            pos += size
        }
    }

    // ==================== dex 类名提取 ====================

    private fun extractClasses(strings: List<String>): List<String> {
        // dex 类名格式: Lcom/example/Foo; 或 Lcom/example/Foo$Inner;
        val classes = strings
            .mapNotNull { s ->
                if (s.length < 5 || s[0] != 'L') null
                else {
                    val end = s.indexOf(';')
                    if (end <= 1) null else s.substring(1, end)
                }
            }
            .map { it.replace('/', '.') }
            .filter { it.length >= 3 && !it.startsWith("L") }
            .filter { cls ->
                LIB_PREFIX.none { it in cls } && cls.count { c -> c == '.' } >= 1
            }
            .distinct()
        // 去掉 $ 内部类, 保留顶层类, 按名称排序
        return classes
            .filterNot { it.contains('$') }
            .sorted()
            .take(200)
    }

    // ==================== 字符串/接口提取 ====================

    private fun extractStrings(file: File): List<String> {
        val result = mutableListOf<String>()
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (!(name.endsWith(".dex") || name.endsWith(".xml") || name.endsWith(".arsc"))) continue
                zip.getInputStream(entry).use { input ->
                    result += extractAsciiStrings(input.readBytes())
                }
            }
        }
        return result
    }

    private fun extractAsciiStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7F) {
                sb.append(v.toChar())
            } else {
                if (sb.length >= 6) result += sb.toString()
                sb.setLength(0)
            }
        }
        if (sb.length >= 6) result += sb.toString()
        return result
    }

    private fun extractInterfaces(file: File): List<String> {
        val result = mutableSetOf<String>()
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (!(name.endsWith(".dex") || name.endsWith(".xml") || name.endsWith(".arsc"))) continue
                zip.getInputStream(entry).use { input ->
                    val text = extractAsciiStrings(input.readBytes()).joinToString("\n")
                    URL_REGEX.findAll(text).forEach { result += it.value }
                }
            }
        }
        return result.toList()
    }

    private fun countDex(file: File): Int {
        var count = 0
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("classes") && name.endsWith(".dex")) count++
            }
        }
        return count
    }
}
