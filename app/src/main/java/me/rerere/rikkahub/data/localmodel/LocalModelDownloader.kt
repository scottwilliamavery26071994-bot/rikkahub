/*
 * 灵犀 Lingxi - 本地模型下载管理
 */

package me.rerere.rikkahub.data.localmodel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
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

/**
 * 可下载模型列表。
 * URL 来自 HuggingFace 官方仓库，已验证可访问。
 * 如模型不存在，请到 https://huggingface.co/models?library=onnx 查找。
 */
/**
 * 可下载模型列表 — URL 均已 curl 验证。
 */
val AVAILABLE_MODELS = listOf(
    AvailableModel(
        id = "deepseek-r1-0528",
        name = "DeepSeek R1 0528 (8B) ONNX",
        description = "推理增强 · INT4量化 · ~7.7GB",
        size = "model.onnx 418KB + model.onnx.data 7.3GB",
        downloadUrl = "https://huggingface.co/keisuke-miyako/DeepSeek-R1-0528-Qwen3-8B-onnx-int4/resolve/main/model.onnx",
        filename = "model.onnx",
    ),
    AvailableModel(
        id = "deepseek-r1-0528-data",
        name = "DeepSeek R1 0528 权重文件",
        description = "⚠️ 必须下载！与model.onnx放在同一目录",
        size = "model.onnx.data 7.3GB",
        downloadUrl = "https://huggingface.co/keisuke-miyako/DeepSeek-R1-0528-Qwen3-8B-onnx-int4/resolve/main/model.onnx.data",
        filename = "model.onnx.data",
    ),
)

class LocalModelDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
        .writeTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
        .build()
) {
    fun download(model: AvailableModel): Flow<DownloadProgress> = callbackFlow {
        trySend(DownloadProgress.Started(model))

        val modelsDir = File(context.filesDir, "local_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val outputFile = File(modelsDir, model.filename)

        if (outputFile.exists() && outputFile.length() > 100 && isValidOnnxFile(outputFile)) {
            Log.d(TAG, "Model already exists: ${outputFile.absolutePath}")
            trySend(DownloadProgress.Completed(outputFile.absolutePath))
            close()
            return@callbackFlow
        }

        launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "Lingxi-Android/1.0")
                    .build()
                val response = client.newCall(request).execute()

                val contentType = response.header("Content-Type", "")
                if (contentType != null && (contentType.contains("text/html") || contentType.contains("text/plain"))) {
                    trySend(DownloadProgress.Error("下载链接无效"))
                    response.close()
                    close()
                    return@launch
                }
                if (!response.isSuccessful) {
                    trySend(DownloadProgress.Error("HTTP ${response.code}"))
                    response.close()
                    close()
                    return@launch
                }
                val body = response.body
                if (body == null) {
                    trySend(DownloadProgress.Error("响应体为空"))
                    response.close()
                    close()
                    return@launch
                }

                val contentLength = body.contentLength()
                if (contentLength > 0 && contentLength < 100_000) {
                    trySend(DownloadProgress.Error("文件太小"))
                    body.close(); response.close(); close(); return@launch
                }

                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { out ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n); downloaded += n
                            if (contentLength > 0 && downloaded % (contentLength / 20) < 8192) {
                                trySend(DownloadProgress.Progress(
                                    (downloaded * 100 / contentLength).toInt(), downloaded, contentLength))
                            }
                        }
                    }
                }
                body.close(); response.close()

                if (!isValidOnnxFile(outputFile)) {
                    outputFile.delete()
                    trySend(DownloadProgress.Error("文件校验失败"))
                } else {
                    trySend(DownloadProgress.Completed(outputFile.absolutePath))
                }
            } catch (e: Exception) {
                trySend(DownloadProgress.Error(e.message ?: "下载失败"))
            }
            close()
        }
        awaitClose()
    }

    fun getExistingModelPath(modelId: String): String? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(context.filesDir, "local_models/${model.filename}")
        return if (file.exists() && file.length() > 100) file.absolutePath else null
    }

    companion object {
        /** ONNX 文件魔数: 0x08 (protobuf wire type) */
        fun isValidOnnxFile(file: File): Boolean {
            if (!file.exists() || file.length() < 8) return false
            return try {
                FileInputStream(file).use {
                    val magic = it.read() // 第一个字节应为 0x08 (protobuf varint field 1)
                    // ONNX protobuf 格式: 0x08 开头
                    magic == 0x08
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}

sealed class DownloadProgress {
    data class Started(val model: AvailableModel) : DownloadProgress()
    data class Progress(val percent: Int, val downloaded: Long, val total: Long) : DownloadProgress()
    data class Completed(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
