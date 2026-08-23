package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
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

    // Shizuku Shortcut Icon mapping: "pkg|id" -> Pair(iconResId, iconResName)
    val shortcutResourceCache = ConcurrentHashMap<String, Pair<Int, String?>>()

    private var currentLoadedPack: String? = null
    private var isPackLoaded = false

    private val CALENDAR_PACKAGES = setOf(
        "com.google.android.calendar",
        "com.transsion.calendar",
        "com.samsung.android.calendar",
        "com.android.calendar",
        "com.google.android.apps.calendar",
        "com.simplemobiletools.calendar",
        "com.simplemobiletools.calendar.pro",
        "com.xiaomi.calendar",
        "com.oneplus.calendar"
    )

    fun registerShortcutIcon(pkg: String, id: String, resId: Int, resName: String? = null) {
        if (pkg.isNotBlank() && id.isNotBlank() && resId != 0) {
            shortcutResourceCache["$pkg|$id"] = Pair(resId, resName)
            shortcutResourceCache["${pkg.lowercase()}|${id.lowercase()}"] = Pair(resId, resName)
        }
    }

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

    fun getIconDrawable(context: Context, tokenOrPkg: String): Drawable? {
        if (tokenOrPkg.isBlank()) return null
        drawableCache[tokenOrPkg]?.let { return it }

        ensureLoaded(context)
        val activePack = getActiveIconPack(context)
        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val dayStr = dayOfMonth.toString().padStart(2, '0')

        // 1. Dynamic Shortcut Icons (Google Play My Apps, Chrome tabs, etc.)
        if (tokenOrPkg.startsWith("shortcut:")) {
            val shortcutDrawable = loadShortcutIconDrawable(context, tokenOrPkg)
            if (shortcutDrawable != null) {
                drawableCache[tokenOrPkg] = shortcutDrawable
                return shortcutDrawable
            }
        }

        val extractedPkg = when {
            tokenOrPkg.startsWith("app:") -> tokenOrPkg.removePrefix("app:")
            tokenOrPkg.startsWith("shortcut:") -> {
                if (tokenOrPkg.contains(";pkg=")) tokenOrPkg.substringAfter(";pkg=").substringBefore(";")
                else tokenOrPkg.substringAfter("pkg=").substringBefore(";")
            }
            else -> tokenOrPkg
        }

        // 2. Third-Party Icon Pack Resolution
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
                                drawableCache[tokenOrPkg] = calDrawable
                                return calDrawable
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
                            drawableCache[tokenOrPkg] = packDrawable
                            return packDrawable
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving icon from pack: $activePack", e)
            }
        }

        // 3. Dynamic Calendar Icon Extraction / Badging
        if (isCalendarPackage(extractedPkg)) {
            val calDrawable = loadAuthenticCalendarDrawable(context, extractedPkg, dayOfMonth)
            if (calDrawable != null) {
                drawableCache[tokenOrPkg] = calDrawable
                return calDrawable
            }
        }

        // 4. System App Icon Fallback
        return try {
            val pm = context.packageManager
            val defaultDrawable = pm.getApplicationIcon(extractedPkg)
            drawableCache[tokenOrPkg] = defaultDrawable
            defaultDrawable
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

    private fun loadShortcutIconDrawable(context: Context, shortcutToken: String): Drawable? {
        val pm = context.packageManager
        val shortcutId = when {
            shortcutToken.contains(";id=") -> shortcutToken.substringAfter(";id=").substringBefore(";")
            shortcutToken.contains("id=") -> shortcutToken.substringAfter("id=").substringBefore(";")
            else -> ""
        }
        val pkgName = when {
            shortcutToken.contains(";pkg=") -> shortcutToken.substringAfter(";pkg=").substringBefore(";")
            shortcutToken.contains("pkg=") -> shortcutToken.substringAfter("pkg=").substringBefore(";")
            shortcutToken.contains("package=") -> shortcutToken.substringAfter("package=").substringBefore(";")
            else -> ""
        }
        val activityName = when {
            shortcutToken.contains(";activity=") -> shortcutToken.substringAfter(";activity=").substringBefore(";")
            shortcutToken.contains("activity=") -> shortcutToken.substringAfter("activity=").substringBefore(";")
            else -> ""
        }

        // A. If activity is specified (Deep Activity / Plugin)
        if (pkgName.isNotBlank() && activityName.isNotBlank()) {
            try {
                val actDrawable = pm.getActivityIcon(ComponentName(pkgName, activityName))
                if (actDrawable != null) return actDrawable
            } catch (_: Exception) {}
        }

        // B. Check Shizuku shortcut resource cache (direct APK drawable)
        if (pkgName.isNotBlank() && shortcutId.isNotBlank()) {
            val resPair = shortcutResourceCache["$pkgName|$shortcutId"]
                ?: shortcutResourceCache["${pkgName.lowercase()}|${shortcutId.lowercase()}"]
                ?: shortcutResourceCache.entries.firstOrNull { it.key.startsWith("$pkgName|", ignoreCase = true) && it.key.contains(shortcutId, ignoreCase = true) }?.value

            if (resPair != null && resPair.first != 0) {
                try {
                    val res = pm.getResourcesForApplication(pkgName)
                    val drawable = res.getDrawable(resPair.first, null)
                    if (drawable != null) return drawable
                } catch (_: Exception) {}
            }
        }

        // C. Query LauncherApps if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                if (shortcutId.isNotBlank() && pkgName.isNotBlank()) {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                    val query = LauncherApps.ShortcutQuery().apply {
                        setPackage(pkgName)
                        setShortcutIds(listOf(shortcutId))
                        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                    }
                    val shortcuts = launcherApps?.getShortcuts(query, Process.myUserHandle())
                    val shortcutInfo = shortcuts?.firstOrNull()
                    if (shortcutInfo != null) {
                        val icon = launcherApps.getShortcutIconDrawable(shortcutInfo, context.resources.displayMetrics.densityDpi)
                        if (icon != null) return icon
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading shortcut icon via LauncherApps", e)
            }
        }

        return null
    }

    private fun isCalendarPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return CALENDAR_PACKAGES.contains(p) || p.contains("calendar")
    }

    private fun loadAuthenticCalendarDrawable(context: Context, pkg: String, day: Int): Drawable? {
        val pm = context.packageManager
        try {
            val res = pm.getResourcesForApplication(pkg)
            val dayFormatted = day.toString().padStart(2, '0')

            // 1. Try dynamic_icons array from APK resources (Official Google Calendar array)
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

            // 2. Try direct authentic drawable resource names
            val candidateNames = listOf(
                "logo_calendar_${dayFormatted}_adaptive",
                "logo_calendar_${dayFormatted}",
                "calendar_icon_day_${day}",
                "calendar_icon_day_${dayFormatted}",
                "calendar_icon_${day}",
                "calendar_icon_${dayFormatted}",
                "ic_launcher_calendar_${day}",
                "ic_launcher_calendar_day_${day}"
            )
            for (cand in candidateNames) {
                val resId = res.getIdentifier(cand, "drawable", pkg)
                if (resId != 0) {
                    val drawable = res.getDrawable(resId, null)
                    if (drawable != null) return drawable
                }
            }

            // 3. For OEM Calendars (like Transsion / Infinix / Tecno / Samsung) that don't have 31 separate APK assets:
            // Dynamically badge the authentic base icon with today's live day number
            val baseAppIcon = pm.getApplicationIcon(pkg)
            return renderDateOnBaseIcon(context, baseAppIcon, day)

        } catch (e: Exception) {
            Log.w(TAG, "Error loading authentic calendar drawable from $pkg", e)
        }
        return null
    }

    private fun renderDateOnBaseIcon(context: Context, baseDrawable: Drawable, day: Int): Drawable? {
        return try {
            val d = context.resources.displayMetrics.density
            val size = (64 * d).toInt()
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Draw base OEM calendar icon
            baseDrawable.setBounds(0, 0, size, size)
            baseDrawable.draw(canvas)

            // Smooth clean card surface over the date window to cover static OEM date
            val cardRect = RectF(size * 0.16f, size * 0.32f, size * 0.84f, size * 0.86f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(cardRect, 8f * d, 8f * d, cardPaint)

            // Paint today's date in bold Material typography centered in the date zone
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A1C1E")
                textSize = 21f * d
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val textBounds = Rect()
            val dayStr = day.toString()
            textPaint.getTextBounds(dayStr, 0, dayStr.length, textBounds)
            val textY = cardRect.centerY() + (textBounds.height() / 2f) - textBounds.bottom
            canvas.drawText(dayStr, cardRect.centerX(), textY, textPaint)

            BitmapDrawable(context.resources, bmp)
        } catch (_: Exception) {
            baseDrawable
        }
    }

    private fun convertDrawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceIn(48, 256)
        val h = drawable.intrinsicHeight.coerceIn(48, 256)
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
