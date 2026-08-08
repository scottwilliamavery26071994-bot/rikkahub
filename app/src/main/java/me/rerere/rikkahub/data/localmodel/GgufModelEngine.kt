/*
 * 灵犀 Lingxi - GGUF 模型推理引擎
 * 需要手动集成 llama.cpp 原生库
 *
 * 集成步骤:
 * 1. 下载 llama.cpp Android 预编译库
 *    https://github.com/ggerganov/llama.cpp/releases
 * 2. 将 .so 文件放入 app/src/main/jniLibs/arm64-v8a/
 * 3. 添加 llama.cpp Java 绑定到项目
 *    参考: llama.cpp/android/ 目录下的 Java 代码
 */

package me.rerere.rikkahub.data.localmodel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "GgufModelEngine"

class GgufModelEngine(
    private val modelPath: String
) {
    private var loaded = false

    val isLoaded: Boolean get() = loaded

    fun load(): Result<Unit> = runCatching {
        // llama.cpp 原生库通过 JNI 加载
        try {
            System.loadLibrary("llama")
            Log.d(TAG, "llama.cpp native library loaded")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "llama.cpp native library not found. Place libllama.so in jniLibs/arm64-v8a/")
            throw IllegalStateException(
                "需要 llama.cpp 原生库。请将 libllama.so 放入 app/src/main/jniLibs/arm64-v8a/"
            )
        }
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        require(isLoaded) { "GGUF model not loaded" }
        emit("")
    }.flowOn(Dispatchers.IO)

    fun close() { loaded = false }
}
