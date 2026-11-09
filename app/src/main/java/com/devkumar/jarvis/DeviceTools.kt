package com.devkumar.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** फ़ोन के सिस्टम फ़ंक्शन: टॉर्च, वॉल्यूम, ब्राइटनेस, अलार्म, टाइमर, कैलेंडर, म्यूज़िक वगैरह। */
object DeviceTools {

    private fun int(desc: String) = JSONObject().put("type", "integer").put("description", desc)

    fun definitions(): JSONArray = JSONArray().apply {
        put(Actions.tool("torch", "Turn the phone flashlight on or off.",
            Actions.props("on" to JSONObject().put("type", "boolean").put("description", "true = on, false = off")),
            listOf("on")))
        put(Actions.tool("set_volume", "Change volume. stream: media, ring or alarm. action: up, down, mute, unmute or set (set needs percent).",
            Actions.props(
                "stream" to Actions.str("media, ring or alarm"),
                "action" to Actions.str("up, down, mute, unmute or set"),
                "percent" to int("0-100, only for action=set")
            ), listOf("stream", "action")))
        put(Actions.tool("set_ringer_mode", "Set the ringer mode.",
            Actions.props("mode" to Actions.str("normal, vibrate or silent")), listOf("mode")))
        put(Actions.tool("set_brightness", "Set the screen brightness (needs a one-time permission).",
            Actions.props("percent" to int("1-100")), listOf("percent")))
        put(Actions.tool("set_alarm", "Set an alarm at a time of day using the 24-hour clock.",
            Actions.props(
                "hour" to int("0-23"),
                "minute" to int("0-59"),
                "label" to Actions.str("Optional label")
            ), listOf("hour", "minute")))
        put(Actions.tool("set_timer", "Start a countdown timer.",
            Actions.props("seconds" to int("Length in seconds"), "label" to Actions.str("Optional label")),
            listOf("seconds")))
        put(Actions.tool("add_calendar_event", "Open a calendar event draft. The user presses Save.",
            Actions.props(
                "title" to Actions.str("Event title"),
                "start" to Actions.str("Local start time as yyyy-MM-dd HH:mm"),
                "duration_minutes" to int("Length in minutes, default 60"),
                "location" to Actions.str("Optional location"),
                "notes" to Actions.str("Optional notes")
            ), listOf("title", "start")))
        put(Actions.tool("open_settings", "Open a system settings page.",
            Actions.props("panel" to Actions.str("wifi, bluetooth, airplane, display, sound, battery, location, apps, nfc, hotspot, mobile_network or general")),
            listOf("panel")))
        put(Actions.tool("media_control", "Control music or video playback.",
            Actions.props("action" to Actions.str("play_pause, next, previous or stop")), listOf("action")))
        put(Actions.tool("open_camera", "Open the camera app (it cannot press the shutter).",
            Actions.props("mode" to Actions.str("photo or video")), listOf("mode")))
        put(Actions.tool("navigate", "Open Google Maps navigation to a place.",
            Actions.props("destination" to Actions.str("Place name or address")), listOf("destination")))
        put(Actions.tool("web_search", "Open a web search in the browser for the user to read.",
            Actions.props("query" to Actions.str("Search text")), listOf("query")))
        put(Actions.tool("battery_status", "Get the battery level and whether it is charging."))
    }

    /** इस ऑब्जेक्ट का टूल न हो तो null लौटाता है। */
    fun run(ctx: Context, name: String, input: JSONObject): String? = when (name) {
        "torch" -> torch(ctx, input.optBoolean("on", true))
        "set_volume" -> volume(ctx, input.optString("stream", "media"), input.optString("action"), input.optInt("percent", 50))
        "set_ringer_mode" -> ringer(ctx, input.optString("mode"))
        "set_brightness" -> brightness(ctx, input.optInt("percent", 50))
        "set_alarm" -> alarm(ctx, input.optInt("hour", -1), input.optInt("minute", -1), input.optString("label"))
        "set_timer" -> timer(ctx, input.optInt("seconds", 0), input.optString("label"))
        "add_calendar_event" -> calendar(
            ctx, input.optString("title"), input.optString("start"),
            input.optInt("duration_minutes", 60), input.optString("location"), input.optString("notes")
        )
        "open_settings" -> openSettings(ctx, input.optString("panel"))
        "media_control" -> media(ctx, input.optString("action"))
        "open_camera" -> camera(ctx, input.optString("mode"))
        "navigate" -> navigate(ctx, input.optString("destination"))
        "web_search" -> webSearch(ctx, input.optString("query"))
        "battery_status" -> battery(ctx)
        else -> null
    }

    // ---------- टॉर्च ----------

    private fun torch(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return "इस फ़ोन में टॉर्च नहीं मिली।"
        cm.setTorchMode(id, on)
        return if (on) "टॉर्च चालू कर दी।" else "टॉर्च बंद कर दी।"
    }

    // ---------- आवाज़ और ब्राइटनेस ----------

