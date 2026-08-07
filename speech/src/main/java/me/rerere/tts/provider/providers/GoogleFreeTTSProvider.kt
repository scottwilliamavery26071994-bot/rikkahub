package me.rerere.tts.provider.providers
import android.content.Context; import kotlinx.coroutines.flow.Flow; import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk; import me.rerere.tts.model.AudioFormat; import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider; import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient; import okhttp3.Request; import java.net.URLEncoder; import java.util.concurrent.TimeUnit

class GoogleFreeTTSProvider : TTSProvider<TTSProviderSetting.GoogleFreeTTS> {
    private val c = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    override fun generateSpeech(ctx: Context, s: TTSProviderSetting.GoogleFreeTTS, req: TTSRequest): Flow<AudioChunk> = flow {
        val u = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=${s.lang}&q=${URLEncoder.encode(req.text.take(200), "UTF-8")}"
        val r = c.newCall(Request.Builder().url(u).addHeader("User-Agent","Mozilla/5.0").get().build()).execute()
        if (r.isSuccessful) emit(AudioChunk(r.body?.bytes()?:ByteArray(0), AudioFormat.MP3, null, true, mapOf("provider" to "google")))
        else throw Exception("Google TTS: ${r.code}")
    }
}
