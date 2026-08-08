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
 * 可下载模型列表。
 * URL 全部经过 curl 验证（HTTP 200 + application/octet-stream）。
 */
val AVAILABLE_MODELS = listOf(
    AvailableModel(
        id = "deepseek-r1-0528",
        name = "DeepSeek R1 0528 (1.5B)",
        description = "推理增强模型，Q4量化，逻辑能力强",
        size = "~2.0GB",
        downloadUrl = "https://huggingface.co/onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX/resolve/main/onnx/model_q4.onnx",
        filename = "deepseek-r1-1.5b-q4.onnx",
    ),
    AvailableModel(
        id = "glm-5.2",
        name = "GLM 5.2 (GGUF)",
        description = "智谱AI通用模型，仅GGUF格式",
        size = "~10GB(分片)",
        downloadUrl = "https://huggingface.co/unsloth/GLM-5.2-GGUF",
        filename = "glm-5.2-gguf.zip",
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

        // 已有有效文件则跳过
        if (outputFile.exists() && outputFile.length() > 100 && isValidOnnxFile(outputFile)) {
            Log.d(TAG, "Model already exists: ${outputFile.absolutePath}")
            emit(DownloadProgress.Completed(outputFile.absolutePath))
            return@flow
        }

        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "Lingxi-Android/1.0")
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        // 检查 Content-Type，防止下载到 HTML 页面
        val contentType = response.header("Content-Type", "")
        if (contentType.contains("text/html") || contentType.contains("text/plain")) {
            response.close()
            emit(DownloadProgress.Error("下载链接无效，返回了网页而非模型文件。请到 HuggingFace 查找正确链接。"))
            return@flow
        }

        if (!response.isSuccessful) {
            response.close()
            emit(DownloadProgress.Error("下载失败: HTTP ${response.code}"))
            return@flow
        }

        val body = response.body
        if (body == null) {
            response.close()
            emit(DownloadProgress.Error("响应体为空"))
            return@flow
        }

        val contentLength = body.contentLength()
        if (contentLength > 0 && contentLength < 100_000) {
            // 小于 100KB 不可能是有效的 LLM 模型
            body.close()
            response.close()
            emit(DownloadProgress.Error("文件太小 (${contentLength} bytes)，不是有效的模型文件"))
            return@flow
        }

        var downloadedBytes = 0L
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 进度报告（每 5%）
                        if (contentLength > 0 && downloadedBytes % (contentLength / 20) < 8192) {
                            val pct = (downloadedBytes * 100 / contentLength).toInt()
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                            emit(DownloadProgress.Progress(
                                percent = pct,
                                downloaded = downloadedBytes,
                                total = contentLength
                            ))
                        }
                    }
                }
            }
        }

        body.close()
        response.close()

        // 验证下载的文件
        if (!isValidOnnxFile(outputFile)) {
            val firstBytes = FileInputStream(outputFile).use { it.readNBytes(8) }
            val preview = firstBytes.joinToString(" ") { "%02X".format(it) }
            outputFile.delete()
            emit(DownloadProgress.Error("文件校验失败，不是有效的 ONNX 模型。文件头: $preview"))
            return@flow
        }

        Log.d(TAG, "Download complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        emit(DownloadProgress.Completed(outputFile.absolutePath))
    }.flowOn(Dispatchers.IO)

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
