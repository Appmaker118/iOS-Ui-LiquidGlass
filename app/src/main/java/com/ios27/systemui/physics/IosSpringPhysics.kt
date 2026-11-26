package com.ios27.systemui.physics

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.pow

object IosSpringPhysics {

    /**
     * Standard iOS bouncy spring (used for Control Center panels, Home Screen unlock bounce, modals)
     */
    fun springTo(
        view: View,
        property: DynamicAnimation.ViewProperty,
        targetValue: Float,
        dampingRatio: Float = 0.68f,
        stiffness: Float = 320f,
        onEnd: (() -> Unit)? = null
    ): SpringAnimation {
        return SpringAnimation(view, property, targetValue).apply {
            spring.dampingRatio = dampingRatio
            spring.stiffness = stiffness
            if (onEnd != null) {
                addEndListener { _, _, _, _ -> onEnd() }
            }
            start()
        }
    }

    /**
     * Attaches iOS tactile press-down shrink + spring-back bounce to any tile, button, or glass pill
     */
    fun attachBouncyTouch(
        view: View,
        pressedScale: Float = 0.94f,
        onClick: (() -> Unit)? = null
    ) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springTo(v, DynamicAnimation.SCALE_X, pressedScale, 0.75f, 500f)
                    springTo(v, DynamicAnimation.SCALE_Y, pressedScale, 0.75f, 500f)
                }
                MotionEvent.ACTION_UP -> {
                    springTo(v, DynamicAnimation.SCALE_X, 1f, 0.55f, 340f)
                    springTo(v, DynamicAnimation.SCALE_Y, 1f, 0.55f, 340f)
                    if (event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()) {
                        v.performClick()
                        onClick?.invoke()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    springTo(v, DynamicAnimation.SCALE_X, 1f, 0.60f, 350f)
                    springTo(v, DynamicAnimation.SCALE_Y, 1f, 0.60f, 350f)
                }
            }
            true
        }
    }

    /**
     * Authentic iOS rubber-band resistance formula when over-dragging sliders or scroll views
     */
    fun rubberBand(offset: Float, dimension: Float, coefficient: Float = 0.55f): Float {
        if (dimension <= 0f) return 0f
        val absOffset = abs(offset)
        val result = (1.0f - (1.0f / ((absOffset * coefficient / dimension) + 1.0f))) * dimension
        return if (offset < 0f) -result else result
    }

    /**
     * Home Screen icon edit-mode jiggle animation
     */
    fun startJiggle(view: View, phaseOffsetMs: Long = 0L): ObjectAnimator {
        val rot = PropertyValuesHolder.ofFloat(View.ROTATION, -2.1f, 2.1f)
        val transY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -1.2f, 1.2f)
        return ObjectAnimator.ofPropertyValuesHolder(view, rot, transY).apply {
            duration = 135L
            startDelay = phaseOffsetMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun stopJiggle(view: View, animator: ObjectAnimator?) {
        animator?.cancel()
        springTo(view, DynamicAnimation.ROTATION, 0f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, 400f)
        springTo(view, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, 400f)
    }
}
