package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context

/**
 * Facade delegating Vista Deck lifecycle to [UnifiedSystemDeckHost].
 */
object VistaDeckManager {

    fun isShowing(): Boolean = UnifiedSystemDeckHost.isShowingMode(SystemDeckMode.VISTA)

    fun updateWindowFocusable(focusable: Boolean) =
        UnifiedSystemDeckHost.updateWindowFocusable(focusable)

    fun show(service: AccessibilityService, fromContinuum: Boolean = false) =
        UnifiedSystemDeckHost.show(service, SystemDeckMode.VISTA, fromContinuum)

    fun dismiss(context: Context? = null) {
        if (isShowing()) {
            UnifiedSystemDeckHost.dismiss()
        }
    }

    fun dismiss() = dismiss(null)
}
