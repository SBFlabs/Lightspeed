package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sbf.lightspeed.system.SpacePodGestureDispatcher
import com.sbf.lightspeed.widget.LightspeedWidgetConfigActivity

/**
 * Zero-latency transparent trampoline Activity catching Home Screen Shortcut taps.
 * Invokes SpacePodGestureDispatcher to evaluate CLICK vs DOUBLE_CLICK only.
 * Home Screen Shortcuts never intercept long-press (reserved for Android Launcher).
 * If the pod is unconfigured, automatically opens LightspeedWidgetConfigActivity.
 */
class SpacePodShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val podIndex = intent?.getIntExtra(EXTRA_POD_INDEX, 1)?.coerceIn(1, 4) ?: 1

        if (!SpacePodGestureDispatcher.isPodConfigured(this, podIndex)) {
            val configIntent = Intent(this, LightspeedWidgetConfigActivity::class.java).apply {
                putExtra(LightspeedWidgetConfigActivity.EXTRA_POD_INDEX, podIndex)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(configIntent)
        } else {
            SpacePodGestureDispatcher.onPodTapped(this, podIndex)
        }
        finish()
    }

    companion object {
        const val EXTRA_POD_INDEX = "extra_pod_index"
    }
}
