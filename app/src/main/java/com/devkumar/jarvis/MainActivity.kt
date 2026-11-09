package com.devkumar.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var orb: OrbView
    private lateinit var statusTv: TextView
    private lateinit var logTv: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var langBtn: Button
    private lateinit var micBtn: Button
    private lateinit var voice: VoiceIO

    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var unlocked = false
    private var lastStop = 0L

    /** वेक सेवा बंद हो तब भी ऐप में बातचीत: "छोटू क्लोज़" तक बार-बार सुनो। */
    private var localSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // रिसेंट्स/स्क्रीनशॉट में बातचीत न दिखे
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        buildUi()
        voice = VoiceIO(
            this,
            onPartial = { statusTv.text = it },
            onFinal = { onLocalVoice(it) },
            onState = { orb.state = it },
            onLevel = { orb.level = it },
            onProblem = {
                statusTv.text = it
                localSession = false
                updateMic()
            }
        )
        askPermissions()
        lock()
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.fitsSystemWindows = true
        content.setPadding(dp(12), dp(8), dp(12), dp(8))

        // ऊपर की पट्टी
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "C H O T U"
        title.setTextColor(Color.parseColor("#22D3EE"))
        title.textSize = 20f
        title.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        title.setShadowLayer(18f, 0f, 0f, Color.parseColor("#22D3EE"))
        titles.addView(title)
        val sub = TextView(this)
        sub.text = "PERSONAL AI  ·  ONLINE"
        sub.setTextColor(Color.parseColor("#4B8FA0"))
        sub.textSize = 10f
        sub.typeface = Typeface.MONOSPACE
        sub.letterSpacing = 0.2f
        titles.addView(sub)
        top.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gear = Button(this)
        gear.text = "⚙"
        gear.setOnClickListener { showSettings() }
        top.addView(gear, LinearLayout.LayoutParams(dp(56), dp(48)))
        content.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ऑर्ब
        orb = OrbView(this)
        content.addView(orb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))

        // स्थिति
        statusTv = TextView(this)
        statusTv.gravity = Gravity.CENTER
        statusTv.setTextColor(Color.parseColor("#7DD3FC"))
        statusTv.textSize = 13f
        statusTv.typeface = Typeface.MONOSPACE
        statusTv.setPadding(dp(8), dp(4), dp(8), dp(8))
        statusTv.text = "‘हेलो छोटू’ बोलिए या 🎤 दबाइए"
        content.addView(statusTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // बातचीत का लॉग
        scroll = ScrollView(this)
        scroll.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#0F1626"))
            setStroke(dp(1), Color.parseColor("#3322D3EE"))
        }
        logTv = TextView(this)
        logTv.setTextColor(Color.parseColor("#E5E7EB"))
        logTv.textSize = 15f
        logTv.setPadding(dp(8), dp(8), dp(8), dp(8))
        logTv.setTextIsSelectable(true)
        scroll.addView(logTv)
        content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f))

        // नीचे की पट्टी
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL

        input = EditText(this)
        input.hint = "या यहाँ लिखिए…"
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped()
                true
            } else false
        }
        bottom.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        langBtn = Button(this)
        langBtn.text = "हि"
        langBtn.setOnClickListener {
            if (voice.language == "hi-IN") {
                voice.language = "en-IN"
                langBtn.text = "EN"
            } else {
                voice.language = "hi-IN"
                langBtn.text = "हि"
            }
        }
        bottom.addView(langBtn, LinearLayout.LayoutParams(dp(56), dp(48)))

        val send = Button(this)
        send.text = "➤"
        send.setOnClickListener { sendTyped() }
        bottom.addView(send, LinearLayout.LayoutParams(dp(56), dp(48)))

        micBtn = Button(this)
        micBtn.text = "🎤"
        micBtn.setOnClickListener { onMicClick() }
        bottom.addView(micBtn, LinearLayout.LayoutParams(dp(64), dp(48)))

        content.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(content)
    }

    private fun onMicClick() {
        if (WakeService.running) {
            // बातचीत चालू हो तो बटन उसे बंद करता है, वरना शुरू
            if (WakeService.inSession) WakeService.endSessionNow() else WakeService.listenNow()
            return
        }
        if (localSession) {
            endLocalSession()
            return
        }
        if (busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions()
            return
        }
        localSession = true
        updateMic()
        orb.boot()
        voice.patient = true
        voice.listen()
    }

    private fun updateMic() {
        val active = localSession || WakeService.inSession
        micBtn.text = if (active) "⏹" else "🎤"
    }

    private fun onLocalVoice(text: String) {
        if (WakeService.isClose(text, SecurePrefs.wakeWord(this))) {
            endLocalSession()
            return
        }
        handle(text)
    }

    private fun endLocalSession() {
        localSession = false
        updateMic()
        voice.cancel()
        orb.state = OrbView.State.IDLE
        statusTv.text = "बातचीत बंद"
        voice.speak("ठीक है ${SecurePrefs.userName(this)}।")
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        handle(t)
    }

    private fun appendLog(line: String) {
        logTv.append(line + "\n\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- आदेश चलाना ----------

    private fun handle(text: String) {
        if (text.isBlank() || busy) return
        if (SecurePrefs.apiKey(this).isBlank()) {
            appendLog("⚠ पहले सेटिंग्स (⚙) में Claude API key डालें।")
            orb.state = OrbView.State.IDLE
            showSettings()
            return
        }
        busy = true
        appendLog("आप: $text")
        orb.state = OrbView.State.THINKING
        statusTv.text = "सोच रहा हूँ…"
        val a = JarvisAgent.shared(applicationContext)

        worker.execute {
            val reply = try {
                a.run(text) { s -> runOnUiThread { statusTv.text = s } }
            } catch (e: Exception) {
                "⚠ " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                appendLog("Chotu: $reply")
                if (reply.startsWith("⚠")) {
                    orb.state = OrbView.State.IDLE
                    statusTv.text = "गड़बड़ हुई"
                    localSession = false
                    updateMic()
                } else {
                    statusTv.text = ""
                    // बातचीत चालू हो तो जवाब के बाद अपने आप फिर सुनो
                    voice.speak(reply) { if (localSession) voice.listen() }
                }
            }
        }
    }

    // ---------- अनुमतियाँ ----------

    private fun askPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = wanted
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
    }

    private fun tryStartWake() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions()
            statusTv.text = "माइक की अनुमति देकर वेक-वर्ड फिर से चालू करें"
            return
        }
        try {
            WakeService.start(this)
            statusTv.text = "‘${SecurePrefs.wakeWord(this)}’ बोलिए — बातचीत ‘छोटू क्लोज़’ तक चलेगी"
        } catch (e: Exception) {
            statusTv.text = "वेक-वर्ड शुरू नहीं हुआ: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ---------- फ़िंगरप्रिंट/PIN लॉक ----------

    private fun lock() {
        unlocked = false
        content.visibility = View.INVISIBLE
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            // फ़ोन में स्क्रीन लॉक सेट नहीं है, तो सीधे खोलो
            unlock()
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Chotu अनलॉक करें")
            .setAllowedAuthenticators(auth)
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            }
        )
        prompt.authenticate(info)
    }

    private fun unlock() {
        unlocked = true
        content.visibility = View.VISIBLE
        if (SecurePrefs.apiKey(this).isBlank()) {
            showSettings()
        } else if (SecurePrefs.wakeEnabled(this) && !WakeService.running) {
            tryStartWake()
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = SystemClock.elapsedRealtime()
        WakeService.onAppBackground()
        WakeService.exchangeListener = null
        WakeService.ui = null
        if (localSession) endLocalSession()
    }

    override fun onStart() {
        super.onStart()
        WakeService.onAppForeground()
        WakeService.exchangeListener = { u, r ->
            appendLog("आप: $u")
            appendLog("Chotu: $r")
        }
        WakeService.ui = object : WakeService.Ui {
            override fun onState(s: OrbView.State) {
                orb.state = s
            }

            override fun onLevel(l: Float) {
                orb.level = l
            }

            override fun onCaption(t: String) {
                statusTv.text = t
            }

            override fun onSession(active: Boolean) {
                if (active) orb.boot() else orb.state = OrbView.State.IDLE
                updateMic()
            }
        }
        if (::micBtn.isInitialized) updateMic()
        // 60 सेकंड से ज़्यादा बाहर रहे तो दोबारा लॉक
        if (unlocked && lastStop != 0L && SystemClock.elapsedRealtime() - lastStop > 60_000) lock()
    }

    override fun onDestroy() {
        voice.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---------- सेटिंग्स ----------

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    private fun notifEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun showSettings() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(12), dp(20), dp(4))

        fun label(t: String): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.setTextColor(Color.parseColor("#9CA3AF"))
            tv.textSize = 12f
            tv.setPadding(0, dp(12), 0, 0)
            return tv
        }

        val hasKey = SecurePrefs.apiKey(this).isNotBlank()
        col.addView(label(if (hasKey) "Claude API key (सेव है, बदलना हो तो नई डालें)" else "Claude API key"))
        val keyEt = EditText(this)
        keyEt.hint = if (hasKey) "••••••••••" else "sk-ant-…"
        keyEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        col.addView(keyEt)

        col.addView(label("मॉडल (सस्ता चाहिए तो claude-haiku-5-5)"))
        val modelEt = EditText(this)
        modelEt.setText(SecurePrefs.model(this))
        modelEt.setSingleLine(true)
        col.addView(modelEt)

        col.addView(label("देश का कोड (भारत = 91), WhatsApp नंबर के लिए"))
        val ccEt = EditText(this)
        ccEt.setText(SecurePrefs.countryCode(this))
        ccEt.inputType = InputType.TYPE_CLASS_NUMBER
        col.addView(ccEt)

        col.addView(label("आपका नाम (Chotu इसी नाम से बुलाएगा)"))
        val nameEt = EditText(this)
        nameEt.setText(SecurePrefs.userName(this))
        nameEt.setSingleLine(true)
        col.addView(nameEt)

        col.addView(label("वेक-वर्ड (ऑफ़लाइन — गाना/मूवी नहीं रुकते, कुछ भी बाहर नहीं जाता)"))
        val wakeSw = SwitchCompat(this)
        wakeSw.text = "‘हेलो छोटू’ से जगाना चालू"
        wakeSw.isChecked = SecurePrefs.wakeEnabled(this)
        col.addView(wakeSw)
        val wakeEt = EditText(this)
        wakeEt.setText(SecurePrefs.wakeWord(this))
        wakeEt.hint = "जगाने का वाक्य, जैसे: हेलो छोटू"
        wakeEt.setSingleLine(true)
        col.addView(wakeEt)

        col.addView(label("चुप रहने पर बातचीत कितने मिनट बाद अपने आप बंद हो (0 = कभी नहीं, सिर्फ़ ‘छोटू क्लोज़’ से)"))
        val sleepEt = EditText(this)
        sleepEt.setText(SecurePrefs.sessionTimeoutMin(this).toString())
        sleepEt.inputType = InputType.TYPE_CLASS_NUMBER
        col.addView(sleepEt)
        val battBtn = Button(this)
        battBtn.text = "बैटरी ऑप्टिमाइज़ेशन से छूट दें (ज़रूरी)"
        battBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        col.addView(battBtn)

        col.addView(label("अनुमतियाँ"))
        val a11yBtn = Button(this)
        a11yBtn.text = (if (a11yEnabled()) "✅ " else "⬜ ") + "Accessibility (ऐप्स चलाना + तैरता अवतार)"
        a11yBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        col.addView(a11yBtn)

        val notifBtn = Button(this)
        notifBtn.text = (if (notifEnabled()) "✅ " else "⬜ ") + "Notification access (मैसेज पढ़ने के लिए)"
        notifBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        col.addView(notifBtn)

        val infoBtn = Button(this)
        infoBtn.text = "टॉगल ग्रे दिख रहा है? ऐप की जानकारी खोलें"
        infoBtn.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        col.addView(infoBtn)

        val hint = TextView(this)
        hint.text = "ग्रे दिखे तो: ऐप की जानकारी → ⋮ (ऊपर दाएँ) → “Allow restricted settings”, फिर Accessibility/Notification में दोबारा चालू करें।"
        hint.setTextColor(Color.parseColor("#9CA3AF"))
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        col.addView(hint)

        val sv = ScrollView(this)
        sv.addView(col)

        AlertDialog.Builder(this)
            .setTitle("Chotu सेटिंग्स")
            .setView(sv)
            .setPositiveButton("सेव") { _, _ ->
                val k = keyEt.text.toString().trim()
                if (k.isNotEmpty()) SecurePrefs.setApiKey(this, k)
                SecurePrefs.setModel(this, modelEt.text.toString())
                SecurePrefs.setCountryCode(this, ccEt.text.toString())
                SecurePrefs.setUserName(this, nameEt.text.toString())
                SecurePrefs.setWakeWord(this, wakeEt.text.toString())
                SecurePrefs.setSessionTimeoutMin(this, sleepEt.text.toString().toIntOrNull() ?: 0)
                SecurePrefs.setWakeEnabled(this, wakeSw.isChecked)
                if (wakeSw.isChecked) tryStartWake() else {
                    WakeService.stop(this)
                    statusTv.text = "सेटिंग्स सेव हो गईं"
                }
            }
            .setNegativeButton("बंद", null)
            .show()
    }
}
