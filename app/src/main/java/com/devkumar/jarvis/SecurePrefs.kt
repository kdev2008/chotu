package com.devkumar.jarvis

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API key और सेटिंग्स Android Keystore से एन्क्रिप्टेड रूप में फ़ोन में रहती हैं। */
object SecurePrefs {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    private const val FILE = "jarvis_secure"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val existing = cached
        if (existing != null) return existing
        return synchronized(this) {
            cached ?: create(ctx.applicationContext).also { cached = it }
        }
    }

    private fun create(app: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiKey(ctx: Context): String = prefs(ctx).getString("api_key", "") ?: ""

    fun setApiKey(ctx: Context, value: String) {
        prefs(ctx).edit().putString("api_key", value.trim()).apply()
    }

    fun model(ctx: Context): String =
        (prefs(ctx).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }

    fun setModel(ctx: Context, value: String) {
        prefs(ctx).edit().putString("model", value.trim()).apply()
    }

    fun countryCode(ctx: Context): String =
        (prefs(ctx).getString("country_code", "91") ?: "91").ifBlank { "91" }

    fun setCountryCode(ctx: Context, value: String) {
        prefs(ctx).edit().putString("country_code", value.filter { it.isDigit() }).apply()
    }

    const val DEFAULT_WAKE_WORD = "हेलो छोटू"

    fun wakeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("wake_enabled", true)

    fun setWakeEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("wake_enabled", value).apply()
    }

    // v1.3 से नई कुंजी, ताकि पुराना "छोटू" अपने आप "हेलो छोटू" बन जाए
    fun wakeWord(ctx: Context): String =
        (prefs(ctx).getString("wake_phrase", DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD).ifBlank { DEFAULT_WAKE_WORD }

    fun setWakeWord(ctx: Context, value: String) {
        prefs(ctx).edit().putString("wake_phrase", value.trim().ifBlank { DEFAULT_WAKE_WORD }).apply()
    }

    /** इतने मिनट चुप रहने पर बातचीत अपने आप बंद (0 = कभी नहीं, सिर्फ़ "छोटू क्लोज़" से)। */
    fun sessionTimeoutMin(ctx: Context): Int = prefs(ctx).getInt("session_timeout_min", 0)

    fun setSessionTimeoutMin(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("session_timeout_min", value.coerceIn(0, 120)).apply()
    }

    const val DEFAULT_USER_NAME = "देव"

    fun userName(ctx: Context): String =
        (prefs(ctx).getString("user_name", DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME).ifBlank { DEFAULT_USER_NAME }

    fun setUserName(ctx: Context, value: String) {
        prefs(ctx).edit().putString("user_name", value.trim().ifBlank { DEFAULT_USER_NAME }).apply()
    }
}
