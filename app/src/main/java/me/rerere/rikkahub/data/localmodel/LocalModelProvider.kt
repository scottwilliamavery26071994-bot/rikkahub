/*
 * 灵犀 Lingxi - 本地模型 Provider
 * 基于 ONNX Runtime 在设备端运行 LLM
 */

package me.rerere.rikkahub.data.localmodel

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.*
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.isEmptyInputMessage

private const val TAG = "LocalModelProvider"

class LocalModelProvider : Provider<ProviderSetting.LocalModel> {

    private var engine: LocalModelEngine? = null

    override suspend fun listModels(providerSetting: ProviderSetting.LocalModel): List<Model> {
        // 本地模型列表由用户手动管理
        return providerSetting.models
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalModel,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        val prompt = buildPrompt(messages)
        val modelPath = providerSetting.modelFilePath.ifBlank {
            throw IllegalStateException("请先配置本地模型文件路径")
        }

        ensureEngineLoaded(modelPath)

        val result = StringBuilder()
        engine!!.generate(prompt, maxTokens = params.maxTokens ?: 256).collect { token ->
            result.append(token)
        }

        return MessageChunk(
            id = "local-${System.currentTimeMillis()}",
            model = params.model.modelId,
            choices = listOf(
                me.rerere.ai.ui.UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage.assistant(result.toString()),
                    finishReason = "stop"
                )
            )
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalModel,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        val prompt = buildPrompt(messages)
        val modelPath = providerSetting.modelFilePath.ifBlank {
            throw IllegalStateException("请先配置本地模型文件路径")
        }

        ensureEngineLoaded(modelPath)

        return engine!!.generate(prompt, maxTokens = params.maxTokens ?: 256).map { token ->
            MessageChunk(
                id = "local-${System.currentTimeMillis()}",
                model = params.model.modelId,
                choices = listOf(
                    me.rerere.ai.ui.UIMessageChoice(
                        index = 0,
                        delta = UIMessage.assistant(token),
                        message = null,
                        finishReason = null
                    )
                )
            )
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult {
        error("本地模型不支持图片生成")
    }

    private fun ensureEngineLoaded(modelPath: String) {
        if (engine?.isLoaded != true || engine == null) {
            engine?.close()
            engine = LocalModelEngine(modelPath).also {
                it.load().getOrElse { e ->
                    engine = null
                    throw IllegalStateException("模型加载失败: ${e.message}", e)
                }
            }
        }
    }

    private fun buildPrompt(messages: List<UIMessage>): String {
        return messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                me.rerere.ai.core.MessageRole.SYSTEM -> "System"
                me.rerere.ai.core.MessageRole.USER -> "User"
                me.rerere.ai.core.MessageRole.ASSISTANT -> "Assistant"
                else -> msg.role.name
            }
            val content = msg.parts.joinToString("") { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text
                    is UIMessagePart.Reasoning -> ""
                    else -> ""
                }
            }
            "$role: $content"
        } + "\nAssistant: "
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
