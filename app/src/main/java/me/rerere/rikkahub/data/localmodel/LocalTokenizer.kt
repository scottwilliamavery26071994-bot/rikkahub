/*
 * 灵犀 Lingxi - 本地模型分词器
 * 简单的 BPE tokenizer，支持加载 tokenizer.json
 */

package me.rerere.rikkahub.data.localmodel

import android.util.Log
import org.json.JSONObject
import java.io.File

private const val TAG = "LocalTokenizer"

class LocalTokenizer(
    private var _vocabSize: Int = 32000,
    val eosTokenId: Int = 2,
    val bosTokenId: Int = 1,
    private val unkTokenId: Int = 0,
) {
    fun getVocabSize(): Int = _vocabSize
    private var vocab: MutableMap<String, Int> = mutableMapOf()
    private var reverseVocab: MutableMap<Int, String> = mutableMapOf()
    private var merges: List<Pair<String, String>> = emptyList()

    fun loadTokenizerFile(path: String): Result<Unit> = runCatching {
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Tokenizer file not found, using basic fallback: $path")
            initFallbackVocab()
            return@runCatching
        }
        val content = file.readText()
        val json = JSONObject(content)
        val model = json.optJSONObject("model") ?: json
        val vocabObj = model.optJSONObject("vocab") ?: JSONObject()
        vocab.clear()
        reverseVocab.clear()
        vocabObj.keys().forEach { key ->
            val id = vocabObj.getInt(key)
            vocab[key] = id
            reverseVocab[id] = key
        }
        val mergesArr = model.optJSONArray("merges")
        if (mergesArr != null) {
            val mergeList = mutableListOf<Pair<String, String>>()
            for (i in 0 until mergesArr.length()) {
                val merge = mergesArr.getString(i)
                val parts = merge.split(" ")
                if (parts.size == 2) mergeList.add(parts[0] to parts[1])
            }
            merges = mergeList
        }
        Log.d(TAG, "Loaded vocab: ${vocab.size} tokens, ${merges.size} merges")
    }

    fun encode(text: String): List<Int> {
        if (vocab.isEmpty()) initFallbackVocab()
        val words = preTokenize(text)
        val result = mutableListOf<Int>()
        result.add(bosTokenId)
        for (word in words) {
            if (word.isBlank()) continue
            vocab[word]?.let { result.add(it); continue }
            result.addAll(bpeEncode(word))
        }
        return result
    }

    fun decode(ids: List<Int>): String {
        if (reverseVocab.isEmpty()) initFallbackVocab()
        return ids.joinToString("") { id ->
            reverseVocab[id]?.replace("▁", " ")?.replace("<0x0A>", "\n") ?: ""
        }
    }

    private fun bpeEncode(word: String): List<Int> {
        var symbols = word.map { it.toString() }.toMutableList()
        while (true) {
            var bestScore = Int.MAX_VALUE
            var bestPair = -1
            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] to symbols[i + 1]
                val mergeIdx = merges.indexOf(pair)
                if (mergeIdx in 0..<bestScore) { bestScore = mergeIdx; bestPair = i }
            }
            if (bestPair == -1) break
            symbols[bestPair] = symbols[bestPair] + symbols[bestPair + 1]
            symbols.removeAt(bestPair + 1)
        }
        return symbols.mapNotNull { vocab[it] ?: unkTokenId }.ifEmpty { listOf(unkTokenId) }
    }

    private fun preTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            when {
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) { result.add("▁$current"); current.clear() }
                    else result.add("▁")
                }
                ch.isLetterOrDigit() || ch == '\'' -> current.append(ch)
                else -> {
                    if (current.isNotEmpty()) { result.add("▁$current"); current.clear() }
                    result.add("▁$ch")
                }
            }
        }
        if (current.isNotEmpty()) result.add("▁$current")
        return result.ifEmpty { listOf("▁") }
    }

    private fun initFallbackVocab() {
        val chars = "▁ abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" +
                ".,!?;:'\"-()[]{}/\\@#$%^&*+=<>|~`\n\t\r" +
                "的一是不了在有人我他这中大来上个国为和出" +
                "就以到说时要地会可下得你年对能自那之过" +
                "学都去把而好小多天里子话行成如然开本所" +
                "法见经同方还起部家前高著力长进体水工"
        vocab.clear(); reverseVocab.clear()
        chars.toList().distinct().forEachIndexed { i, ch ->
            vocab[ch.toString()] = i + 4
            reverseVocab[i + 4] = ch.toString()
        }
        vocab["<unk>"] = 0; vocab["<s>"] = 1; vocab["</s>"] = 2; vocab["<pad>"] = 3
        reverseVocab[0] = "<unk>"; reverseVocab[1] = "<s>"; reverseVocab[2] = "</s>"; reverseVocab[3] = "<pad>"
        merges = emptyList()
        Log.d(TAG, "Fallback vocab: ${vocab.size} tokens")
    }
}
