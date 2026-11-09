package com.devkumar.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * दूसरे ऐप्स की स्क्रीन पढ़ने और टैप/टाइप करने के लिए।
 * सुरक्षा: पासवर्ड फ़ील्ड कभी नहीं पढ़े/भरे जाते, और Send/Post/Pay/Delete जैसे बटन टैप नहीं होते।
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null

        private const val RISK_MSG =
            "यह बटन जोखिम वाला है (भेजना/पोस्ट/भुगतान/डिलीट जैसा)। इसे Chotu नहीं दबाएगा। यूज़र को कहें कि खुद दबाएँ।"

        private val RISKY_EN = Regex(
            "\\b(send|post|publish|pay|delete|remove|confirm|submit|transfer|buy|purchase|order|place order|sign out|log out|logout)\\b",
            RegexOption.IGNORE_CASE
        )
        private val RISKY_HI = listOf(
            "भेजें", "भेजो", "सेंड", "पोस्ट", "भुगतान", "पेमेंट", "डिलीट",
            "हटाएं", "हटाएँ", "खरीदें", "ऑर्डर", "कन्फर्म", "पुष्टि", "साइन आउट", "लॉग आउट"
        )

        fun isRisky(label: String): Boolean =
            RISKY_EN.containsMatchIn(label) || RISKY_HI.any { label.contains(it) }
    }

    override fun onServiceConnected() {
        instance = this
        // रीस्टार्ट के बाद वेक-वर्ड अपने आप चालू करने की कोशिश (Android रोके तो चुपचाप छोड़ देते हैं)
        try {
            if (SecurePrefs.wakeEnabled(this) && SecurePrefs.apiKey(this).isNotBlank() && !WakeService.running) {
                WakeService.start(this)
            }
        } catch (e: Exception) {
            // ऐप खोलने पर सेवा शुरू हो जाएगी
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AvatarOverlay.hide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AvatarOverlay.hide()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ज़रूरत नहीं: Chotu सिर्फ़ तब स्क्रीन देखता है जब आप कोई काम देते हैं।
    }

    override fun onInterrupt() {}

    // ---------- पढ़ना ----------

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "(स्क्रीन पढ़ी नहीं जा सकी)"
        val sb = StringBuilder("ऐप: ${root.packageName}\n")
        walk(root, sb)
        return sb.toString().take(6000)
    }

    private fun walk(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null || !n.isVisibleToUser) return
        if (n.isPassword) {
            sb.append("[पासवर्ड फ़ील्ड छिपा है]\n")
            return
        }
        val label = (n.text?.toString() ?: n.contentDescription?.toString())?.trim()
        if (!label.isNullOrEmpty()) {
            when {
                n.isEditable -> sb.append("[इनपुट] ")
                n.isClickable -> sb.append("[बटन] ")
            }
            sb.append(label).append('\n')
        } else if (n.isEditable) {
            sb.append("[खाली इनपुट]\n")
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), sb)
    }

    // ---------- टैप ----------

    fun tapText(label: String): String {
        if (label.isBlank()) return "लेबल चाहिए।"
        if (isRisky(label)) return RISK_MSG
        val root = rootInActiveWindow ?: return "स्क्रीन उपलब्ध नहीं।"
        val nodes = root.findAccessibilityNodeInfosByText(label)
        if (nodes.isNullOrEmpty()) return "'$label' स्क्रीन पर नहीं दिखा। read_screen से देखें।"
        for (n in nodes) {
            if (!n.isVisibleToUser) continue
            val target = clickableAncestor(n) ?: continue
            // खोजा गया शब्द नहीं, नोड का असली लेबल जाँचो (ताकि "Sen" जैसे आंशिक शब्द से बचाव न हो सके)
            val actual = listOfNotNull(n.text, n.contentDescription, target.text, target.contentDescription)
                .joinToString(" ")
            if (isRisky(actual)) return RISK_MSG
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "टैप किया: $label"
        }
        return "'$label' दिखा, पर टैप नहीं हो सका।"
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    // ---------- टाइप ----------

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "स्क्रीन उपलब्ध नहीं।"
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        val target = focused ?: findEditable(root) ?: return "कोई टेक्स्ट बॉक्स नहीं मिला।"
        if (target.isPassword) return "पासवर्ड फ़ील्ड में टाइप नहीं करूँगा। यूज़र खुद डालें।"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "टाइप कर दिया।" else "टाइप नहीं हो सका।"
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null || !n.isVisibleToUser) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            findEditable(n.getChild(i))?.let { return it }
        }
        return null
    }

    // ---------- स्क्रॉल और सिस्टम की ----------

    fun scroll(direction: String): String {
        val root = rootInActiveWindow ?: return "स्क्रीन उपलब्ध नहीं।"
        val node = findScrollable(root) ?: return "स्क्रॉल होने वाली चीज़ नहीं मिली।"
        val action = if (direction.lowercase().startsWith("u")) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return if (node.performAction(action)) "स्क्रॉल किया।" else "स्क्रॉल नहीं हो सका।"
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            findScrollable(n.getChild(i))?.let { return it }
        }
        return null
    }

    fun pressKey(key: String): String {
        val action = when (key.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            "lock_screen" ->
                if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN
                else return "इस Android वर्ज़न में लॉक स्क्रीन एक्शन नहीं है।"
            "screenshot" ->
                if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT
                else return "इस Android वर्ज़न में स्क्रीनशॉट एक्शन नहीं है।"
            else -> return "अज्ञात की: $key"
        }
        return if (performGlobalAction(action)) "दबा दिया: $key" else "नहीं दबा सका।"
    }
}
