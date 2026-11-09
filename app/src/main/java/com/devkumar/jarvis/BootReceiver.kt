package com.devkumar.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** फ़ोन चालू होने पर वेक-वर्ड सेवा चालू करने की कोशिश। Android रोक दे तो चुपचाप छोड़ देते हैं। */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (SecurePrefs.wakeEnabled(context) && SecurePrefs.apiKey(context).isNotBlank()) {
                WakeService.start(context)
            }
        } catch (e: Exception) {
            // ऐप एक बार खोलने पर सेवा फिर शुरू हो जाएगी
        }
    }
}
