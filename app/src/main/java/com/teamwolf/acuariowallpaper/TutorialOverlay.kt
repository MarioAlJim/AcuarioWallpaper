package com.teamwolf.acuariowallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/** One step of the onboarding tutorial: the view to spotlight plus its explanation. */
data class TutorialStep(
    val targetId: Int,
    @StringRes val title: Int,
    @StringRes val description: Int
)

/**
 * Lightweight step-by-step coach-mark tutorial for the settings screen.
 */
class TutorialOverlay(private val activity: Activity) {

    private var steps: List<TutorialStep> = emptyList()
    private var stepIndex = 0
    private var onFinished: () -> Unit = {}

    private val rootContent: ViewGroup by lazy { activity.findViewById(android.R.id.content) }
    private var overlayContainer: FrameLayout? = null
    private var spotlightView: SpotlightView? = null
    private var cardView: MaterialCardView? = null
    private var stepCounterText: TextView? = null
    private var titleText: TextView? = null
    private var descriptionText: TextView? = null
    private var nextButton: TextView? = null

    fun isRunning(): Boolean = overlayContainer != null

    fun start(steps: List<TutorialStep>, onFinished: () -> Unit = {}) {
        if (steps.isEmpty() || activity.isFinishing || activity.isDestroyed) return
        finish()
        this.steps = steps
        this.stepIndex = 0
        this.onFinished = onFinished
        buildOverlay()
        showStep(0)
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun dpF(value: Int): Float = value * activity.resources.displayMetrics.density

    private fun buildOverlay() {
        val container = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
        }

        val spotlight = SpotlightView(activity)
        container.addView(
            spotlight,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val card = buildCard()
        card.visibility = View.INVISIBLE
        val cardWidth = (rootContent.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels) * 0.86f
        container.addView(card, FrameLayout.LayoutParams(cardWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))

        rootContent.addView(
            container,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        overlayContainer = container
        spotlightView = spotlight
        cardView = card
    }

    private fun buildCard(): MaterialCardView {
        val ctx = activity
        val card = MaterialCardView(ctx).apply {
            radius = dpF(14)
            cardElevation = dpF(8)
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.settings_card_bg))
            strokeColor = ContextCompat.getColor(ctx, R.color.settings_accent)
            strokeWidth = dp(1)
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val stepCounter = TextView(ctx).apply {
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.settings_accent_text))
            setTypeface(typeface, Typeface.BOLD)
        }
        val title = TextView(ctx).apply {
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.settings_text_primary))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }
        val description = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.settings_text_secondary))
            setLineSpacing(dpF(2), 1f)
        }

        val skip = TextView(ctx).apply {
            text = activity.getString(R.string.tutorial_skip)
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.settings_text_secondary))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(ctx, android.R.drawable.list_selector_background)
            setOnClickListener { finish() }
        }
        val next = TextView(ctx).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            setPadding(dp(18), dp(9), dp(18), dp(9))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(20)
                setColor(ContextCompat.getColor(ctx, R.color.settings_accent))
            }
            setOnClickListener { goToNextStep() }
        }

        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(skip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        content.addView(stepCounter)
        content.addView(title)
        content.addView(description)
        content.addView(buttonRow)
        card.addView(content)

        stepCounterText = stepCounter
        titleText = title
        descriptionText = description
        nextButton = next

        return card
    }

    private fun goToNextStep() {
        if (stepIndex < steps.size - 1) {
            stepIndex += 1
            showStep(stepIndex)
        } else {
            finish()
        }
    }

    private fun showStep(index: Int) {
        val step = steps.getOrNull(index) ?: return finish()
        val target = activity.findViewById<View>(step.targetId)
        if (target == null || target.width == 0 || target.height == 0) {
            if (index < steps.size - 1) {
                stepIndex = index + 1
                showStep(stepIndex)
            } else {
                finish()
            }
            return
        }

        cardView?.visibility = View.INVISIBLE
        target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), false)
        target.postDelayed({
            if (activity.isFinishing || activity.isDestroyed || overlayContainer == null) return@postDelayed
            positionOverlayForTarget(target, step, index)
        }, 320)
    }

    private fun positionOverlayForTarget(target: View, step: TutorialStep, index: Int) {
        val targetLoc = IntArray(2)
        target.getLocationInWindow(targetLoc)
        val rootLoc = IntArray(2)
        rootContent.getLocationInWindow(rootLoc)

        val pad = dpF(6)
        val left = (targetLoc[0] - rootLoc[0]) - pad
        val top = (targetLoc[1] - rootLoc[1]) - pad
        val right = left + target.width + pad * 2
        val bottom = top + target.height + pad * 2
        val highlightRect = RectF(left, top, right, bottom)
        spotlightView?.highlightRect = highlightRect

        stepCounterText?.text = activity.getString(R.string.tutorial_step_counter, index + 1, steps.size)
        titleText?.setText(step.title)
        descriptionText?.setText(step.description)
        nextButton?.setText(if (index == steps.size - 1) R.string.tutorial_done else R.string.tutorial_next)

        val card = cardView ?: return
        val widthSpec = View.MeasureSpec.makeMeasureSpec(card.layoutParams.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(widthSpec, heightSpec)
        val cardHeight = card.measuredHeight
        val cardWidth = card.layoutParams.width

        val margin = dp(16)
        val rootWidth = rootContent.width
        val rootHeight = rootContent.height

        val spaceBelow = rootHeight - bottom
        val spaceAbove = top
        val placeBelow = spaceBelow >= cardHeight + margin || spaceBelow >= spaceAbove

        val targetY = if (placeBelow) {
            bottom + margin
        } else {
            top - margin - cardHeight
        }.coerceIn(margin.toFloat(), (rootHeight - cardHeight - margin).coerceAtLeast(margin).toFloat())

        val targetX = ((rootWidth - cardWidth) / 2f).coerceIn(margin.toFloat(), (rootWidth - cardWidth - margin).coerceAtLeast(margin).toFloat())

        val params = card.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = targetX.toInt()
        params.topMargin = targetY.toInt()
        card.layoutParams = params
        card.visibility = View.VISIBLE
    }

    private fun finish() {
        val container = overlayContainer ?: return
        rootContent.removeView(container)
        overlayContainer = null
        spotlightView = null
        cardView = null
        stepCounterText = null
        titleText = null
        descriptionText = null
        nextButton = null
        val callback = onFinished
        onFinished = {}
        callback()
    }

    private class SpotlightView(context: Context) : View(context) {

        var highlightRect: RectF? = null
            set(value) {
                field = value
                invalidate()
            }

        private val scrimPaint = Paint().apply {
            color = Color.parseColor("#CC000000")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.settings_accent)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * resources.displayMetrics.density
        }
        private val cornerRadius = 12f * resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = highlightRect
            if (r == null) {
                canvas.drawRect(0f, 0f, w, h, scrimPaint)
                return
            }
            canvas.drawRect(0f, 0f, w, r.top, scrimPaint)
            canvas.drawRect(0f, r.bottom, w, h, scrimPaint)
            canvas.drawRect(0f, r.top, r.left, r.bottom, scrimPaint)
            canvas.drawRect(r.right, r.top, w, r.bottom, scrimPaint)
            canvas.drawRoundRect(r, cornerRadius, cornerRadius, borderPaint)
        }
    }
}
