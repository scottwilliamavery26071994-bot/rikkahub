package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlin.math.sqrt

/**
 * 向量索引助手（#1657）。
 *
 * 集成 sqlite-vec 原生库（libvec0.so 已随 APK 打包到 jniLibs）。
 * Android framework SQLite 不支持 load_extension，因此这里提供：
 * 1. 原生 so 检测（供后续通过 SQLCipher/bundled SQLite 加载 vec0 扩展）
 * 2. 纯 Kotlin 词袋向量检索 fallback（零依赖，功能始终可用）
 *
 * 未来接入点：拿到支持扩展的 SQLite 后，用 vec0 的 SQL 完成
 * 真正的向量近似检索（余弦距离），替换 [cosineScore]。
 */
object VectorIndexHelper {
    private const val TAG = "VectorIndexHelper"

    @Volatile
    private var nativeAvailable: Boolean? = null

    /** 检测 libvec0.so 是否可加载（arm64-v8a 已打包） */
    fun isNativeAvailable(): Boolean {
        nativeAvailable?.let { return it }
        return try {
            System.loadLibrary("vec0")
            nativeAvailable = true
            Log.i(TAG, "libvec0.so loaded successfully")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "libvec0.so not loadable: ${e.message}")
            nativeAvailable = false
            false
        }
    }

    /**
     * 纯 Kotlin 词袋向量检索：把文本转成哈希词频向量，计算余弦相似度。
     * 无原生依赖，离线可用。
     */
    fun cosineScore(query: String, doc: String): Double {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return 0.0
        val docTokens = tokenize(doc)
        if (docTokens.isEmpty()) return 0.0

        val qVec = hashVector(queryTokens)
        val dVec = hashVector(docTokens)
        return cosine(qVec, dVec)
    }

    private fun tokenize(text: String): List<String> = text.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }

    /** 简单哈希词袋：字符 n-gram 计数（小向量，无需嵌入模型） */
    private fun hashVector(tokens: List<String>): Map<Int, Int> {
        val vec = HashMap<Int, Int>()
        for (token in tokens) {
            // 用 token 的 hash 映射到 1024 维桶
            val bucket = (token.hashCode() and 0x7fffffff) % 1024
            vec[bucket] = (vec[bucket] ?: 0) + 1
        }
        return vec
    }

    private fun cosine(a: Map<Int, Int>, b: Map<Int, Int>): Double {
        var dot = 0L
        for ((k, v) in a) {
            b[k]?.let { dot += v.toLong() * it }
        }
        val normA = sqrt(a.values.sumOf { it.toLong() * it }.toDouble())
        val normB = sqrt(b.values.sumOf { it.toLong() * it }.toDouble())
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (normA * normB)
    }
}
