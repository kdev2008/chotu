package com.devkumar.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Chotu का साइ-फ़ाई HUD अवतार: घूमते घेरे, आवाज़ के साथ नाचती पट्टियाँ,
 * सोचते समय रडार, और जागने पर "बूट" एनिमेशन।
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    /** माइक की आवाज़ का स्तर, 0..1 */
    @Volatile
    var level: Float = 0f

    /** नीचे स्थिति का लेबल (LISTENING आदि) दिखाएँ */
    var showLabel: Boolean = true

    private val density = resources.displayMetrics.density
    private var time = 0f
    private var spin = 0f
    private var lastFrame = 0L
    private var smooth = 0f
    private var bootP = 1f
    private var curColor = colorFor(State.IDLE)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 7f * density), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.25f
    }
    private val hex = Path()
    private val rect = RectF()
    private val matrix = Matrix()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { tick() }
    }

    /** जागने का एनिमेशन: अवतार छोटा से बड़ा होकर चमकता है। */
    fun boot() {
        bootP = 0f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun colorFor(s: State): Int = when (s) {
        State.IDLE -> Color.parseColor("#22D3EE")
        State.LISTENING -> Color.parseColor("#34D399")
        State.THINKING -> Color.parseColor("#FBBF24")
        State.SPEAKING -> Color.parseColor("#A78BFA")
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now
        time += dt
        val speed = when (state) {
            State.IDLE -> 1f
            State.LISTENING -> 1.6f
            State.THINKING -> 3.5f
            State.SPEAKING -> 2.2f
        }
        spin += dt * speed
        if (bootP < 1f) bootP = min(1f, bootP + dt / 0.9f)
        curColor = ColorUtils.blendARGB(curColor, colorFor(state), min(1f, dt * 6f))
        invalidate()
    }

    private var fade = 1f

    private fun a(color: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(color, (alpha * fade).toInt().coerceIn(0, 255))

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val labelSpace = if (showLabel) 26f * density else 0f
        val cx = w / 2f
        val cy = (h - labelSpace) / 2f
        val r0 = min(w, h - labelSpace) * 0.46f
        val inv = 1f - bootP
        val scale = 0.6f + 0.4f * (1f - inv * inv * inv)
        val r = r0 * scale
        fade = 0.25f + 0.75f * bootP
        smooth += (level - smooth) * 0.25f
        val col = curColor
        val twoPi = (2 * PI).toFloat()

        val energy = when (state) {
            State.IDLE -> 0.12f
            State.LISTENING -> 0.25f + smooth * 0.9f
            State.THINKING -> 0.35f
            State.SPEAKING -> 0.45f + 0.4f * abs(sin(time * 9f)) * abs(sin(time * 3.3f + 1f))
        }

        // 1) पृष्ठभूमि की चमक
        fill.shader = RadialGradient(
            cx, cy, r * 1.15f,
            intArrayOf(a(col, 90), a(col, 25), a(col, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 1.15f, fill)
        fill.shader = null

        // 2) बाहरी टिक-घेरा (धीरे घूमता)
        stroke.strokeCap = Paint.Cap.BUTT
        val tickRot = spin * 0.10f
        for (i in 0 until 72) {
            val ang = tickRot + i * twoPi / 72f
            val long = i % 6 == 0
            val outer = r * 0.99f
            val inner = r * (if (long) 0.92f else 0.955f)
            stroke.strokeWidth = (if (long) 2f else 1f) * density
            stroke.color = a(col, if (long) 200 else 110)
            c.drawLine(
                cx + cos(ang) * inner, cy + sin(ang) * inner,
                cx + cos(ang) * outer, cy + sin(ang) * outer, stroke
            )
        }

        // 3) दिशा-निशान (N/E/S/W)
        stroke.strokeWidth = 2f * density
        stroke.color = a(Color.WHITE, 170)
        for (k in 0 until 4) {
            val ang = k * twoPi / 4f
            c.drawLine(
                cx + cos(ang) * r * 1.02f, cy + sin(ang) * r * 1.02f,
                cx + cos(ang) * r * 1.09f, cy + sin(ang) * r * 1.09f, stroke
            )
        }

        // 4) खंडित चाप (उलटी दिशा में घूमते)
        stroke.strokeWidth = 3f * density
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = a(col, 230)
        val arcR = r * 0.85f
        rect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        val arcRot = -spin * 25f
        for (k in 0 until 3) c.drawArc(rect, arcRot + k * 120f, 78f, false, stroke)

        // 5) बिंदीदार घेरा
        dashed.strokeWidth = 1.5f * density
        dashed.color = a(col, 150)
        c.save()
        c.rotate(spin * 35f, cx, cy)
        c.drawCircle(cx, cy, r * 0.76f, dashed)
        c.restore()

        // 6) सोचते समय रडार स्वीप
        if (state == State.THINKING) {
            val sweep = SweepGradient(
                cx, cy,
                intArrayOf(a(col, 0), a(col, 0), a(col, 170)),
                floatArrayOf(0f, 0.7f, 1f)
            )
            matrix.setRotate(spin * 120f, cx, cy)
            sweep.setLocalMatrix(matrix)
            fill.shader = sweep
            c.drawCircle(cx, cy, r * 0.72f, fill)
            fill.shader = null
        }

        // 7) आवाज़ की पट्टियाँ (गोल स्पेक्ट्रम)
        stroke.strokeWidth = 2f * density
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = a(col, 210)
        val bars = 60
        val barBase = r * 0.5f
        for (i in 0 until bars) {
            val ang = i * twoPi / bars - spin * 0.2f
            val wave = (0.5f + 0.5f * sin(i * 0.7f + time * 8f)) * (0.6f + 0.4f * sin(i * 1.3f - time * 5f))
            val len = r * (0.03f + 0.17f * energy * abs(wave))
            c.drawLine(
                cx + cos(ang) * barBase, cy + sin(ang) * barBase,
                cx + cos(ang) * (barBase + len), cy + sin(ang) * (barBase + len), stroke
            )
        }

        // 8) षट्भुज (दो परतें, उलटी दिशा)
        stroke.strokeCap = Paint.Cap.BUTT
        drawHex(c, cx, cy, r * 0.42f, spin * 0.25f)
        stroke.strokeWidth = 1.5f * density
        stroke.color = a(col, 190)
        c.drawPath(hex, stroke)
        drawHex(c, cx, cy, r * 0.36f, -spin * 0.4f)
        stroke.strokeWidth = 1f * density
        stroke.color = a(col, 110)
        c.drawPath(hex, stroke)

        // 9) कोर (चमकता गोला)
        val coreR = r * (0.24f + 0.025f * sin(time * 2f) + energy * 0.06f)
        fill.shader = RadialGradient(
            cx - coreR * 0.25f, cy - coreR * 0.25f, coreR * 1.3f,
            intArrayOf(
                a(ColorUtils.blendARGB(col, Color.WHITE, 0.75f), 255),
                a(col, 255),
                a(ColorUtils.blendARGB(col, Color.BLACK, 0.5f), 255)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, coreR, fill)
        fill.shader = null
        fill.color = a(Color.WHITE, (120 + 100 * energy).toInt())
        c.drawCircle(cx, cy, coreR * 0.32f, fill)

        // 10) परिक्रमा करते कण
        val orbits = floatArrayOf(0.62f, 0.69f, 0.93f)
        val speeds = floatArrayOf(0.7f, -0.95f, 0.45f)
        for (k in orbits.indices) {
            val ang = spin * speeds[k] + k * 2.1f
            val px = cx + cos(ang) * r * orbits[k]
            val py = cy + sin(ang) * r * orbits[k]
            fill.color = a(col, 90)
            c.drawCircle(px, py, 6f * density, fill)
            fill.color = a(Color.WHITE, 230)
            c.drawCircle(px, py, 2.2f * density, fill)
        }

        // 11) जागने की लहर
        if (bootP < 1f) {
            stroke.strokeWidth = 3f * density
            stroke.color = ColorUtils.setAlphaComponent(col, (255 * (1f - bootP)).toInt())
            c.drawCircle(cx, cy, r0 * (0.3f + 0.9f * bootP), stroke)
        }

        // 12) स्थिति लेबल
        if (showLabel) {
            val label = when (state) {
                State.IDLE -> "STANDBY"
                State.LISTENING -> "LISTENING"
                State.THINKING -> "PROCESSING"
                State.SPEAKING -> "SPEAKING"
            }
            text.textSize = 11f * density
            val blink = if (sin(time * 6f) > 0f) 255 else 90
            text.color = a(col, blink)
            val ty = h - 8f * density
            c.drawText("◉ $label", cx, ty, text)
        }
    }

    private fun drawHex(c: Canvas, cx: Float, cy: Float, rad: Float, rot: Float) {
        hex.reset()
        for (k in 0 until 6) {
            val ang = rot + k * (PI / 3).toFloat()
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad
            if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
    }
}
