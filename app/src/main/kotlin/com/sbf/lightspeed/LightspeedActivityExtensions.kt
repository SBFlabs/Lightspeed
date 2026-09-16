package com.sbf.lightspeed

import android.app.Activity
import android.os.Build

/**
 * Modernizes activity transitions across Android versions.
 * Employs zero-duration activity transitions on Android 14+ (API 34/35)
 * and safely falls back to overridePendingTransition(0, 0) on API 26-33.
 */
fun Activity.overrideZeroTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
