/*
 * 灵犀 Lingxi - 本地模型下载管理
 */

package me.rerere.rikkahub.data.localmodel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

private const val TAG = "LocalModelDownloader"

data class AvailableModel(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val downloadUrl: String,
    val filename: String,
)

val AVAILABLE_MODELS = listOf(
    AvailableModel(
        id = "qwen2.5-0.5b",
        name = "Qwen 2.5 (0.5B)",
        description = "阿里通义千问，0.5B参数，极致轻量",
        size = "~400MB",
        downloadUrl = "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct-ONNX/resolve/main/onnx/model_q4.onnx",
        filename = "qwen2.5-0.5b-q4.onnx",
    ),
    AvailableModel(
        id = "smollm2-135m",
        name = "SmolLM2 (135M)",
        description = "HuggingFace出品，135M超轻量",
        size = "~250MB",
        downloadUrl = "https://huggingface.co/onnx-community/SmolLM2-135M-Instruct-ONNX/resolve/main/onnx/model_q4.onnx",
        filename = "smollm2-135m-q4.onnx",
    ),
    AvailableModel(
        id = "tinyllama-1.1b",
        name = "TinyLlama (1.1B)",
        description = "轻量通用模型，1.1B参数",
        size = "~650MB",
        downloadUrl = "https://huggingface.co/onnx-community/TinyLlama-1.1B-Chat-v1.0-ONNX/resolve/main/onnx/model_q4.onnx",
        filename = "tinyllama-1.1b-q4.onnx",
    ),
    AvailableModel(
        id = "phi3-mini",
        name = "Phi-3 Mini (3.8B)",
        description = "微软出品，3.8B参数，性能强劲",
        size = "~2.2GB",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-cpu-int4.onnx",
        filename = "phi3-mini-4k-q4.onnx",
    ),
    AvailableModel(
        id = "deepseek-r1-0528",
        name = "DeepSeek R1 (1.5B)",
        description = "推理增强模型，逻辑能力强",
        size = "~900MB",
        downloadUrl = "https://huggingface.co/onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX/resolve/main/onnx/model_q4.onnx",
        filename = "deepseek-r1-1.5b-q4.onnx",
    ),
)

class LocalModelDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun download(model: AvailableModel): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Started(model))

        val modelsDir = File(context.filesDir, "local_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val outputFile = File(modelsDir, model.filename)

        if (outputFile.exists()) {
            emit(DownloadProgress.Completed(outputFile.absolutePath))
            return@flow
        }

        val request = Request.Builder().url(model.downloadUrl).build()
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            emit(DownloadProgress.Error("下载失败: HTTP ${response.code}"))
            return@flow
        }

        val body = response.body ?: run {
            emit(DownloadProgress.Error("响应体为空"))
            return@flow
        }

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        withContext(Dispatchers.IO) {
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                    }
                }
            }
        }

        body.close()
        response.close()

        emit(DownloadProgress.Completed(outputFile.absolutePath))
    }.flowOn(Dispatchers.IO)

    fun getExistingModelPath(modelId: String): String? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(context.filesDir, "local_models/${model.filename}")
        return if (file.exists()) file.absolutePath else null
    }
}

sealed class DownloadProgress {
    data class Started(val model: AvailableModel) : DownloadProgress()
    data class Progress(val percent: Int, val downloaded: Long, val total: Long) : DownloadProgress()
    data class Completed(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
