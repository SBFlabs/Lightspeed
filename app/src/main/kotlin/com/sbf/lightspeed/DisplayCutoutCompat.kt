package com.sbf.lightspeed

import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import androidx.annotation.RequiresApi

/**
 * Returns the top display-cutout rect. `getBoundingRectTop` only exists on API 29+, so API 28
 * falls back to scanning `getBoundingRects` for the rect touching the top edge.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal fun DisplayCutout.topCutoutRect(): Rect? {
    val direct: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) boundingRectTop else null
    return direct ?: boundingRects.firstOrNull { it.top == 0 }
}
