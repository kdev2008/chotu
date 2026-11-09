package com.devkumar.jarvis

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * ऑफ़लाइन वेक-वर्ड ("हेलो छोटू") — Vosk, पूरी तरह फ़ोन पर।
 * - कोई आवाज़ इंटरनेट पर नहीं जाती।
 * - ऑडियो फ़ोकस नहीं लेता, इसलिए गाना/मूवी रुकते या अटकते नहीं।
 * - बीप नहीं, बार-बार रीस्टार्ट नहीं।
 * मॉडल (vosk-model-small-hi) GitHub बिल्ड के समय assets/model-hi में डाला जाता है।
 */
class VoskWake(
    private val ctx: Context,
    private val wakePhrase: () -> String,
    private val onWake: () -> Unit,
    private val onHeard: (String) -> Unit,
    private val onFail: (String) -> Unit
) {
    companion object {
        @Volatile
        private var model: Model? = null

        private const val RATE = 16000

        private val HELLO = setOf(
            "हेलो", "हैलो", "हलो", "हल्लो", "हेल्लो", "हैल्लो", "हेलौ", "हालो", "hello", "helo", "hallo"
        )
        private val CHOTU_DEFAULTS = setOf("हेलो छोटू", "हैलो छोटू", "hello chotu", "छोटू", "chotu", "chhotu")

        private fun norm(s: String): String =
            s.lowercase().replace("़", "").replace('ज़', 'ज').trim()

        /** "हेलो" के ठीक बाद "छोट…" वाला शब्द, या आपका अपना वेक-वाक्य। */
        fun isWake(text: String, phrase: String): Boolean {
            val t = norm(text)
            if (t.isEmpty()) return false
            val p = norm(phrase)
            if (p.isNotEmpty() && p !in CHOTU_DEFAULTS) return t.contains(p)
            val w = t.split(Regex("\\s+"))
            for (i in 0 until w.size - 1) {
                if (w[i] in HELLO) {
                    val n = w[i + 1]
                    if (n.startsWith("छोट") || n.startsWith("चोट") || n.startsWith("chot") || n.startsWith("chhot")) return true
                }
            }
            return false
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var thread: Thread? = null

    @Volatile
    private var active = false

    val ready: Boolean get() = model != null

    /** मॉडल को assets से फ़ोन की स्टोरेज में खोलो (पहली बार कुछ सेकंड लगते हैं)। */
    fun load(onReady: () -> Unit) {
        if (model != null) {
            onReady()
            return
        }
        try {
            StorageService.unpack(
                ctx.applicationContext, "model-hi", "model",
                { m ->
                    model = m
                    onReady()
                },
                { e -> onFail("ऑफ़लाइन वेक-वर्ड मॉडल लोड नहीं हुआ: ${e.message}") }
            )
        } catch (e: Throwable) {
            onFail("ऑफ़लाइन वेक-वर्ड मॉडल नहीं मिला: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val m = model ?: return
        if (active) return
        active = true
        val t = Thread({
            var rec: AudioRecord? = null
            var recog: Recognizer? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val bufBytes = maxOf(minBuf, RATE) // ~0.5 सेकंड
                rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes * 2
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("माइक उपलब्ध नहीं")
                recog = Recognizer(m, RATE.toFloat())
                rec.startRecording()
                val buf = ShortArray(RATE / 10) // 100ms
                var lastShown = ""
                while (active) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) {
                        Thread.sleep(50)
                        continue
                    }
                    val done = recog.acceptWaveForm(buf, n)
                    val text = try {
                        if (done) JSONObject(recog.result).optString("text")
                        else JSONObject(recog.partialResult).optString("partial")
                    } catch (_: Exception) {
                        ""
                    }
                    if (text.isNotBlank() && text != lastShown) {
                        lastShown = text
                        main.post { onHeard(text) }
                    }
                    if (text.isNotBlank() && isWake(text, wakePhrase())) {
                        active = false
                        main.post { onWake() }
                        break
                    }
                    if (done) lastShown = ""
                }
            } catch (e: Throwable) {
                if (active) {
                    active = false
                    main.post { onFail("वेक-वर्ड सुनने में दिक्कत: ${e.message ?: e.javaClass.simpleName}") }
                }
            } finally {
                try {
                    rec?.stop()
                } catch (_: Exception) {
                }
                rec?.release()
                try {
                    recog?.close()
                } catch (_: Exception) {
                }
            }
        }, "chotu-wake")
        thread = t
        t.start()
    }

    /** माइक छोड़ दो (बातचीत के लिए Google की पहचान माइक लेगी)। */
    fun stop() {
        active = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(1500)
            } catch (_: InterruptedException) {
            }
        }
    }
}
