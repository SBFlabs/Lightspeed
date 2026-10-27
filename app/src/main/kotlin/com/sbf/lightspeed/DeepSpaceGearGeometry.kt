package com.sbf.lightspeed

import android.content.Context
import android.graphics.Path
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedPreferences
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deep Space Gear Geometry Engine.
 * Provides physical DPI-severed unit scaling and parametric perimeter calculations
 * across multiple geometrical archetypes (Circle, Square, Squircle superellipse, and Star).
 */
object DeepSpaceGearGeometry {

    /**
     * Computes the base unit scale factor for the gimbal assembly.
     * When [LightspeedPreferences.KEY_GEAR_DPI_INDEPENDENT] is enabled, this scale is derived
     * strictly from physical hardware DPI (xdpi/ydpi) or screen dimension fractions,
     * immunizing the gimbal rings against software Android densityDpi / smallest width overrides.
     */
    fun computeGearUnitScale(context: Context): Float {
        val metrics = context.resources.displayMetrics
        val dpiIndependent = CockpitGearRepository.isGearDpiIndependent(context)
        if (!dpiIndependent) {
            return metrics.density
        }

        // Hardware physical dots per inch (independent of software display scaling)
        val physicalDpi = if (metrics.xdpi > 80f && metrics.ydpi > 80f) {
            (metrics.xdpi + metrics.ydpi) / 2f
        } else {
            val minDimensionPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
            // Standard baseline: 392.7dp width for 1080px display
            (minDimensionPx / 392.7f) * 160f
        }

        // 160 physical dpi = 1.0 unit scale
        return (physicalDpi / 160f).coerceIn(1.5f, 5.0f)
    }

    /**
     * Resolves the orbital center point for an app slot along the ring.
     * All sculpted Cockpit mockup themes (Gear Wheel, Hex Ring, Petal Menu, Floating Blades,
     * Orbital Rings, Diamond, Crescent, Tech Disc) orbit app icons smoothly on a true
     * circular track centered within each sculpted radial segment.
     */
    fun pointOnGearPerimeter(
        cx: Float,
        cy: Float,
        radius: Float,
        angleRad: Double,
        rotationRad: Double,
        @Suppress("UNUSED_PARAMETER") shape: String
    ): Pair<Float, Float> {
        val totalAngle = angleRad + rotationRad
        val px = cx + radius * cos(totalAngle).toFloat()
        val py = cy + radius * sin(totalAngle).toFloat()
        return Pair(px, py)
    }

    /**
     * Constructs a closed geometric path for the central hub matching the specified mockup style.
     * Strictly secular, non-religious industrial/sci-fi geometry.
     * Rewinds [outPath] before populating so no allocations occur in onDraw().
     */
    fun buildGearRingPath(
        cx: Float,
        cy: Float,
        radius: Float,
        rotationDeg: Float,
        shape: String,
        outPath: Path
    ) {
        outPath.rewind()
        val rotRad = Math.toRadians(rotationDeg.toDouble())
        when (shape.lowercase()) {
            LightspeedPreferences.GEAR_SHAPE_SQUARE -> {
                // #2 Hex Ring: 6-sided regular flat-edged hexagon (industrial armor hub)
                val sides = 6
                val step = (2.0 * Math.PI) / sides
                for (i in 0 until sides) {
                    val a = i * step + rotRad
                    val x = cx + radius * cos(a).toFloat()
                    val y = cy + radius * sin(a).toFloat()
                    if (i == 0) outPath.moveTo(x, y) else outPath.lineTo(x, y)
                }
                outPath.close()
            }
            LightspeedPreferences.GEAR_SHAPE_DIAMOND -> {
                // #6 Diamond: 4-point tactical rhombus/diamond hub
                val angles = doubleArrayOf(0.0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0)
                val rPeak = radius * 1.08f
                for (i in angles.indices) {
                    val a = angles[i] + rotRad
                    val x = cx + rPeak * cos(a).toFloat()
                    val y = cy + rPeak * sin(a).toFloat()
                    if (i == 0) outPath.moveTo(x, y) else outPath.lineTo(x, y)
                }
                outPath.close()
            }
            LightspeedPreferences.GEAR_SHAPE_TECH,
            LightspeedPreferences.GEAR_SHAPE_STAR -> {
                // #9 Floating Blades / #14 Tech Disc: 8-sided chamfered octagon reactor core
                val sides = 8
                val step = (2.0 * Math.PI) / sides
                for (i in 0 until sides) {
                    val a = i * step + (step * 0.5) + rotRad
                    val x = cx + radius * cos(a).toFloat()
                    val y = cy + radius * sin(a).toFloat()
                    if (i == 0) outPath.moveTo(x, y) else outPath.lineTo(x, y)
                }
                outPath.close()
            }
            LightspeedPreferences.GEAR_SHAPE_SQUIRCLE -> {
                // #3 Petal Menu: Soft superellipse (n=4) core
                val steps = 64
                val stepRad = (2.0 * Math.PI) / steps
                for (i in 0 until steps) {
                    val a = i * stepRad
                    val cosVal = cos(a)
                    val sinVal = sin(a)
                    val denom = (cosVal * cosVal * cosVal * cosVal + sinVal * sinVal * sinVal * sinVal).pow(0.25)
                    val r = if (denom > 0.0001) (radius / denom).toFloat() * 0.94f else radius
                    val px = cx + r * cos(a + rotRad).toFloat()
                    val py = cy + r * sin(a + rotRad).toFloat()
                    if (i == 0) outPath.moveTo(px, py) else outPath.lineTo(px, py)
                }
                outPath.close()
            }
            else -> {
                outPath.addCircle(cx, cy, radius, Path.Direction.CW)
            }
        }
    }
}
