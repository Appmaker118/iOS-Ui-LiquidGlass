package com.ios27.systemui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout

class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var cornerRadiusDp: Float = 26f
        set(value) {
            field = value
            invalidate()
        }

    var glassTintColor: Int = Color.parseColor("#26FFFFFF")
        set(value) {
            field = value
            tintPaint.color = value
            invalidate()
        }

    var blurRadius: Int = 12
    var refractionMagnification: Float = 1.06f // Convex water-lens magnification

    private var backdropSourceView: View? = null
    private var blurredBackdrop: Bitmap? = null
    private var sampleCanvas: Canvas? = null
    private var rawSampleBitmap: Bitmap? = null
    private var isCapturing = false

    private val clipPath = Path()
    private val boundsRect = RectF()
    private val rimRect = RectF()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val chromaticPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 55
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = glassTintColor
    }
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (backdropSourceView != null && visibility == View.VISIBLE && width > 0 && height > 0) {
            captureAndRefractBackdrop()
        }
        true
    }

    init {
        setWillNotDraw(false)
    }

    /**
     * Attach the underlying view (Wallpaper, Home Screen, or scrolling App Library)
     * so LiquidGlassView refracts its live colors as items pass underneath.
     */
    fun bindBackdrop(sourceView: View) {
        backdropSourceView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        backdropSourceView = sourceView
        sourceView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        backdropSourceView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        super.onDetachedFromWindow()
    }

    private fun captureAndRefractBackdrop() {
        val source = backdropSourceView ?: return
        if (isCapturing || width <= 0 || height <= 0) return
        isCapturing = true

        try {
            val scale = 0.14f // Downscale ~7x for locked 60fps on Galaxy A21s
            val sampleW = (width * scale).toInt().coerceAtLeast(8)
            val sampleH = (height * scale).toInt().coerceAtLeast(8)

            if (rawSampleBitmap == null || rawSampleBitmap?.width != sampleW || rawSampleBitmap?.height != sampleH) {
                rawSampleBitmap = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                sampleCanvas = Canvas(rawSampleBitmap!!)
            }

            val locSelf = IntArray(2)
            val locSource = IntArray(2)
            getLocationOnScreen(locSelf)
            source.getLocationOnScreen(locSource)

            val offsetX = (locSelf[0] - locSource[0]).toFloat()
            val offsetY = (locSelf[1] - locSource[1]).toFloat()

            val c = sampleCanvas ?: return
            c.save()
            c.drawColor(Color.BLACK)
            c.scale(scale, scale)
            c.translate(-offsetX, -offsetY)
            source.draw(c)
            c.restore()

            blurredBackdrop = FastBlurEngine.blur(rawSampleBitmap!!, blurRadius)
        } catch (_: Exception) {
        } finally {
            isCapturing = false
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        boundsRect.set(0f, 0f, width.toFloat(), height.toFloat())

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(boundsRect, radiusPx, radiusPx, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 1. Draw refracted live backdrop (convex water-lens zoom + chromatic edge dispersion)
        val bmp = blurredBackdrop
        if (bmp != null && !bmp.isRecycled) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            val insetX = width * (refractionMagnification - 1f) * 0.5f
            val insetY = height * (refractionMagnification - 1f) * 0.5f
            dstRect.set(-insetX, -insetY, width + insetX, height + insetY)
            canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)

            // Subtle chromatic light dispersion shift along glass curve
            val shift = 3.5f * density
            dstRect.set(-insetX - shift, -insetY, width + insetX - shift, height + insetY)
            canvas.drawBitmap(bmp, srcRect, dstRect, chromaticPaint)
        }

        // 2. Translucent liquid glass body tint
        canvas.drawRect(boundsRect, tintPaint)

        // 3. Top-to-bottom internal caustic light reflection
        innerGlowPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.parseColor("#42FFFFFF"),
                Color.parseColor("#0DFFFFFF"),
                Color.parseColor("#1FFFFFFF")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(boundsRect, innerGlowPaint)

        // 4. Draw children (icons, text, clock, search input) inside the glass
        super.dispatchDraw(canvas)

        // 5. Crisp Liquid Glass Specular Rim (bright Fresnel light edge)
        val strokeWidth = 1.25f * density
        rimPaint.strokeWidth = strokeWidth
        val halfStroke = strokeWidth / 2f
        rimRect.set(halfStroke, halfStroke, width - halfStroke, height - halfStroke)
        rimPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#CCFFFFFF"), // Bright specular highlight top-left
                Color.parseColor("#33FFFFFF"), // Translucent glass middle
                Color.parseColor("#88FFFFFF")  // Secondary reflection bottom-right
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rimRect, radiusPx, radiusPx, rimPaint)
        canvas.restore()
    }
}
