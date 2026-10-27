package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import com.sbf.lightspeed.DeepSpaceGearGeometry

/**
 * High-performance in-memory cache for Cockpit Gimbal & Reticle configuration.
 * Prevents defaultPrefs() string lookups on every 120Hz frame of Cockpit Hangar rendering.
 */
object CockpitGimbalConfigCache {

    @Volatile private var isRegistered = false
    @Volatile private var cachedReticleStyle: String? = null
    @Volatile private var cachedReticleOrientation: String? = null
    @Volatile private var cachedGearShape: String? = null
    @Volatile private var cachedDpiIndependent: Boolean? = null
    @Volatile private var cachedCustomDiameterScale: Float? = null
    @Volatile private var cachedUnitScale: Float? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null ||
            key == LightspeedPreferences.KEY_GEAR_RETICLE_STYLE ||
            key == LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION ||
            key == LightspeedPreferences.KEY_GEAR_SHAPE ||
            key == LightspeedPreferences.KEY_GEAR_DPI_INDEPENDENT ||
            key == LightspeedPreferences.KEY_GEAR_CUSTOM_DIAMETER_SCALE
        ) {
            flush()
        }
    }

    private fun ensureListener(context: Context) {
        if (!isRegistered) {
            synchronized(this) {
                if (!isRegistered) {
                    val appContext = context.applicationContext ?: context
                    appContext.defaultPrefs().registerOnSharedPreferenceChangeListener(prefListener)
                    isRegistered = true
                }
            }
        }
    }

    fun flush() {
        cachedReticleStyle = null
        cachedReticleOrientation = null
        cachedGearShape = null
        cachedDpiIndependent = null
        cachedCustomDiameterScale = null
        cachedUnitScale = null
    }

    fun getReticleStyle(context: Context): String {
        cachedReticleStyle?.let { return it }
        ensureListener(context)
        val v = context.defaultPrefs().getString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "tactical") ?: "tactical"
        cachedReticleStyle = v
        return v
    }

    fun getReticleOrientation(context: Context): String {
        cachedReticleOrientation?.let { return it }
        ensureListener(context)
        val v = context.defaultPrefs().getString(
            LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION,
            LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
        ) ?: LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
        cachedReticleOrientation = v
        return v
    }

    fun getGearShape(context: Context): String {
        cachedGearShape?.let { return it }
        ensureListener(context)
        val v = CockpitGearRepository.getGearShape(context)
        cachedGearShape = v
        return v
    }

    fun isDpiIndependent(context: Context): Boolean {
        cachedDpiIndependent?.let { return it }
        ensureListener(context)
        val v = CockpitGearRepository.isGearDpiIndependent(context)
        cachedDpiIndependent = v
        return v
    }

    fun getCustomDiameterScale(context: Context): Float {
        cachedCustomDiameterScale?.let { return it }
        ensureListener(context)
        val v = CockpitGearRepository.getGearCustomDiameterScale(context)
        cachedCustomDiameterScale = v
        return v
    }

    fun getUnitScale(context: Context): Float {
        cachedUnitScale?.let { return it }
        val v = DeepSpaceGearGeometry.computeGearUnitScale(context)
        cachedUnitScale = v
        return v
    }
}
