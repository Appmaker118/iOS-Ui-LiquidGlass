package com.ios27.systemui.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class IosSignalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var signalLevel: Int = 4
        set(value) {
            field = value.coerceIn(0, 4)
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val width = (18f * density).toInt()
        val height = (11.5f * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barWidth = 3.1f * density
        val gap = 1.6f * density
        val cornerRadius = barWidth / 2f

        val heightRatios = floatArrayOf(0.38f, 0.56f, 0.78f, 1.0f)

        for (i in 0 until 4) {
            val left = i * (barWidth + gap)
            val barHeight = height * heightRatios[i]
            val top = height - barHeight

            paint.color = iconColor
            paint.alpha = if (i < signalLevel) 255 else 85

            rect.set(left, top, left + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}
