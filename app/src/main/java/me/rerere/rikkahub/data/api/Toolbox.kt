/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（Toolbox：开发者工具箱，纯函数集合）
 */

package me.rerere.rikkahub.data.api

import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

/**
 * 开发者工具箱：Base64 / 时间戳 / 密码生成 / JSON 格式化 / 颜色混合 / 进制转换 / 正则测试.
 */
object Toolbox {
    private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

    private val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private val DIGITS = "23456789"
    private val SYMBOLS = "!@#$%^&*()_+-="

    fun base64Encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun base64Decode(text: String): String = runCatching {
        String(Base64.getDecoder().decode(text), Charsets.UTF_8)
    }.getOrDefault("")

    /** 日期字符串 → 时间戳（秒） */
    fun dateToTimestamp(dateStr: String): String {
        return runCatching {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            (sdf.parse(dateStr)?.time ?: 0L) / 1000
        }.getOrElse { "日期格式错误, 用 yyyy-MM-dd HH:mm:ss" }.toString()
    }

    /** 时间戳（秒）→ 日期字符串 */
    fun timestampToDate(timestamp: Long): String {
        return runCatching {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            sdf.format(Date(timestamp * 1000))
        }.getOrDefault("")
    }

    /** 生成随机密码 */
    fun generatePassword(length: Int, useUpper: Boolean, useLower: Boolean, useDigit: Boolean, useSymbol: Boolean): String {
        val pool = buildString {
            if (useUpper) append(UPPER)
            if (useLower) append(LOWER)
            if (useDigit) append(DIGITS)
            if (useSymbol) append(SYMBOLS)
        }
        if (pool.isEmpty()) return "至少选一种字符"
        val sb = StringBuilder()
        repeat(length.coerceAtLeast(1)) {
            sb.append(pool.random())
        }
        return sb.toString()
    }

    /** JSON 格式化（缩进 2 空格） */
    fun jsonPretty(text: String): String {
        return runCatching {
            val json = org.json.JSONObject(text)
            json.toString(2)
        }.getOrElse {
            runCatching {
                val arr = org.json.JSONArray(text)
                arr.toString(2)
            }.getOrDefault(text)
        }
    }

    /** 十六进制颜色值转整数 */
    private fun hexToInt(hex: String, default: Int): Int {
        return runCatching {
            hex.removePrefix("#").toLong(16).toInt()
        }.getOrDefault(default)
    }

    /** 十六进制颜色值转 Color 整数（0xAARRGGBB） */
    fun hexToColor(hex: String): Int {
        val raw = hex.removePrefix("#")
        val v = runCatching { raw.toLong(16) }.getOrDefault(0xFF000000L)
        return when (raw.length) {
            6 -> (0xFF000000L or v).toInt()
            8 -> v.toInt()
            else -> 0xFF000000.toInt()
        }
    }

    /** 混合两种颜色 */
    fun mixColors(color1: String, color2: String, ratio: Double): String {
        val c1 = hexToInt(color1, 0)
        val c2 = hexToInt(color2, 0)
        val r = ratio.coerceIn(0.0, 1.0)
        fun mixChannel(shift: Int): Int {
            val a = (c1 shr shift) and 0xFF
            val b = (c2 shr shift) and 0xFF
            return (a * (1 - r) + b * r).toInt().coerceIn(0, 255)
        }
        val red = mixChannel(16)
        val green = mixChannel(8)
        val blue = mixChannel(0)
        return String.format("#%02X%02X%02X", red, green, blue)
    }

    /** 进制转换（value 字符串，fromBase → toBase） */
    fun radixConvert(value: String, fromBase: Int, toBase: Int): String {
        return runCatching {
            val num = value.toLongOrNull(fromBase) ?: value.toLongOrNull() ?: return "无效数字"
            java.lang.Long.toString(num, toBase).uppercase(Locale.ROOT)
        }.getOrDefault("")
    }

    /** 正则测试，返回所有匹配 */
    fun regexTest(text: String, pattern: String): List<String> {
        if (pattern.isEmpty()) return emptyList()
        return runCatching {
            Regex(pattern).findAll(text).map { it.value }.toList()
        }.getOrDefault(emptyList())
    }

    /** 颜色名称（按 RGB 判断色系） */
    fun colorName(r: Int, g: Int, b: Int): String {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val sat = max - min
        // 黑白灰
        if (sat < 30) {
            return if (max < 80) "黑色系" else if (max > 200) "白色系" else "灰色系"
        }
        return when (max) {
            r -> if (g > b) "橙色系" else "红色系"
            g -> if (r > b) "黄色系" else "绿色系"
            else -> if (r > g) "紫色系" else "蓝色系"
        }
    }
}
