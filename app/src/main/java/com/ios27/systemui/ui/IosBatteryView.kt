package com.ios27.systemui.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class IosBatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var batteryLevel: Int = 85
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var isCharging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isLowPowerMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var baseColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val bodyRect = RectF()
    private val fillRect = RectF()
    private val capRect = RectF()
    private val clipPath = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val width = (26.5f * density).toInt()
        val height = (12.5f * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val capWidth = 1.5f * density
        val capGap = 1.0f * density
        val bodyWidth = width - capWidth - capGap
        val bodyHeight = height.toFloat()
        val cornerRadius = 3.8f * density

        bodyRect.set(0f, 0f, bodyWidth, bodyHeight)

        bgPaint.color = baseColor
        bgPaint.alpha = 95
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, bgPaint)

        val capHeight = 4.2f * density
        val capTop = (bodyHeight - capHeight) / 2f
        capRect.set(bodyWidth + capGap, capTop, width.toFloat(), capTop + capHeight)
        bgPaint.alpha = 130
        canvas.drawRoundRect(capRect, capWidth, capWidth, bgPaint)

        val activeFillColor = when {
            isCharging -> Color.parseColor("#34C759")
            isLowPowerMode -> Color.parseColor("#FFCC00")
            batteryLevel <= 20 -> Color.parseColor("#FF3B30")
            else -> baseColor
        }

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(bodyRect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val fillWidth = bodyWidth * (batteryLevel / 100f)
        fillRect.set(0f, 0f, fillWidth, bodyHeight)
        fillPaint.color = activeFillColor
        canvas.drawRect(fillRect, fillPaint)
        canvas.restore()

        val isDarkBase = Color.luminance(baseColor) < 0.5f
        textPaint.color = when {
            isCharging || isLowPowerMode -> Color.BLACK
            batteryLevel <= 20 -> Color.WHITE
            batteryLevel >= 45 -> if (isDarkBase) Color.WHITE else Color.BLACK
            else -> baseColor
        }
        textPaint.textSize = 9.0f * density
        val textY = (bodyHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(batteryLevel.toString(), bodyWidth / 2f, textY, textPaint)
    }
}
