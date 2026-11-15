package com.sbf.lightspeed.system

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DeviceFontScanner {

    fun getInstalledFonts(): List<Pair<String, String>> {
        val list = mutableListOf(
            "system_default" to "📱 Follow Device (System Default)",
            "sans-serif-condensed" to "✈️ Tactical Condensed (Cockpit HUD)",
            "sans-serif" to "Standard Sans-Serif",
            "sans-serif-medium" to "Sans-Serif Medium",
            "sans-serif-black" to "Sans-Serif Black (Heavy)",
            "sans-serif-light" to "Sans-Serif Light",
            "monospace" to "📟 Monospace Terminal",
            "serif" to "📰 Serif (Editorial)",
            "casual" to "✍️ Casual Hand",
            "cursive" to "🖋️ Cursive Script"
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
                "system_default" -> "System Default"
                "sans-serif-condensed" -> "Tactical Condensed (Cockpit HUD)"
                else -> stripped.ifBlank { key }
            }
            key to label
        }
    }

    /** Looks up a single friendly label for a saved font key without loading all system fonts. */
    fun getFontLabel(fontKey: String): String = when (fontKey) {
        "", "system_default" -> "System Default"
        "sans-serif-condensed" -> "Tactical Condensed (Cockpit HUD)"
        "sans-serif" -> "Standard Sans-Serif"
        "sans-serif-medium" -> "Sans-Serif Medium"
        "sans-serif-black" -> "Sans-Serif Black (Heavy)"
        "sans-serif-light" -> "Sans-Serif Light"
        "monospace" -> "Monospace Terminal"
        "serif" -> "Serif (Editorial)"
        "casual" -> "Casual Hand"
        "cursive" -> "Cursive Script"
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
