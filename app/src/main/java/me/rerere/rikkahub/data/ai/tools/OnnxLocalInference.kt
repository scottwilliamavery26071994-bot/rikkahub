package me.rerere.rikkahub.data.ai.tools

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File

/**
 * 本地推理引擎（#34，开箱即用版）。
 *
 * 使用 ONNX Runtime Android（官方 Java API，库随 APK 打包，开箱即用）。
 * 模型文件放置到 files/models/ 后即可推理（无需编译 so、无需额外配置）。
 *
 * 注意：完整 LLM 需要模型文件（几百 MB），受 APK 体积限制无法内置；
 * 引擎已就绪，放入 .onnx 模型即触发推理。
 */
object OnnxLocalInference {
    private const val TAG = "OnnxLocalInference"

    /** 模型目录 */
    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    /** 列出可用 .onnx 模型 */
    fun listModels(context: Context): List<File> =
        modelsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".onnx") }
            ?.sortedBy { it.name } ?: emptyList()

    fun hasModel(context: Context): Boolean = listModels(context).isNotEmpty()

    /** 引擎可用性（库已打包，应始终 true） */
    fun isEngineAvailable(): Boolean = try {
        OrtEnvironment.getEnvironment()
        true
    } catch (e: Throwable) {
        Log.w(TAG, "ONNX Runtime unavailable: ${e.message}")
        false
    }

    /**
     * 推理：加载第一个模型并执行。
     * 输入按模型签名构造（此处为通用 float[1][seqLen] 占位，模型就绪后按需调整）。
     */
    fun run(context: Context, input: FloatArray): FloatArray {
        val model = listModels(context).firstOrNull() ?: return FloatArray(0)
        return try {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            env.createSession(model.absolutePath, options).use { session ->
                val inputName = session.inputNames.first()
                val tensor = ai.onnxruntime.OnnxTensor.createTensor(env, input)
                session.run(mapOf(inputName to tensor)).use { result ->
                    result[0]?.value as? FloatArray ?: FloatArray(0)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "ONNX inference failed: ${e.message}")
            FloatArray(0)
        }
    }
}
