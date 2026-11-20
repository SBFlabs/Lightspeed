package com.sbf.lightspeed.system

import com.sbf.lightspeed.R

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object DeviceFontScanner {

    private val androidTypefaceCache = ConcurrentHashMap<String, Typeface>()

    fun getInstalledFonts(): List<Pair<String, String>> {
        val list = mutableListOf(
            "system_default" to lsString(R.string.font_follow_device),
            "sans-serif-condensed" to lsString(R.string.font_tactical),
            "sans-serif" to lsString(R.string.font_sans),
            "sans-serif-medium" to lsString(R.string.font_sans_med),
            "sans-serif-black" to lsString(R.string.font_sans_black),
            "sans-serif-light" to lsString(R.string.font_sans_light),
            "monospace" to lsString(R.string.font_mono),
            "serif" to lsString(R.string.font_serif),
            "casual" to lsString(R.string.font_casual),
            "cursive" to lsString(R.string.font_cursive)
        )

        try {
            val fontDirs = listOf(File("/system/fonts"), File("/product/fonts"))
            val seenNames = mutableSetOf<String>()
            for (dir in fontDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { f ->
                        f.isFile && (f.name.endsWith(".ttf", ignoreCase = true) || f.name.endsWith(".otf", ignoreCase = true))
                    } ?: emptyArray()

                    for (file in files.sortedBy { it.name }) {
                        val name = file.nameWithoutExtension
                        if (name.startsWith("Noto", ignoreCase = true)) continue
                        if (name.startsWith("AndroidClock", ignoreCase = true)) continue
                        if (name.contains("Emoji", ignoreCase = true)) continue

                        val friendlyName = name
                            .replace("-Regular", "")
                            .replace("Regular", "")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .trim()

                        if (friendlyName.isNotBlank() && seenNames.add(friendlyName)) {
                            list.add(file.absolutePath to "🔤 $friendlyName")
                        }
                    }
                }
            }
        } catch (e: Exception) { logSwallowed("DeviceFontScanner", "getInstalledFonts:51", e) }

        return list
    }

    /** Returns installed device fonts with clean emoji-free labels for Central Command UI. */
    fun getCleanInstalledFonts(): List<Pair<String, String>> {
        return getInstalledFonts().map { (key, rawLabel) ->
            val stripped = rawLabel
                .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F]"), "")
                .trim()
            val label = when (key) {
                "system_default" -> lsString(R.string.font_sys_default)
                "sans-serif-condensed" -> lsString(R.string.font_tactical)
                else -> stripped.ifBlank { key }
            }
            key to label
        }
    }

    /** Looks up a single friendly label for a saved font key without loading all system fonts. */
    fun getFontLabel(fontKey: String): String = when (fontKey) {
        "", "system_default" -> lsString(R.string.font_sys_default)
        "sans-serif-condensed" -> lsString(R.string.font_tactical)
        "sans-serif" -> lsString(R.string.font_sans)
        "sans-serif-medium" -> lsString(R.string.font_sans_med)
        "sans-serif-black" -> lsString(R.string.font_sans_black)
        "sans-serif-light" -> lsString(R.string.font_sans_light)
        "monospace" -> lsString(R.string.font_mono)
        "serif" -> lsString(R.string.font_serif)
        "casual" -> lsString(R.string.font_casual)
        "cursive" -> lsString(R.string.font_cursive)
        else -> {
            val rawName = File(fontKey).nameWithoutExtension
                .replace("-Regular", "")
                .replace("Regular", "")
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .trim()
            rawName.ifBlank { fontKey }
        }
    }

    /** Scans and resolves device fonts strictly on demand off the UI thread (nothing cached globally). */
    suspend fun loadCleanResolvedFontsOnDemand(): List<Triple<String, String, FontFamily?>> =
        withContext(Dispatchers.IO) {
            getCleanInstalledFonts().map { (key, label) ->
                Triple(key, label, resolveComposeFontFamily(key))
            }
        }

    /** Resolves an installed Android [Typeface] for [fontKey] with optional bold styling. */
    fun resolveAndroidTypeface(fontKey: String, isBold: Boolean = false): Typeface? {
        if (fontKey.isBlank() || fontKey == "system_default") return null
        val cacheKey = "$fontKey|$isBold"
        return androidTypefaceCache.getOrPut(cacheKey) {
            try {
                val base = if (fontKey.startsWith("/")) {
                    Typeface.createFromFile(fontKey)
                } else {
                    Typeface.create(fontKey, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                }
                if (isBold) Typeface.create(base, Typeface.BOLD) else base
            } catch (_: Exception) {
                if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
    }

    /**
     * Resolves the global overlay/HUD [Typeface] when "Apply Font Across Overlays & HUDs" is enabled,
     * falling back to [fallback] otherwise.
     */
    fun resolveGlobalOverlayTypeface(
        context: Context,
        isBold: Boolean = false,
        fallback: Typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    ): Typeface {
        if (!LightspeedPreferences.getApplyFontGlobally(context)) return fallback
        val key = LightspeedPreferences.getCentralCommandFontFamily(context)
        return resolveAndroidTypeface(key, isBold) ?: fallback
    }

    /** Resolves a Compose [FontFamily] on demand without maintaining a global font cache. */
    fun resolveComposeFontFamily(fontKey: String): FontFamily? {
        if (fontKey.isBlank() || fontKey == "system_default") return null
        return try {
            val tf = if (fontKey.startsWith("/")) {
                Typeface.createFromFile(fontKey)
            } else {
                Typeface.create(fontKey, Typeface.NORMAL)
            }
            FontFamily(tf)
        } catch (e: Exception) {
            logSwallowed("DeviceFontScanner", "resolveComposeFontFamily:$fontKey", e)
            null
        }
    }
}
