package com.sbf.lightspeed.system.sonic

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.sbf.lightspeed.system.logSwallowed

/**
 * Attributions wrapper and ContextImpl field installer impersonating UID 2000 (`com.android.shell`).
 * Adapted from android-realtime-voice-isolation (MIT License).
 * Allows privileged AudioRecord loopback capture and AudioTrack output without SecurityException on Android 12+.
 */
internal class ShellAttributionContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = "com.android.shell"
    override fun getOpPackageName(): String = "com.android.shell"

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource {
        return buildShellAttributionSource()
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        fun buildShellAttributionSource(): AttributionSource {
            return AttributionSource.Builder(Process.myUid())
                .setPackageName("com.android.shell")
                .build()
        }

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun installShellAttribution(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            try {
                val shellAttr = buildShellAttributionSource()
                patchContextAttribution(context, shellAttr)
                val atClass = Class.forName("android.app.ActivityThread")
                val curApp = atClass.getMethod("currentApplication").invoke(null) as? Context
                if (curApp != null) {
                    patchContextAttribution(curApp, shellAttr)
                }
            } catch (e: Exception) {
                logSwallowed("ShellAttributionContext", "installShellAttribution", e)
            }
        }

        private fun patchContextAttribution(ctx: Context, attr: AttributionSource) {
            var current: Context? = ctx
            var depth = 0
            while (current != null && depth < 10) {
                var cls: Class<*>? = current.javaClass
                while (cls != null && cls != Any::class.java) {
                    try {
                        val field = cls.getDeclaredField("mAttributionSource")
                        field.isAccessible = true
                        field.set(current, attr)
                    } catch (_: NoSuchFieldException) {
                    } catch (_: Exception) {}
                    cls = cls.superclass
                }
                current = if (current is ContextWrapper) current.baseContext else null
                depth++
            }
        }
    }
}
