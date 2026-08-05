package me.rerere.tts.provider.providers
import android.content.Context; import kotlinx.coroutines.flow.Flow; import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk; import me.rerere.tts.model.AudioFormat; import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider; import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient; import okhttp3.Request; import java.net.URLEncoder; import java.util.concurrent.TimeUnit

class EdgeTTSProvider : TTSProvider<TTSProviderSetting.EdgeTTS> {
    private val c = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    override fun generateSpeech(ctx: Context, s: TTSProviderSetting.EdgeTTS, req: TTSRequest): Flow<AudioChunk> = flow {
        val u = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Voice=${s.voice}&Text=${URLEncoder.encode(req.text, "UTF-8")}"
        val r = c.newCall(Request.Builder().url(u).addHeader("User-Agent","Mozilla/5.0").get().build()).execute()
        if (r.isSuccessful) emit(AudioChunk(r.body?.bytes()?:ByteArray(0), AudioFormat.MP3, null, true, mapOf("provider" to "edge")))
        else throw Exception("Edge TTS: ${r.code}")
    }
}
