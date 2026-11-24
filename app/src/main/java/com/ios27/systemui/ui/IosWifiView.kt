package com.ios27.systemui.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class IosWifiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var wifiLevel: Int = 3
        set(value) {
            field = value.coerceIn(0, 3)
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcRect = RectF()
    private val dotPath = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val width = (16.5f * density).toInt()
        val height = (12f * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height - (1.8f * density)
        val strokeWidth = 2.05f * density
        strokePaint.strokeWidth = strokeWidth

        dotPaint.color = iconColor
        dotPaint.alpha = if (wifiLevel >= 1) 255 else 85
        val dotRadius = 1.9f * density
        dotPath.reset()
        dotPath.moveTo(cx, cy + (0.6f * density))
        dotPath.arcTo(
            RectF(cx - dotRadius, cy - dotRadius, cx + dotRadius, cy + dotRadius),
            -132f,
            84f,
            false
        )
        dotPath.close()
        canvas.drawPath(dotPath, dotPaint)

        val radii = floatArrayOf(5.2f * density, 8.8f * density)
        for (i in radii.indices) {
            val r = radii[i]
            strokePaint.color = iconColor
            strokePaint.alpha = if (wifiLevel >= i + 2) 255 else 85
            arcRect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arcRect, -133f, 86f, false, strokePaint)
        }
    }
}
