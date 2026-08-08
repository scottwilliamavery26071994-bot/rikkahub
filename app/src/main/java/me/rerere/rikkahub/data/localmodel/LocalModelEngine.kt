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
import java.nio.LongBuffer
import kotlin.math.exp

private const val TAG = "LocalModelEngine"

class LocalModelEngine(
    private val modelPath: String,
    private val tokenizer: LocalTokenizer = LocalTokenizer()
) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null

    val isLoaded: Boolean get() = session != null

    fun load(): Result<Unit> = runCatching {
        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
        }
        session = env!!.createSession(modelPath, opts)
        Log.d(TAG, "Model loaded: $modelPath")
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): Flow<String> = flow {
        require(isLoaded) { "Model not loaded" }
        val ids = tokenizer.encode(prompt).toMutableList()
        var count = 0
        while (count < maxTokens) {
            val logits = runInference(ids) ?: break
            val next = sample(logits, temperature, topP, topK)
            if (next == tokenizer.eosTokenId) break
            ids.add(next)
            count++
            val text = tokenizer.decode(listOf(next))
            if (text.isNotEmpty()) emit(text)
        }
    }.flowOn(Dispatchers.IO)

    private fun runInference(inputIds: List<Int>): FloatArray? {
        val ortEnv = env ?: return null
        val ortSession = session ?: return null
        return try {
            val shape = longArrayOf(1, inputIds.size.toLong())
            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(inputIds.map { it.toLong() }.toLongArray()),
                shape
            )
            val inputs = mapOf("input_ids" to inputTensor)
            val result = ortSession.run(inputs)
            inputTensor.close()

            // result.get(0) returns Optional, .get() unwraps Optional
            val logitsTensor = result.get(0).get() as? OnnxTensor
            if (logitsTensor == null) {
                result.close()
                return null
            }

            val data = logitsTensor.floatBuffer
            val vocabSize = tokenizer.getVocabSize()
            val offset = (inputIds.size - 1) * vocabSize
            val logits = FloatArray(vocabSize) { i ->
                val pos = offset + i
                if (pos < data.limit()) data[pos] else Float.NEGATIVE_INFINITY
            }
            logitsTensor.close()
            result.close()
            logits
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            null
        }
    }

    private fun sample(
        logits: FloatArray,
        temperature: Float,
        topP: Float,
        topK: Int
    ): Int {
        val n = logits.size
        val indices = (0 until n).toList()

        val scaled = if (temperature > 0f) FloatArray(n) { logits[it] / temperature } else logits
        val maxLogit = scaled.max()
        val expSum = scaled.sumOf { exp((it - maxLogit).toDouble()) }
        if (expSum.isInfinite() || expSum <= 0.0) return indices.maxByOrNull { scaled[it] } ?: 0

        val probs = FloatArray(n) { (exp((scaled[it] - maxLogit).toDouble()) / expSum).toFloat() }

        val filtered = if (topK > 0 && topK < n) {
            indices.sortedByDescending { probs[it] }.take(topK)
        } else indices

        val sorted = filtered.sortedByDescending { probs[it] }
        var cumsum = 0f
        val nucleus = mutableListOf<Int>()
        for (idx in sorted) {
            cumsum += probs[idx]
            nucleus.add(idx)
            if (cumsum >= topP) break
        }

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
        session?.close()
        session = null
        env?.close()
        env = null
    }
}
