package com.sbf.lightspeed.system

import android.app.Activity
import com.sbf.lightspeed.overrideZeroTransition as rootOverrideZeroTransition

/**
 * Forwards to root overrideZeroTransition() for activities in com.sbf.lightspeed.system.
 */
fun Activity.overrideZeroTransition() {
    rootOverrideZeroTransition()
}
