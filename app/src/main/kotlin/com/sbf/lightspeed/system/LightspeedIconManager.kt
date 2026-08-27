package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false
)

object LightspeedIconManager {
    private const val TAG = "LightspeedIconManager"
    private const val PREF_ICON_PACK = "pref_active_icon_pack"

    private val componentToDrawableMap = ConcurrentHashMap<String, String>()
    private val packageToDrawableMap = ConcurrentHashMap<String, String>()
    private val calendarPrefixMap = ConcurrentHashMap<String, String>()

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val drawableCache = ConcurrentHashMap<String, Drawable>()

    private var currentLoadedPack: String? = null
    private var isPackLoaded = false

    private val GOOGLE_CALENDAR_PACKAGES = setOf(
        "com.google.android.calendar",
        "com.google.android.apps.calendar"
    )

    fun getAvailableIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val list = mutableListOf<IconPackInfo>()
        list.add(IconPackInfo("system", "System Default (Adaptive / Dynamic)", isSystem = true))

        val intentFilters = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.fede.launcher.THEME_ICONPACK"),
            Intent("com.teslacoilsw.launcher.THEME"),
            Intent("com.anddoes.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme")
        )

        val seen = mutableSetOf<String>()
        intentFilters.forEach { intent ->
            try {
                val resolved = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                resolved.forEach { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (seen.add(pkg)) {
                        val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                        list.add(IconPackInfo(pkg, label))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying icon packs", e)
            }
        }
        return list
    }

    fun getActiveIconPack(context: Context): String {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        return prefs.getString(PREF_ICON_PACK, "system") ?: "system"
    }

    fun setActiveIconPack(context: Context, iconPackPkg: String) {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_ICON_PACK, iconPackPkg).apply()
        clearCache()
        loadIconPackAsync(context, iconPackPkg)
    }

    fun clearCache() {
        bitmapCache.clear()
        drawableCache.clear()
        customShortcutBitmaps.clear()
        componentToDrawableMap.clear()
        packageToDrawableMap.clear()
        calendarPrefixMap.clear()
        isPackLoaded = false
        currentLoadedPack = null
    }

    fun ensureLoaded(context: Context) {
        val activePack = getActiveIconPack(context)
        if (activePack != currentLoadedPack || !isPackLoaded) {
            loadIconPackSync(context, activePack)
        }
    }

    fun loadIconPackAsync(context: Context, iconPackPkg: String, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            loadIconPackSync(context, iconPackPkg)
            onComplete?.invoke()
        }
    }

    @Synchronized
    private fun loadIconPackSync(context: Context, iconPackPkg: String) {
        currentLoadedPack = iconPackPkg
        componentToDrawableMap.clear()
        packageToDrawableMap.clear()
        calendarPrefixMap.clear()

        if (iconPackPkg == "system" || iconPackPkg.isBlank()) {
            isPackLoaded = true
            return
        }

        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPkg)

            var inputStream: InputStream? = null
            try {
                val appfilterResId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPkg)
                if (appfilterResId != 0) {
                    val xmlParser = iconPackRes.getXml(appfilterResId)
                    parseXmlStream(xmlParser)
                    isPackLoaded = true
                    return
                }
            } catch (_: Exception) {}

            try {
                inputStream = iconPackRes.assets.open("appfilter.xml")
            } catch (_: Exception) {
                try {
                    inputStream = iconPackRes.assets.open("appfilter_full.xml")
                } catch (_: Exception) {}
            }

            if (inputStream != null) {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")
                parseXmlStream(parser)
                inputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon pack: $iconPackPkg", e)
        }
        isPackLoaded = true
    }

    private fun parseXmlStream(parser: XmlPullParser) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName.equals("item", ignoreCase = true)) {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                        val cleanComp = component.removePrefix("ComponentInfo{").removeSuffix("}")
                        componentToDrawableMap[cleanComp] = drawable
                        val pkg = cleanComp.substringBefore("/")
                        packageToDrawableMap[pkg] = drawable
                    }
                } else if (tagName.equals("calendar", ignoreCase = true)) {
                    val component = parser.getAttributeValue(null, "component")
                    val prefix = parser.getAttributeValue(null, "prefix")
                    if (!component.isNullOrBlank() && !prefix.isNullOrBlank()) {
                        val cleanComp = component.removePrefix("ComponentInfo{").removeSuffix("}")
                        calendarPrefixMap[cleanComp] = prefix
                        val pkg = cleanComp.substringBefore("/")
                        calendarPrefixMap[pkg] = prefix
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private val customShortcutBitmaps = ConcurrentHashMap<String, Bitmap>()

    /** Saves a manually-chosen icon bitmap for any token (shortcut:, custom:, or app:packageName).
     *  Writes to disk under shortcut_icons/ and records the token in the "manual_icon_overrides"
     *  SharedPreferences set so backups know to include it. */
    fun saveCustomShortcutBitmap(context: Context, token: String, bitmap: Bitmap) {
        if (token.isBlank()) return
        customShortcutBitmaps[token] = bitmap
        bitmapCache[token] = bitmap
        drawableCache[token] = BitmapDrawable(context.resources, bitmap)

        // Mark as manually overridden for backup tracking
        markAsManuallyOverridden(context, token)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = java.io.File(context.filesDir, "shortcut_icons")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "${token.hashCode()}.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error saving shortcut bitmap to disk", e)
            }
        }
    }

    /** Records a token as manually icon-overridden in SharedPreferences. */
    fun markAsManuallyOverridden(context: Context, token: String) {
        val prefs = context.getSharedPreferences("lightspeed_prefs", android.content.Context.MODE_PRIVATE)
        val current = prefs.getStringSet("manual_icon_overrides", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(token)
        prefs.edit().putStringSet("manual_icon_overrides", current).apply()
    }

    /** Returns all tokens that the user has manually overridden icons for. */
    fun getManuallyOverriddenTokens(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("lightspeed_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getStringSet("manual_icon_overrides", emptySet()) ?: emptySet()
    }

    /** Removes a token from the manually overridden set (e.g. when the user resets to default). */
    fun clearManualOverride(context: Context, token: String) {
        val prefs = context.getSharedPreferences("lightspeed_prefs", android.content.Context.MODE_PRIVATE)
        val current = prefs.getStringSet("manual_icon_overrides", emptySet())?.toMutableSet() ?: return
        current.remove(token)
        prefs.edit().putStringSet("manual_icon_overrides", current).apply()
        customShortcutBitmaps.remove(token)
        bitmapCache.remove(token)
        drawableCache.remove(token)
        try {
            java.io.File(context.filesDir, "shortcut_icons/${token.hashCode()}.png").delete()
        } catch (_: Exception) {}
    }

    private fun loadCustomShortcutBitmap(context: Context, token: String): Bitmap? {
        // Load for shortcut:/custom: tokens, AND for any app: token that was manually overridden
        val isShortcut = token.startsWith("shortcut:") || token.startsWith("custom:")
        val isManualOverride = !isShortcut && getManuallyOverriddenTokens(context).contains(token)
        if (!isShortcut && !isManualOverride) return null

        customShortcutBitmaps[token]?.let {
            if (!LightspeedShortcutManager.isCorruptBitmap(it)) return it
            customShortcutBitmaps.remove(token)
        }
        try {
            val file = java.io.File(context.filesDir, "shortcut_icons/${token.hashCode()}.png")
            if (file.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    if (LightspeedShortcutManager.isCorruptBitmap(bmp)) {
                        file.delete()
                        return null
                    }
                    customShortcutBitmaps[token] = bmp
                    return bmp
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getIconDrawable(context: Context, tokenOrPkg: String): Drawable? {
        if (tokenOrPkg.isBlank()) return null
        drawableCache[tokenOrPkg]?.let {
            return it.constantState?.newDrawable()?.mutate() ?: it
        }

        // Custom shortcut bitmaps (shortcut:, custom:) and manually overridden app: icons
        loadCustomShortcutBitmap(context, tokenOrPkg)?.let { bmp ->
            val d = BitmapDrawable(context.resources, bmp)
            drawableCache[tokenOrPkg] = d
            return d
        }

        if (tokenOrPkg.startsWith("shortcut:") || tokenOrPkg.startsWith("custom:")) {
            val shortcutDrawable = LightspeedShortcutManager.resolveIconDrawable(context, tokenOrPkg)
            if (shortcutDrawable != null) {
                val mutated = shortcutDrawable.constantState?.newDrawable()?.mutate() ?: shortcutDrawable.mutate()
                mutated.alpha = 255
                drawableCache[tokenOrPkg] = mutated
                return mutated.constantState?.newDrawable()?.mutate() ?: mutated
            }
        }

        ensureLoaded(context)
        val activePack = getActiveIconPack(context)
        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val dayStr = dayOfMonth.toString().padStart(2, '0')

        val extractedPkg = when {
            tokenOrPkg.startsWith("app:") -> tokenOrPkg.removePrefix("app:")
            else -> tokenOrPkg
        }

        if (extractedPkg.isBlank()) return null

        // 1. Third-Party Icon Pack Resolution (e.g. Arcticons)
        if (activePack != "system" && isPackLoaded) {
            try {
                val pm = context.packageManager
                val iconPackRes = pm.getResourcesForApplication(activePack)

                // Check dynamic calendar rule in icon pack
                val calPrefix = calendarPrefixMap[extractedPkg]
                if (calPrefix != null) {
                    val calCandidates = listOf("${calPrefix}${dayOfMonth}", "${calPrefix}${dayStr}")
                    for (cand in calCandidates) {
                        val calResId = iconPackRes.getIdentifier(cand, "drawable", activePack)
                        if (calResId != 0) {
                            val calDrawable = iconPackRes.getDrawable(calResId, null)
                            if (calDrawable != null) {
                                val mutated = calDrawable.constantState?.newDrawable()?.mutate() ?: calDrawable.mutate()
                                mutated.alpha = 255
                                drawableCache[tokenOrPkg] = mutated
                                return mutated.constantState?.newDrawable()?.mutate() ?: mutated
                            }
                        }
                    }
                }

                // Check standard app/component mapping in icon pack
                val drawableName = packageToDrawableMap[extractedPkg]
                if (!drawableName.isNullOrBlank()) {
                    val resId = iconPackRes.getIdentifier(drawableName, "drawable", activePack)
                    if (resId != 0) {
                        val packDrawable = iconPackRes.getDrawable(resId, null)
                        if (packDrawable != null) {
                            val mutated = packDrawable.constantState?.newDrawable()?.mutate() ?: packDrawable.mutate()
                            mutated.alpha = 255
                            drawableCache[tokenOrPkg] = mutated
                            return mutated.constantState?.newDrawable()?.mutate() ?: mutated
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving icon from pack: $activePack", e)
            }
        }

        // 2. Native System/OEM App Icon Resolution
        // Uses resolveInfo.loadIcon(pm) which correctly handles: active <activity-alias>,
        // density selection, and the app's own package-manager theming — without any forced theme injection.
        return try {
            val pm = context.packageManager
            var rawDrawable: Drawable? = null

            // A. Primary: queryIntentActivities resolves active <activity-alias> (e.g. Plus Messenger NoxIcon)
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(extractedPkg)
                }
                val resolveInfo = pm.queryIntentActivities(intent, 0).firstOrNull()
                if (resolveInfo != null) {
                    rawDrawable = resolveInfo.loadIcon(pm)
                }
            } catch (_: Exception) {}

            // B. Secondary: getLaunchIntentForPackage (covers apps with no CATEGORY_LAUNCHER alias)
            if (rawDrawable == null) {
                val launchIntent = pm.getLaunchIntentForPackage(extractedPkg)
                if (launchIntent?.component != null) {
                    try {
                        rawDrawable = pm.getActivityIcon(launchIntent.component!!)
                    } catch (_: Exception) {}
                }
            }

            val finalDrawable = rawDrawable ?: pm.getApplicationIcon(extractedPkg)
            val mutated = finalDrawable.constantState?.newDrawable()?.mutate() ?: finalDrawable.mutate()
            mutated.alpha = 255
            drawableCache[tokenOrPkg] = mutated
            mutated.constantState?.newDrawable()?.mutate() ?: mutated
        } catch (_: Exception) {
            null
        }
    }

    fun getIconBitmap(context: Context, tokenOrPkg: String): Bitmap? {
        if (tokenOrPkg.isBlank()) return null
        bitmapCache[tokenOrPkg]?.let { return it }

        val drawable = getIconDrawable(context, tokenOrPkg) ?: return null
        val bitmap = convertDrawableToBitmap(drawable)
        if (bitmap != null) {
            bitmapCache[tokenOrPkg] = bitmap
        }
        return bitmap
    }

    private fun loadGoogleCalendarDrawable(context: Context, pkg: String, day: Int): Drawable? {
        val pm = context.packageManager
        try {
            val res = pm.getResourcesForApplication(pkg)
            val dayFormatted = day.toString().padStart(2, '0')

            // Try dynamic_icons array from APK resources (Official Google Calendar array)
            val arrayNames = listOf("calendar_icons_dynamic", "calendar_icons_dynamic_nexus_round", "dynamic_icons")
            for (arrayName in arrayNames) {
                val arrayId = res.getIdentifier(arrayName, "array", pkg)
                if (arrayId != 0) {
                    try {
                        val typedArray = res.obtainTypedArray(arrayId)
                        val idx = (day - 1).coerceIn(0, typedArray.length() - 1)
                        val drawable = typedArray.getDrawable(idx)
                        typedArray.recycle()
                        if (drawable != null) return drawable
                    } catch (_: Exception) {}
                }
            }

            // Try direct authentic drawable resource names
            val candidateNames = listOf(
                "logo_calendar_${dayFormatted}_adaptive",
                "logo_calendar_${dayFormatted}",
                "calendar_icon_day_${day}",
                "calendar_icon_day_${dayFormatted}",
                "calendar_icon_${day}",
                "calendar_icon_${dayFormatted}"
            )
            for (cand in candidateNames) {
                val resId = res.getIdentifier(cand, "drawable", pkg)
                if (resId != 0) {
                    val drawable = res.getDrawable(resId, null)
                    if (drawable != null) return drawable
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading Google Calendar drawable", e)
        }
        return null
    }

    fun getIconPackDrawableNames(context: Context, iconPackPkg: String): List<String> {
        if (iconPackPkg == "system" || iconPackPkg.isBlank()) return emptyList()
        val drawables = LinkedHashSet<String>()
        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPkg)

            // 1. Try reading drawable.xml if present
            try {
                val drawableXmlId = iconPackRes.getIdentifier("drawable", "xml", iconPackPkg)
                if (drawableXmlId != 0) {
                    val parser = iconPackRes.getXml(drawableXmlId)
                    var eventType = parser.eventType
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                            val dr = parser.getAttributeValue(null, "drawable")
                            if (!dr.isNullOrBlank()) drawables.add(dr)
                        }
                        eventType = parser.next()
                    }
                }
            } catch (_: Exception) {}

            // 2. Try reading appfilter.xml
            if (drawables.isEmpty()) {
                var inputStream: java.io.InputStream? = null
                try {
                    val appfilterResId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPkg)
                    if (appfilterResId != 0) {
                        val parser = iconPackRes.getXml(appfilterResId)
                        var eventType = parser.eventType
                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                                val dr = parser.getAttributeValue(null, "drawable")
                                if (!dr.isNullOrBlank()) drawables.add(dr)
                            }
                            eventType = parser.next()
                        }
                    }
                } catch (_: Exception) {}

                if (drawables.isEmpty()) {
                    try {
                        inputStream = iconPackRes.assets.open("appfilter.xml")
                    } catch (_: Exception) {
                        try {
                            inputStream = iconPackRes.assets.open("appfilter_full.xml")
                        } catch (_: Exception) {}
                    }
                    if (inputStream != null) {
                        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        parser.setInput(inputStream, "UTF-8")
                        var eventType = parser.eventType
                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                                val dr = parser.getAttributeValue(null, "drawable")
                                if (!dr.isNullOrBlank()) drawables.add(dr)
                            }
                            eventType = parser.next()
                        }
                        inputStream.close()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load drawables from pack: $iconPackPkg", e)
        }
        return drawables.toList()
    }

    fun getDrawableFromPack(context: Context, iconPackPkg: String, drawableName: String): Drawable? {
        return try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(iconPackPkg)
            val resId = res.getIdentifier(drawableName, "drawable", iconPackPkg)
            if (resId != 0) res.getDrawable(resId, null) else null
        } catch (_: Exception) { null }
    }

    fun convertDrawableToBitmap(drawable: Drawable): Bitmap? {
        val workingDrawable = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        workingDrawable.alpha = 255

        if (workingDrawable is BitmapDrawable && workingDrawable.bitmap != null) {
            val bmp = workingDrawable.bitmap
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
                return bmp.copy(Bitmap.Config.ARGB_8888, false)
            }
            if (bmp.width > 0 && bmp.height > 0) {
                return bmp
            }
        }

        val rawW = workingDrawable.intrinsicWidth
        val rawH = workingDrawable.intrinsicHeight
        val targetSize = if (rawW > 0 && rawH > 0) maxOf(rawW, rawH).coerceIn(96, 256) else 192

        return try {
            val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            workingDrawable.setBounds(0, 0, targetSize, targetSize)
            workingDrawable.draw(canvas)
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "Error converting drawable to bitmap", e)
            null
        }
    }
}
