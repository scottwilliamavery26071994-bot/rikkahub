/*
 * 灵犀 Lingxi - 本地模型推理引擎
 * 基于 ONNX Runtime 在设备端直接运行 LLM
 */

package me.rerere.rikkahub.data.localmodel

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp

private const val TAG = "LocalModelEngine"

/**
 * 本地模型推理引擎
 * 加载 ONNX 格式的量化模型文件，在设备端运行推理
 */
class LocalModelEngine(
    private val modelPath: String,
    private val tokenizer: LocalTokenizer = LocalTokenizer()
) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var kvCache: Map<String, OnnxTensor>? = null

    val isLoaded: Boolean get() = session != null

    /**
     * 加载模型文件
     */
    fun load(): Result<Unit> = runCatching {
        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            // 使用 CPU 执行，开启优化
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // 设置线程数
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
            // 启用内存模式优化
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL)
        }
        session = env?.createSession(modelPath, opts)
        Log.d(TAG, "Model loaded: $modelPath")
    }

    /**
     * 流式生成文本
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): Flow<String> = flow {
        require(isLoaded) { "Model not loaded. Call load() first." }

        val ids = tokenizer.encode(prompt).toMutableList()
        var generatedTokens = 0

        while (generatedTokens < maxTokens) {
            val logits = runInference(ids)
            if (logits == null) {
                Log.e(TAG, "Inference returned null logits")
                break
            }

            val nextToken = sample(logits, temperature, topP, topK)
            if (nextToken == tokenizer.eosTokenId) break

            ids.add(nextToken)
            generatedTokens++

            val text = tokenizer.decode(listOf(nextToken))
            if (text.isNotEmpty()) {
                emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 运行一次推理
     */
    private fun runInference(inputIds: List<Int>): FloatArray? {
        val ortEnv = env ?: return null
        val ortSession = session ?: return null

        return try {
            val inputShape = longArrayOf(1, inputIds.size.toLong())
            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(inputIds.map { it.toLong() }.toLongArray()),
                inputShape
            )

            val inputs = mutableMapOf<String, OnnxTensor>("input_ids" to inputTensor)
            val outputs = mutableListOf("logits")

            if (kvCache != null) {
                inputs.putAll(kvCache!!)
                outputs.addAll(kvCache!!.keys.filter { it.startsWith("present") })
            } else {
                outputs.addAll(ortSession.inputInfo.keys.filter { it.startsWith("present") })
            }

            val result = ortSession.run(inputs, outputs)
            val logitsTensor = result.get("logits")?.get() as? OnnxTensor ?: return null

            // Extract last token logits
            val logitsData = logitsTensor.floatBuffer
            val vocabSize = tokenizer.vocabSize
            val offset = (inputIds.size - 1) * vocabSize
            val logits = FloatArray(vocabSize) {
                if (offset + it < logitsData.limit()) logitsData[offset + it] else Float.NEGATIVE_INFINITY
            }

            // Update KV cache
            val newCache = mutableMapOf<String, OnnxTensor>()
            result.forEach { (key, value) ->
                if (key.startsWith("present") && value.isTensor) {
                    newCache[key] = value as OnnxTensor
                }
            }
            kvCache = newCache

            // Close unused tensors
            inputTensor.close()
            result.values.filter { it !is OnnxTensor || !it.info.name.startsWith("present") }
                .forEach { if (it is OnnxTensor) it.close() }

            logits
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            null
        }
    }

    /**
     * 采样下一个 token
     */
    private fun sample(
        logits: FloatArray,
        temperature: Float,
        topP: Float,
        topK: Int
    ): Int {
        val vocabSize = logits.size
        val indices = (0 until vocabSize).toList()

        // Apply temperature
        val scaledLogits = if (temperature > 0) {
            FloatArray(vocabSize) { logits[it] / temperature }
        } else {
            logits
        }

        // Softmax
        val maxLogit = scaledLogits.max()
        val expSum = scaledLogits.sumOf { exp((it - maxLogit).toDouble()) }
        if (expSum.isInfinite() || expSum <= 0.0) {
            return indices.maxByOrNull { scaledLogits[it] } ?: 0
        }
        val probs = FloatArray(vocabSize) {
            (exp((scaledLogits[it] - maxLogit).toDouble()) / expSum).toFloat()
        }

        // Top-K filtering
        val topKIndices = if (topK > 0 && topK < vocabSize) {
            indices.sortedByDescending { probs[it] }.take(topK)
        } else {
            indices
        }

        // Top-P (nucleus) filtering
        val sorted = topKIndices.sortedByDescending { probs[it] }
        var cumsum = 0f
        val nucleus = mutableListOf<Int>()
        for (idx in sorted) {
            cumsum += probs[idx]
            nucleus.add(idx)
            if (cumsum >= topP) break
        }

        // Sample
        val nucleusProbs = nucleus.map { probs[it] }
        val nucleusSum = nucleusProbs.sum()
        if (nucleusSum <= 0f) return nucleus.firstOrNull() ?: 0

        val rand = Math.random().toFloat() * nucleusSum
        var accum = 0f
        for ((i, idx) in nucleus.withIndex()) {
            accum += nucleusProbs[i]
            if (rand <= accum) return idx
        }
        return nucleus.lastOrNull() ?: 0
    }

    fun close() {
        kvCache?.values?.forEach { runCatching { it.close() } }
        kvCache = null
        session?.close()
        session = null
        env?.close()
        env = null
    }
}
