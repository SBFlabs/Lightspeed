package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface

object LightspeedOrientationManager {
    const val ACTION_TOGGLE_ROTATION = "system:orientation_toggle"
    const val ACTION_FORCE_PORTRAIT = "system:orientation_portrait"
    const val ACTION_FORCE_SENSOR_360 = "system:orientation_sensor_360"
    const val ACTION_SENSOR_PORTRAIT = "system:orientation_sensor_portrait"

    fun canWriteSettings(context: Context): Boolean = LightspeedOrientationEngine.canWriteSettings(context)
    fun getAccelerometerRotation(context: Context): Int = if (LightspeedOrientationEngine.isAutoRotateEnabled(context)) 1 else 0
    fun getUserRotation(context: Context): Int = LightspeedOrientationEngine.getUserRotation(context)
    fun toggleRotation(context: Context) { LightspeedOrientationEngine.toggleAutoRotate(context) }
    fun forcePortrait(context: Context) { LightspeedOrientationEngine.forcePortrait(context) }
    fun forceSensor360(context: Context) { LightspeedOrientationEngine.forceSensor360(context) }
    fun setSensorPortrait(context: Context) { LightspeedOrientationEngine.setSensorPortrait(context) }
    fun evaluateContextGuardrails(context: Context, isLocked: Boolean, currentPackage: String?) {
        LightspeedOrientationEngine.evaluateContextAndAppRules(context, isLocked, currentPackage)
    }
}
