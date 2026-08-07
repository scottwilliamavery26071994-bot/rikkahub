/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "OpenAITTSProvider"

class OpenAITTSProvider : TTSProvider<TTSProviderSetting.OpenAI> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "mp3") // Default to MP3
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/audio/speech")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            throw Exception("TTS request failed: ${response.code} ${response.message}: $errorBody")
        }

        val audioData = response.body.bytes()

        if (audioData.isEmpty()) {
            throw Exception("TTS response body is empty (0 bytes)")
        }

        // 根据 Content-Type 判断实际音频格式，而非盲目假设 MP3
        val contentType = response.header("Content-Type") ?: ""
        val format = when {
            contentType.contains("wav", ignoreCase = true) -> AudioFormat.WAV
            contentType.contains("pcm", ignoreCase = true) -> AudioFormat.PCM
            contentType.contains("mpeg", ignoreCase = true) ||
                contentType.contains("mp3", ignoreCase = true) -> AudioFormat.MP3
            // 鱼卷音频等可能返回 application/octet-stream，默认按 MP3 处理
            else -> AudioFormat.MP3
        }

        Log.i(TAG, "generateSpeech: received ${audioData.size} bytes, format=$format, contentType=$contentType")

        emit(
            AudioChunk(
                data = audioData,
                format = format,
                isLast = true,
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice
                )
            )
        )
    }
}
