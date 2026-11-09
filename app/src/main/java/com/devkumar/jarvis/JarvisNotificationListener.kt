package com.devkumar.jarvis

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** हाल की नोटिफ़िकेशन सिर्फ़ फ़ोन की RAM में रखता है (डिस्क या नेटवर्क पर नहीं)। */
class JarvisNotificationListener : NotificationListenerService() {

    data class Entry(val app: String, val title: String, val text: String, val time: Long)

    companion object {
        private val lock = Any()
        private val buf = ArrayDeque<Entry>()

        @Volatile
        var connected = false

        fun snapshot(filter: String?, max: Int = 15): String {
            if (!connected) {
                return "Notification access चालू नहीं है। यूज़र से Chotu सेटिंग्स (⚙) में इसे चालू करने को कहें।"
            }
            val now = System.currentTimeMillis()
            val items = synchronized(lock) { buf.toList() }
                .filter { now - it.time < 24L * 60 * 60 * 1000 }
                .filter { filter == null || it.app.contains(filter, ignoreCase = true) }
                .sortedByDescending { it.time }
                .take(max)
            if (items.isEmpty()) return "कोई हाल की नोटिफ़िकेशन नहीं है।"
            return items.joinToString("\n") {
                val mins = ((now - it.time) / 60_000).toInt()
                val ago = if (mins < 60) "$mins मिनट पहले" else "${mins / 60} घंटे पहले"
                "• [${it.app}] ${it.title}: ${it.text} ($ago)"
            }
        }
    }

    override fun onListenerConnected() {
        connected = true
        try {
            activeNotifications?.forEach { add(it) }
        } catch (e: Exception) {
            // पुरानी नोटिफ़िकेशन न मिलें तो ठीक है
        }
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        add(sbn)
    }

    private fun add(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        synchronized(lock) {
            buf.addLast(Entry(app, title, text.take(300), sbn.postTime))
            while (buf.size > 50) buf.removeFirst()
        }
    }
}
