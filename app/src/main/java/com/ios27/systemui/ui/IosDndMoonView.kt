package com.ios27.systemui.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class IosDndMoonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val moonPath = Path()
    private val cutoutPath = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val size = (12.5f * density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = width / 2f
        moonPath.reset()
        cutoutPath.reset()

        moonPath.addCircle(r, r, r * 0.88f, Path.Direction.CW)
        cutoutPath.addCircle(r * 1.38f, r * 0.65f, r * 0.72f, Path.Direction.CW)
        moonPath.op(cutoutPath, Path.Op.DIFFERENCE)

        canvas.drawPath(moonPath, paint)
    }
}
