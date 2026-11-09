package com.devkumar.jarvis

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * वेक-वर्ड ("छोटू") और बातचीत सेवा।
 * 1) बैकग्राउंड में सिर्फ़ "हेलो छोटू" सुनती है — ऑफ़लाइन (Vosk), फ़ोन से बाहर कुछ नहीं जाता,
 *    और गाना/मूवी नहीं अटकते क्योंकि ऑडियो फ़ोकस नहीं लिया जाता।
 * 2) "हेलो छोटू" सुनते ही स्क्रीन जगाती है, अवतार दिखाती है, नमस्ते करती है।
 * 3) फिर बातचीत चालू रहती है: हर जवाब के बाद अपने आप फिर सुनती है,
 *    जब तक आप "छोटू क्लोज़" / "बंद हो जा" / "बाय" न बोलें। बातचीत के दौरान गाना रुकता है,
 *    बंद होने पर फिर चलता है।
 * सुरक्षा: फ़ोन लॉक होते हुए कोई आदेश नहीं चलता।
 */
class WakeService : Service() {

    /** ऐप की स्क्रीन (अवतार/लॉग) को बताने के लिए। */
    interface Ui {
        fun onState(s: OrbView.State)
        fun onLevel(l: Float)
        fun onCaption(t: String)
        fun onSession(active: Boolean)
    }

