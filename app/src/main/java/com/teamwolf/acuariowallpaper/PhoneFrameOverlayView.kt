package com.teamwolf.acuariowallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Drawn on top of the preview SurfaceView: masks its corners with the card color (a SurfaceView
 * cannot be clipped to rounded corners), adds a thin bezel and a small camera notch, so the preview
 * reads as a phone. Ported from InfiniteRetro.
 */
class PhoneFrameOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val radius = 18f * density
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.settings_card_bg)
        style = Paint.Style.FILL
    }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.settings_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
        style = Paint.Style.FILL
    }
    private val mask = Path()
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        mask.reset()
        mask.fillType = Path.FillType.EVEN_ODD
        mask.addRect(rect, Path.Direction.CW)
        mask.addRoundRect(rect, radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(mask, maskPaint)
        val inset = bezelPaint.strokeWidth / 2f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, bezelPaint)
        canvas.drawCircle(width / 2f, 10f * density, 3f * density, notchPaint)
    }
}