    private fun volume(ctx: Context, stream: String, action: String, percent: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = when (stream.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(st).coerceAtLeast(1)
        when (action.lowercase()) {
            "up" -> am.adjustStreamVolume(st, AudioManager.ADJUST_RAISE, 0)
            "down" -> am.adjustStreamVolume(st, AudioManager.ADJUST_LOWER, 0)
            "mute" -> if (st == AudioManager.STREAM_RING) {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0)
            }
            "unmute" -> am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0)
            "set" -> am.setStreamVolume(st, (max * percent.coerceIn(0, 100) / 100f).roundToInt(), 0)
            else -> return "action: up, down, mute, unmute या set होना चाहिए।"
        }
        val now = am.getStreamVolume(st)
        return "वॉल्यूम अब लगभग ${now * 100 / max}% है।"
    }

    private fun ringer(ctx: Context, mode: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (mode.lowercase()) {
            "normal" -> {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                "रिंगर चालू कर दिया।"
            }
            "vibrate" -> {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "फ़ोन वाइब्रेट पर कर दिया।"
            }
            "silent" -> try {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                "फ़ोन साइलेंट कर दिया।"
            } catch (e: SecurityException) {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "साइलेंट के लिए Do Not Disturb access चाहिए, इसलिए वाइब्रेट पर कर दिया।"
            }
            else -> "mode: normal, vibrate या silent होना चाहिए।"
        }
    }

    private fun brightness(ctx: Context, percent: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            Actions.launch(ctx, i)
            return "ब्राइटनेस बदलने की अनुमति नहीं है। अनुमति का पेज खोल दिया है। यूज़र वहाँ Chotu को अनुमति दें, फिर दोबारा कहें।"
        }
        val v = (255 * percent.coerceIn(1, 100) / 100f).roundToInt()
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
        return "ब्राइटनेस ${percent.coerceIn(1, 100)}% कर दी।"
    }

    // ---------- अलार्म, टाइमर, कैलेंडर ----------

    private fun alarm(ctx: Context, hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "घंटा 0-23 और मिनट 0-59 होना चाहिए।"
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val t = String.format(Locale.US, "%02d:%02d", hour, minute)
        return if (Actions.launch(ctx, i)) "अलार्म $t के लिए सेट कर दिया। (क्लॉक ऐप में जाँच सकते हैं)" else "क्लॉक ऐप नहीं खुल सका।"
    }

    private fun timer(ctx: Context, seconds: Int, label: String): String {
        if (seconds !in 1..86400) return "समय 1 सेकंड से 24 घंटे के बीच होना चाहिए।"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (Actions.launch(ctx, i)) "$seconds सेकंड का टाइमर शुरू कर दिया।" else "क्लॉक ऐप नहीं खुल सका।"
    }

    private fun calendar(ctx: Context, title: String, start: String, minutes: Int, location: String, notes: String): String {
        if (title.isBlank()) return "इवेंट का नाम चाहिए।"
        val parsed = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(start.trim())?.time
        } catch (e: Exception) {
            null
        }
        if (parsed == null) return "शुरू होने का समय 'yyyy-MM-dd HH:mm' फ़ॉर्मेट में चाहिए।"
        val end = parsed + minutes.coerceIn(5, 1440) * 60_000L
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsed)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.DESCRIPTION, notes)
        return if (Actions.launch(ctx, i)) "कैलेंडर में इवेंट का ड्राफ्ट खुल गया। यूज़र Save खुद दबाएँ।" else "कैलेंडर ऐप नहीं खुल सका।"
    }

    // ---------- सेटिंग्स, कैमरा, म्यूज़िक, नक्शा, सर्च, बैटरी ----------

    private fun openSettings(ctx: Context, panel: String): String {
        val action = when (panel.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "hotspot" -> "android.settings.TETHER_SETTINGS"
            "mobile_network" -> Settings.ACTION_WIRELESS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (Actions.launch(ctx, Intent(action))) "सेटिंग्स का पेज (${panel.ifBlank { "general" }}) खुल गया।" else "सेटिंग्स नहीं खुलीं।"
    }

    private fun media(ctx: Context, action: String): String {
        val code = when (action.lowercase()) {
            "play_pause", "play", "pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "action: play_pause, next, previous या stop होना चाहिए।"
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "म्यूज़िक कंट्रोल भेज दिया: $action।"
    }

    private fun camera(ctx: Context, mode: String): String {
        val action = if (mode.lowercase().startsWith("v")) {
            MediaStore.INTENT_ACTION_VIDEO_CAMERA
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }
        return if (Actions.launch(ctx, Intent(action))) "कैमरा खुल गया। फ़ोटो/वीडियो का बटन यूज़र खुद दबाएँ।" else "कैमरा नहीं खुल सका।"
    }

    private fun navigate(ctx: Context, destination: String): String {
        if (destination.isBlank()) return "जगह का नाम चाहिए।"
        val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
            .setPackage("com.google.android.apps.maps")
        if (Actions.launch(ctx, maps)) return "Maps में '$destination' का रास्ता खुल गया।"
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
        return if (Actions.launch(ctx, geo)) "नक्शे में '$destination' खुल गया।" else "नक्शा ऐप नहीं खुल सका।"
    }

    private fun webSearch(ctx: Context, query: String): String {
        if (query.isBlank()) return "सर्च का टेक्स्ट चाहिए।"
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (Actions.launch(ctx, i)) return "'$query' का वेब सर्च खुल गया। नतीजे स्क्रीन पर हैं।"
        val url = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return if (Actions.launch(ctx, url)) "'$query' का वेब सर्च खुल गया।" else "ब्राउज़र नहीं खुल सका।"
    }

    private fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0) return "बैटरी की जानकारी नहीं मिली।"
        return "बैटरी ${level * 100 / scale.coerceAtLeast(1)}% है, " + if (charging) "चार्ज हो रही है।" else "चार्ज नहीं हो रही।"
    }
}
