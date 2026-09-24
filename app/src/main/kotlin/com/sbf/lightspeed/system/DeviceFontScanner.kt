package com.sbf.lightspeed.system

import java.io.File

object DeviceFontScanner {
    private var cachedList: List<Pair<String, String>>? = null

    fun getInstalledFonts(): List<Pair<String, String>> {
        cachedList?.let { return it }

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
        } catch (_: Exception) {}

        cachedList = list
        return list
    }
}
