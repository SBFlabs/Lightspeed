package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context

/**
 * Facade delegating Sonic Deck lifecycle to [UnifiedSystemDeckHost].
 */
object SonicDeckManager {

    const val KEY_SONIC_VOICE_ISOLATION_STASHED = "pref_sonic_voice_isolation_stashed"

    fun isShowing(): Boolean = UnifiedSystemDeckHost.isShowingMode(SystemDeckMode.SONIC)

    fun updateWindowFocusable(focusable: Boolean) =
        UnifiedSystemDeckHost.updateWindowFocusable(focusable)

    fun onMasterToggle(context: Context, enabled: Boolean) =
        UnifiedSystemDeckHost.onSonicMasterToggle(context, enabled)

    fun show(service: AccessibilityService, fromContinuum: Boolean = false) =
        UnifiedSystemDeckHost.show(service, SystemDeckMode.SONIC, fromContinuum)

    fun dismiss(context: Context? = null) {
        if (isShowing()) {
            UnifiedSystemDeckHost.dismiss()
        }
    }

    fun dismiss() = dismiss(null)
}
