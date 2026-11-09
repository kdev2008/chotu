package com.devkumar.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * बातचीत के दौरान किसी भी ऐप के ऊपर तैरता Chotu अवतार।
 * Accessibility सेवा की ओवरले विंडो इस्तेमाल करता है, इसलिए अलग अनुमति नहीं चाहिए।
 * टच पार हो जाते हैं (NOT_TOUCHABLE), यानी नीचे का ऐप आप चलाते रह सकते हैं।
 * सब कुछ मुख्य थ्रेड से बुलाएँ।
 */
object AvatarOverlay {

    private var root: View? = null
    private var orb: OrbView? = null
    private var caption: TextView? = null
    private var wm: WindowManager? = null

    val showing: Boolean get() = root != null

    fun show(): Boolean {
        if (root != null) {
            orb?.boot()
            return true
        }
        val svc = JarvisAccessibilityService.instance ?: return false
        val manager = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = svc.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(svc)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(14), dp(10), dp(14), dp(12))
        box.background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(Color.parseColor("#E6060B16"))
            setStroke(dp(1), Color.parseColor("#8822D3EE"))
        }

        val o = OrbView(svc)
        o.showLabel = true
        box.addView(o, LinearLayout.LayoutParams(dp(190), dp(210)))

        val cap = TextView(svc)
        cap.setTextColor(Color.parseColor("#E5E7EB"))
        cap.textSize = 13f
        cap.typeface = Typeface.MONOSPACE
        cap.gravity = Gravity.CENTER
        cap.maxLines = 4
        cap.ellipsize = TextUtils.TruncateAt.END
        cap.visibility = View.GONE
        box.addView(cap, LinearLayout.LayoutParams(dp(270), LinearLayout.LayoutParams.WRAP_CONTENT))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(60)

        try {
            manager.addView(box, lp)
        } catch (e: Exception) {
            return false
        }
        box.alpha = 0f
        box.animate().alpha(1f).setDuration(250).start()
        root = box
        orb = o
        caption = cap
        wm = manager
        o.boot()
        return true
    }

    fun hide() {
        val r = root ?: return
        try {
            wm?.removeView(r)
        } catch (_: Exception) {
        }
        root = null
        orb = null
        caption = null
        wm = null
    }

    fun setState(s: OrbView.State) {
        orb?.state = s
    }

    fun setLevel(l: Float) {
        orb?.level = l
    }

    fun setCaption(t: String) {
        val cap = caption ?: return
        cap.text = t
        cap.visibility = if (t.isBlank()) View.GONE else View.VISIBLE
    }
}
