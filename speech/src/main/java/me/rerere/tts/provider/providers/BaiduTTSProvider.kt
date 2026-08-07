package me.rerere.tts.provider.providers
import android.content.Context; import kotlinx.coroutines.flow.Flow; import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk; import me.rerere.tts.model.AudioFormat; import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider; import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient; import okhttp3.Request; import java.net.URLEncoder; import java.util.concurrent.TimeUnit

class BaiduTTSProvider : TTSProvider<TTSProviderSetting.BaiduTTS> {
    private val c = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    override fun generateSpeech(ctx: Context, s: TTSProviderSetting.BaiduTTS, req: TTSRequest): Flow<AudioChunk> = flow {
        val u = "https://fanyi.baidu.com/gettts?lan=zh&text=${URLEncoder.encode(req.text.take(500), "UTF-8")}&spd=${s.speed}&source=web"
        val r = c.newCall(Request.Builder().url(u).addHeader("User-Agent","Mozilla/5.0").get().build()).execute()
        if (r.isSuccessful && (r.body?.contentLength()?:0) > 100) emit(AudioChunk(r.body?.bytes()?:ByteArray(0), AudioFormat.MP3, null, true, mapOf("provider" to "baidu")))
        else throw Exception("百度 TTS 失败")
    }
}