    companion object {
        @Volatile
        var running = false

        @Volatile
        var appForeground = false

        @Volatile
        private var instance: WakeService? = null

        const val ACTION_STOP = "com.devkumar.jarvis.STOP_WAKE"
        private const val CHANNEL = "chotu_bg"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WakeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeService::class.java))
        }

        /** ऐप सामने आया: तैरता अवतार छिपाओ (ऐप का अपना अवतार दिखेगा)। मुख्य थ्रेड से। */
        fun onAppForeground() {
            appForeground = true
            instance?.refreshAvatar()
        }

        fun onAppBackground() {
            appForeground = false
            instance?.refreshAvatar()
        }

        /** (आप, जवाब) के जोड़े ऐप की स्क्रीन पर दिखाने के लिए। */
        @Volatile
        var exchangeListener: ((String, String) -> Unit)? = null

        @Volatile
        var ui: Ui? = null

        val inSession: Boolean get() = instance?.session == true

        /** ऐप का माइक बटन: बातचीत शुरू करो। */
        fun listenNow() {
            instance?.requestCommand()
        }

        /** ऐप का बटन: बातचीत बंद करो। */
        fun endSessionNow() {
            instance?.endSession(true)
        }

        private val WAKE_VARIANTS = listOf("छोटू", "छोटु", "chotu", "chhotu", "chottu")

        private fun norm(s: String): String =
            s.lowercase().replace("़", "").replace('ज़', 'ज')

        /** वेक-वर्ड मिले तो उसके बाद का बचा हुआ टेक्स्ट लौटाता है (खाली भी हो सकता है), वरना null। */
        fun matchWake(text: String, word: String): String? {
            val t = text.lowercase()
            val w = word.trim().lowercase()
            val variants = LinkedHashSet<String>()
            if (w.isNotEmpty()) variants.add(w)
            if (w in WAKE_VARIANTS) variants.addAll(WAKE_VARIANTS)
            var best = -1
            var bestLen = 0
            for (v in variants) {
                val i = t.indexOf(v)
                if (i >= 0 && (best == -1 || i < best)) {
                    best = i
                    bestLen = v.length
                }
            }
            if (best < 0) return null
            return t.substring(best + bestLen).trim { it.isWhitespace() || it in ",.!?।" }
        }

        private val FILLERS = setOf(
            "ok", "okay", "ओके", "ओक", "भाई", "bhai", "अब", "ab", "चलो", "chalo",
            "please", "प्लीज", "now", "जी", "ji", "हेलो", "हैलो", "hello", "अच्छा", "accha", "achha", "हाँ", "हां", "haan"
        ) + WAKE_VARIANTS

        private val CLOSE_PHRASES = setOf(
            "close", "क्लोज", "क्लोस", "bye", "बाय", "bye bye", "बाय बाय", "goodbye", "good bye", "गुडबाय",
            "stop", "स्टॉप", "exit", "एग्जिट", "बंद", "band", "बंद करो", "band karo", "बंद कर दो", "band kar do",
            "बंद हो जा", "बंद हो जाओ", "band ho ja", "band ho jao", "बंद हो जाइए",
            "बस", "bas", "बस करो", "bas karo", "सो जा", "सो जाओ", "so ja", "so jao", "that's all", "that is all", "दैट्स ऑल"
        )

        /**
         * बातचीत बंद करने का आदेश? पूरा वाक्य (भराव शब्द हटाकर) सिर्फ़ बंद करने वाला होना चाहिए,
         * ताकि "टॉर्च बंद कर दो" जैसे आदेश से बातचीत बंद न हो।
         */
        fun isClose(text: String, word: String): Boolean {
            val fillers = FILLERS + norm(word.trim())
            val words = norm(text).replace(Regex("[,.!?।]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && it !in fillers }
            if (words.isEmpty()) return false
            return words.joinToString(" ") in CLOSE_PHRASES
        }
    }

    private enum class Mode { WAKE, COMMAND, BUSY, PAUSED }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var voice: VoiceIO
    private var mode = Mode.PAUSED
    private var errStreak = 0
    private var session = false
    private var lastHeard = 0L
    private var cpuLock: PowerManager.WakeLock? = null
    private lateinit var vosk: VoskWake
    private var focusReq: AudioFocusRequest? = null
    private val startVosk = Runnable { if (mode == Mode.WAKE && running) vosk.start() }
    private val relisten = Runnable { if (mode == Mode.COMMAND && session) listenInSession() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            // MIN: स्टेटस बार में आइकन नहीं, बस नीचे खिसकाने पर दिखे
            NotificationChannel(CHANNEL, "Chotu (बैकग्राउंड)", NotificationManager.IMPORTANCE_MIN)
        )
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(idleText()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            stopSelf()
            return
        }
        running = true
        instance = this

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply {
            setReferenceCounted(false)
            acquire()
        }

        voice = VoiceIO(
            this,
            onPartial = { if (session) caption("“$it”") },
            onFinal = { onHeard(it) },
            onState = { if (session) pushState(it) },
            onLevel = { if (session) pushLevel(it) },
            onProblem = { if (session) caption("⚠ $it") },
            onErrorCode = { onVoiceError(it) }
        )
        vosk = VoskWake(
            this,
            wakePhrase = { SecurePrefs.wakeWord(this) },
            onWake = { onWakeDetected() },
            onHeard = { if (appForeground && !session) ui?.onCaption("👂 $it") },
            onFail = { onWakeFail(it) }
        )
        updateNotification("ऑफ़लाइन वेक-वर्ड तैयार हो रहा है…")
        vosk.load {
            updateNotification(idleText())
            beginWake()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SecurePrefs.setWakeEnabled(this, false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        session = false
        main.removeCallbacksAndMessages(null)
        if (::vosk.isInitialized) vosk.stop()
        releaseAudioFocus()
        AvatarOverlay.hide()
        ui?.onSession(false)
        if (::voice.isInitialized) voice.shutdown()
        worker.shutdownNow()
        cpuLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun idleText() = "‘${SecurePrefs.wakeWord(this)}’ बोलकर जगाइए"

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, WakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Chotu सुन रहा है")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "बंद करें", stop)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    // ---------- अवतार/स्क्रीन को अपडेट ----------

    private fun pushState(s: OrbView.State) {
        AvatarOverlay.setState(s)
        ui?.onState(s)
    }

    private fun pushLevel(l: Float) {
        AvatarOverlay.setLevel(l)
        ui?.onLevel(l)
    }

    private fun caption(t: String) {
        AvatarOverlay.setCaption(t)
        ui?.onCaption(t)
    }

    /** बातचीत चालू हो और ऐप सामने न हो, तभी तैरता अवतार। */
    fun refreshAvatar() {
        if (session && !appForeground) AvatarOverlay.show() else AvatarOverlay.hide()
    }

    // ---------- वेक-वर्ड का चक्र ----------

    private fun beginWake() {
        main.removeCallbacks(startVosk)
        main.removeCallbacks(relisten)
        mode = Mode.WAKE
        voice.cancel() // Google की पहचान माइक छोड़े
        if (!vosk.ready) return // मॉडल लोड होने पर load() फिर beginWake() बुलाएगा
        // Google की पहचान को माइक छोड़ने का थोड़ा समय दो
        main.postDelayed(startVosk, 400)
    }

    private fun onWakeDetected() {
        if (mode != Mode.WAKE) return
        vosk.stop()
        errStreak = 0
        wakeScreen()
        startSession()
        // लॉक में कोई आदेश नहीं चलेगा, पहले आप अनलॉक कीजिए
        if (isLocked()) askUnlock() else greet()
    }

    private fun onWakeFail(msg: String) {
        updateNotification("⚠ $msg")
        if (appForeground) ui?.onCaption("⚠ $msg")
        // माइक कुछ देर किसी और के पास हो (कैमरा/कॉल) तो 10 सेकंड बाद फिर कोशिश
        if (vosk.ready && mode == Mode.WAKE) {
            main.removeCallbacks(startVosk)
            main.postDelayed(startVosk, 10_000)
        }
    }

    private fun name() = SecurePrefs.userName(this)

    private fun onHeard(text: String) {
        val word = SecurePrefs.wakeWord(this)
        when (mode) {
            Mode.COMMAND -> {
                errStreak = 0
                lastHeard = SystemClock.elapsedRealtime()
                // बातचीत के बीच "छोटू"/"हेलो छोटू" अकेला बोला?
                val rest = matchWake(text, "छोटू")
                when {
                    isClose(text, word) -> endSession(true)
                    isLocked() -> askUnlock()
                    rest != null && rest.length < 3 -> {
                        mode = Mode.BUSY
                        caption("जी ${name()}, बोलिए।")
                        voice.speak("जी ${name()}, बोलिए।") { listenInSession() }
                    }
                    else -> runCommand(text)
                }
            }
            else -> Unit
        }
    }

    private fun onVoiceError(code: Int) {
        if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf()
            return
        }
        val quiet = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        if (quiet) errStreak = 0 else errStreak++
        val delay = if (quiet) 150L else minOf(500L shl minOf(errStreak, 4), 8000L)
        when (mode) {
            Mode.COMMAND -> {
                // चुप रहने पर बातचीत बंद नहीं होती, बस फिर से सुनते हैं
                val limitMin = SecurePrefs.sessionTimeoutMin(this)
                val idleMs = SystemClock.elapsedRealtime() - lastHeard
                if (limitMin > 0 && idleMs > limitMin * 60_000L) {
                    mode = Mode.BUSY
                    caption("😴")
                    voice.speak("मैं सो रहा हूँ, ज़रूरत हो तो छोटू बोलिए।") { endSession(false) }
                    return
                }
                if (errStreak >= 8) {
                    mode = Mode.BUSY
                    caption("⚠ आवाज़ पहचानने में दिक्कत")
                    voice.speak("आवाज़ पहचानने में दिक्कत आ रही है।") { endSession(false) }
                    return
                }
                main.removeCallbacks(relisten)
                main.postDelayed(relisten, delay)
            }
            else -> Unit
        }
    }

    // ---------- बातचीत ----------

    private fun startSession() {
        lastHeard = SystemClock.elapsedRealtime()
        if (session) return
        session = true
        errStreak = 0
        grabAudioFocus()
        refreshAvatar()
        ui?.onSession(true)
        updateNotification("बातचीत चालू — ‘छोटू क्लोज़’ बोलकर बंद करें")
    }

    private fun greet() {
        mode = Mode.BUSY
        val g = "हाय ${name()}, मैं आपकी क्या मदद कर सकता हूँ?"
        caption(g)
        voice.speak(g) { listenInSession() }
    }

    private fun listenInSession() {
        if (!session || mode == Mode.PAUSED) return
        main.removeCallbacks(relisten)
        mode = Mode.COMMAND
        voice.patient = true
        voice.listen()
    }

    private fun continueSession() {
        if (session) listenInSession() else beginWake()
    }

    fun endSession(sayBye: Boolean) {
        if (!session) return
        session = false
        main.removeCallbacks(relisten)
        voice.cancel()
        mode = Mode.BUSY
        updateNotification(idleText())
        val finish = {
            releaseAudioFocus()
            pushState(OrbView.State.IDLE)
            AvatarOverlay.hide()
            ui?.onSession(false)
            beginWake()
        }
        if (sayBye) {
            val bye = "ठीक है ${name()}, ज़रूरत हो तो छोटू बोलिए।"
            caption(bye)
            // अवतार अलविदा बोलते हुए दिखे, फिर छिपे
            AvatarOverlay.setState(OrbView.State.SPEAKING)
            ui?.onState(OrbView.State.SPEAKING)
            voice.speak(bye) { finish() }
        } else {
            finish()
        }
    }

    // ---------- गाना रोकना/चलाना (सिर्फ़ बातचीत के दौरान) ----------

    private fun grabAudioFocus() {
        if (focusReq != null) return
        try {
            val am = getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            am.requestAudioFocus(req)
            focusReq = req
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioFocus() {
        val req = focusReq ?: return
        focusReq = null
        try {
            getSystemService(AudioManager::class.java).abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
    }

    // ---------- स्क्रीन और लॉक ----------

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "jarvis:screen"
        )
        wl.acquire(8_000)
    }

    private fun isLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private fun askUnlock() {
        mode = Mode.BUSY
        caption("🔒 फ़ोन अनलॉक कीजिए")
        voice.speak("हाय ${name()}, फ़ोन अनलॉक कीजिए।") { waitUnlock(0) }
    }

    private fun waitUnlock(n: Int) {
        if (mode != Mode.BUSY || !session) return
        if (!isLocked()) {
            // अनलॉक के बाद अवतार फिर से दिखाओ (लॉक स्क्रीन से हट गया हो तो)
            refreshAvatar()
            greet()
            return
        }
        if (n >= 40) {
            endSession(false)
            return
        }
        main.postDelayed({ waitUnlock(n + 1) }, 750)
    }

    // ---------- आदेश ----------

    private fun runCommand(text: String) {
        mode = Mode.BUSY
        pushState(OrbView.State.THINKING)
        caption("आप: $text")
        val agent = JarvisAgent.shared(applicationContext)
        worker.execute {
            val reply = try {
                agent.run(text) { s -> main.post { if (session) caption(s) } }
            } catch (e: Exception) {
                "⚠ " + (e.message ?: e.javaClass.simpleName)
            }
            main.post {
                exchangeListener?.invoke(text, reply)
                if (!running) return@post
                caption(reply)
                if (reply.startsWith("⚠")) {
                    voice.speak("कुछ गड़बड़ हुई। ऐप खोलकर देखिए।") { continueSession() }
                } else {
                    voice.speak(reply) { continueSession() }
                }
            }
        }
    }

    // ---------- ऐप का माइक बटन ----------

    private fun requestCommand() {
        if (!::voice.isInitialized || !::vosk.isInitialized || mode == Mode.BUSY) return
        main.removeCallbacks(startVosk)
        vosk.stop()
        voice.cancel()
        startSession()
        listenInSession()
    }
}
