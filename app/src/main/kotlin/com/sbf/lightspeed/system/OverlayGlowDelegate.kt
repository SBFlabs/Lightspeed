package com.sbf.lightspeed.system

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Encapsulates tactical perimeter glow animation state, value animator lifecycle,
 * and invalidate dispatch across overlay windows.
 */
class OverlayGlowDelegate(
    private val hostView: View,
    private val onInvalidate: () -> Unit = { hostView.invalidate() }
) {
    var glowFraction: Float = 0f
        internal set

    private var glowAnimator: ValueAnimator? = null

    fun triggerGlow(context: Context, durationMs: Long = -1L) {
        hostView.post {
            val effectiveDuration = if (durationMs > 0L) durationMs else LightspeedPreferences.getDeflectorGlowDurationMs(context)
            glowAnimator?.cancel()
            glowAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = effectiveDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    glowFraction = anim.animatedValue as Float
                    onInvalidate()
                }
                start()
            }
        }
    }

    fun cancel() {
        glowAnimator?.cancel()
        glowFraction = 0f
    }
}
