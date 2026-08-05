/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（ApkReverse：APK 文件静态分析）
 */

package me.rerere.rikkahub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * APK 静态分析结果.
 */
data class ApkReverseResult(
    val fileName: String,
    val fileSize: Long,
    val packageName: String,
    val versionName: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val interfaces: List<String>,
    val error: String,
)

/**
 * APK 逆向分析工具：解包并提取包名/版本/权限/组件/接口等信息.
 */
object ApkReverse {
    private val URL_REGEX = Regex("https?://[a-zA-Z0-9._\\-]+(?:/[a-zA-Z0-9_/\\-]*)?")
    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val VERSION_REGEX = Regex("^\\d+(\\.\\d+)+$")

    /**
     * 分析 APK 文件.
     */
    suspend fun reverse(file: File): ApkReverseResult = withContext(Dispatchers.IO) {
        try {
            val strings = extractStrings(file)

            val packageName = strings.firstOrNull { PACKAGE_REGEX.matches(it) && !it.contains("android.") } ?: ""
            val versionName = strings.firstOrNull { VERSION_REGEX.matches(it) } ?: ""

            val permissions = strings.filter { it.startsWith("android.permission.") }.distinct()
            val activities = strings.filter { it.startsWith("android.intent.action.") || it.endsWith(".MainActivity") }.distinct()
            val services = strings.filter { it.contains("Service") }.distinct()
            val interfaces = extractInterfaces(file)

            ApkReverseResult(
                fileName = file.name,
                fileSize = file.length(),
                packageName = packageName,
                versionName = versionName,
                permissions = permissions,
                activities = activities,
                services = services,
                interfaces = interfaces,
                error = "",
            )
        } catch (e: Exception) {
            ApkReverseResult(
                fileName = file.name,
                fileSize = file.length(),
                packageName = "",
                versionName = "",
                permissions = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                interfaces = emptyList(),
                error = e.message ?: "未知错误",
            )
        }
    }

    /**
     * 提取 APK 内 .dex/.xml/.arsc 中的全部 ASCII 字符串（长度 >= 6）.
     */
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

    /**
     * 从字节数组中提取连续的可打印 ASCII 字符串（最小长度 6）.
     */
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

    /**
     * 提取 APK 内出现的外部 URL/接口地址.
     */
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
}
