package com.soltini.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.soltini.app.audio.JarvisSoundEngine
import kotlin.math.sin

/**
 * MyraDynamicIslandHudCapsule — Iron Man / iOS Dynamic Island Floating Capsule
 *
 * Sits at top-center under the notch/status bar.
 *  - Compact Pill: 190dp x 36dp with real-time pulsing waveform & live status
 *  - Tap to Expand: Smoothly morphs into a full tactical control island
 *    (Microphone quick-mute, Vision Trigger, Voice Secretary Mode, Dismiss)
 */
@SuppressLint("ViewConstructor")
class MyraDynamicIslandHudCapsule(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var isExpanded = false
    private val compactWidth = dp(190)
    private val compactHeight = dp(36)
    private val expandedWidth = dp(320)
    private val expandedHeight = dp(140)

    private val backgroundDrawable = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(Color.parseColor("#E60B0F19"))
        setStroke(dp(1), Color.parseColor("#6600E5FF"))
    }

    private val compactLayout: LinearLayout
    private val expandedLayout: LinearLayout
    private val statusText: TextView
    private val waveformView: MiniWaveformView
    private val taskTitleText: TextView
    private val taskSubtitleText: TextView

    var onMicToggle: (() -> Unit)? = null
    var onVisionTrigger: (() -> Unit)? = null
    var onSecretaryToggle: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        background = backgroundDrawable
        elevation = dp(12).toFloat()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        // ── 1. Compact Layout (Pill Mode) ───────────────────────────────────
        compactLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        waveformView = MiniWaveformView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(18))
        }
        compactLayout.addView(waveformView)

        statusText = TextView(context).apply {
            text = "MYRA • ACTIVE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        compactLayout.addView(statusText)

        val dotView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00F5D4"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7))
        }
        compactLayout.addView(dotView)
        addView(compactLayout)

        // ── 2. Expanded Layout (HUD Island Mode) ───────────────────────────
        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        taskTitleText = TextView(context).apply {
            text = "MYRA TACTICAL HUD"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        expandedLayout.addView(taskTitleText)

        taskSubtitleText = TextView(context).apply {
            text = "Autonomous Voice & System Operator Linked"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }
        expandedLayout.addView(taskSubtitleText)

        // Action Buttons Row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun makePillButton(label: String, colorHex: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.parseColor(colorHex))
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#2200E5FF"))
                    setStroke(dp(1), Color.parseColor(colorHex).and(0x77FFFFFF))
                }
                setOnClickListener {
                    JarvisSoundEngine.playTacticalBlip()
                    onClick()
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = lp
            }
        }

        btnRow.addView(makePillButton("MIC", "#00E5FF") { onMicToggle?.invoke() })
        btnRow.addView(makePillButton("VISION", "#00F5D4") { onVisionTrigger?.invoke() })
        btnRow.addView(makePillButton("SECRETARY", "#38BDF8") { onSecretaryToggle?.invoke() })
        btnRow.addView(makePillButton("✕", "#EF4444") { toggleExpand(false); onDismiss?.invoke() })

        expandedLayout.addView(btnRow)
        addView(expandedLayout)

        // Click to toggle expansion
        setOnClickListener {
            toggleExpand(!isExpanded)
        }
    }

    fun updateStatus(text: String, isProcessing: Boolean = false) {
        statusText.text = text
        taskSubtitleText.text = text
        waveformView.isAnimating = isProcessing
    }

    private fun toggleExpand(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand
        JarvisSoundEngine.playTacticalBlip()

        val startW = if (expand) compactWidth else expandedWidth
        val targetW = if (expand) expandedWidth else compactWidth
        val startH = if (expand) compactHeight else expandedHeight
        val targetH = if (expand) expandedHeight else compactHeight

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { va ->
                val frac = va.animatedFraction
                val w = (startW + (targetW - startW) * frac).toInt()
                val h = (startH + (targetH - startH) * frac).toInt()
                layoutParams = layoutParams.apply {
                    width = w
                    height = h
                }
                requestLayout()
            }
        }

        if (expand) {
            compactLayout.visibility = View.GONE
            expandedLayout.visibility = View.VISIBLE
            backgroundDrawable.cornerRadius = dp(20).toFloat()
        } else {
            expandedLayout.visibility = View.GONE
            compactLayout.visibility = View.VISIBLE
            backgroundDrawable.cornerRadius = dp(24).toFloat()
        }

        anim.start()
    }

    /**
     * Mini live audio waveform bar renderer.
     */
    private class MiniWaveformView(context: Context) : View(context) {
        var isAnimating = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        private var phase = 0f
        private val handler = Handler(Looper.getMainLooper())
        private val ticker = object : Runnable {
            override fun run() {
                if (isAnimating) {
                    phase += 0.22f
                    invalidate()
                }
                handler.postDelayed(this, 35)
            }
        }

        init {
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            handler.removeCallbacks(ticker)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val barCount = 4
            val barWidth = w / (barCount * 2f)

            for (i in 0 until barCount) {
                val wave = (sin(phase + i * 1.2) * 0.5 + 0.5).toFloat()
                val barH = (h * (0.3f + wave * 0.7f)).coerceAtLeast(3f)
                val left = i * barWidth * 2f + barWidth * 0.5f
                val top = (h - barH) / 2f
                val rect = RectF(left, top, left + barWidth, top + barH)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }
        }
    }
}
