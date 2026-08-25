package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class ParsedShortcut(
    val type: String, // "pinned", "activity", "intent", "app_shortcut", "app", "system", "unknown"
    val packageName: String,
    val id: String = "",
    val activityName: String = "",
    val label: String = "",
    val intentUri: String = ""
)

object LightspeedShortcutManager {
    private const val TAG = "LightspeedShortcut"

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val drawableCache = ConcurrentHashMap<String, Drawable>()

    fun clearMemoryCache() {
        bitmapCache.clear()
        drawableCache.clear()
    }

    private fun safeTokenKey(token: String): String {
        return token.hashCode().toString()
    }

    private fun encode(str: String): String = Uri.encode(str)
    private fun decode(str: String): String = try { Uri.decode(str) } catch (_: Exception) { str }

    fun createPinnedShortcutToken(pkg: String, id: String, label: String): String {
        return "shortcut:type=pinned;pkg=$pkg;id=${encode(id)};label=${encode(label)};"
    }

    fun createActivityShortcutToken(pkg: String, act: String, label: String): String {
        return "shortcut:type=activity;pkg=$pkg;act=$act;label=${encode(label)};"
    }

    fun createCustomShortcutToken(
        context: Context,
        pkg: String,
        label: String,
        intent: Intent,
        bitmap: Bitmap?
    ): String {
        val rawUri = intent.toUri(Intent.URI_INTENT_SCHEME)
        val base64Uri = Base64.encodeToString(rawUri.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        val cleanLabel = if (label.isNotBlank()) label else "Shortcut"
        val token = "shortcut:type=intent;pkg=$pkg;label=${encode(cleanLabel)};b64uri=$base64Uri;"

        if (bitmap != null) {
            saveShortcutBitmap(context, token, bitmap)
        }
        return token
    }

    fun saveShortcutBitmap(context: Context, token: String, bitmap: Bitmap) {
        if (token.isBlank()) return
        bitmapCache[token] = bitmap
        drawableCache[token] = BitmapDrawable(context.resources, bitmap)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.filesDir, "shortcut_icons")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "${safeTokenKey(token)}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error caching shortcut bitmap to disk", e)
            }
        }
    }

    fun loadCachedBitmap(context: Context, token: String): Bitmap? {
        bitmapCache[token]?.let { return it }
        try {
            val file = File(context.filesDir, "shortcut_icons/${safeTokenKey(token)}.png")
            if (file.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    bitmapCache[token] = bmp
                    return bmp
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun parseToken(token: String): ParsedShortcut {
        if (token.isBlank()) return ParsedShortcut("unknown", "")

        if (token.startsWith("system:")) {
            return ParsedShortcut("system", "", label = token.removePrefix("system:").replace("_", " ").uppercase())
        }

        if (token.startsWith("app:")) {
            return ParsedShortcut("app", token.removePrefix("app:"))
        }

        if (!token.startsWith("shortcut:")) {
            return ParsedShortcut("app", token)
        }

        val body = token.removePrefix("shortcut:")

        // 1. Structured Token Format: type=...;pkg=...;
        if (body.contains("type=") && body.contains("pkg=")) {
            val type = body.substringAfter("type=").substringBefore(";")
            val pkg = body.substringAfter("pkg=").substringBefore(";")
            val id = if (body.contains("id=")) decode(body.substringAfter("id=").substringBefore(";")) else ""
            val act = if (body.contains("act=")) body.substringAfter("act=").substringBefore(";")
                      else if (body.contains("activity=")) body.substringAfter("activity=").substringBefore(";") else ""
            val label = if (body.contains("label=")) decode(body.substringAfter("label=").substringBefore(";")) else ""
            
            var intentUri = ""
            if (body.contains("b64uri=")) {
                try {
                    val b64 = body.substringAfter("b64uri=").substringBefore(";")
                    val bytes = Base64.decode(b64, Base64.NO_WRAP or Base64.URL_SAFE)
                    intentUri = String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {}
            } else if (body.contains("uri=")) {
                intentUri = decode(body.substringAfter("uri=").substringBefore(";"))
            }

            return ParsedShortcut(type, pkg, id, act, label, intentUri)
        }

        // 2. Legacy LauncherApps dynamic shortcut: ;id=...;pkg=...;label=...;
        if (body.contains(";id=") && body.contains(";pkg=")) {
            val id = body.substringAfter(";id=").substringBefore(";")
            val pkg = body.substringAfter(";pkg=").substringBefore(";")
            val label = if (body.contains(";label=")) decode(body.substringAfter(";label=").substringBefore(";")) else ""
            return ParsedShortcut("pinned", pkg, id = id, label = label)
        }

        // 3. Legacy Intent shortcut: intent:#Intent;...;custom_label=...;
        if (body.contains("#Intent;")) {
            val customLabel = if (body.contains("custom_label=")) decode(body.substringAfter("custom_label=").substringBefore(";"))
                              else if (body.contains("label=")) decode(body.substringAfter("label=").substringBefore(";")) else ""
            
            var cleanUri = body
            if (cleanUri.contains(";custom_label=")) cleanUri = cleanUri.substringBefore(";custom_label=")
            if (cleanUri.contains(";label=")) cleanUri = cleanUri.substringBefore(";label=")
            if (cleanUri.startsWith("intent:intent:#Intent;")) cleanUri = cleanUri.removePrefix("intent:")
            if (cleanUri.startsWith("intent:") && cleanUri.contains("#Intent;")) cleanUri = cleanUri.removePrefix("intent:")

            var pkg = ""
            try {
                val parsed = Intent.parseUri(cleanUri, Intent.URI_INTENT_SCHEME)
                pkg = parsed.`package` ?: parsed.component?.packageName ?: ""
                if (pkg.isBlank()) {
                    pkg = when {
                        cleanUri.contains(";package=") -> cleanUri.substringAfter(";package=").substringBefore(";")
                        cleanUri.contains("package=") -> cleanUri.substringAfter("package=").substringBefore(";")
                        cleanUri.contains(";pkg=") -> cleanUri.substringAfter(";pkg=").substringBefore(";")
                        cleanUri.contains("pkg=") -> cleanUri.substringAfter("pkg=").substringBefore(";")
                        else -> ""
                    }
                }
            } catch (_: Exception) {}

            return ParsedShortcut("intent", pkg, label = customLabel, intentUri = cleanUri)
        }

        // 4. Fallback legacy tokens
        val pkg = when {
            body.contains(";pkg=") -> body.substringAfter(";pkg=").substringBefore(";")
            body.contains("pkg=") -> body.substringAfter("pkg=").substringBefore(";")
            body.contains("package=") -> body.substringAfter("package=").substringBefore(";")
            else -> ""
        }
        val label = when {
            body.contains(";label=") -> decode(body.substringAfter(";label=").substringBefore(";"))
            body.contains("label=") -> decode(body.substringAfter("label=").substringBefore(";"))
            else -> ""
        }
        return ParsedShortcut("generic", pkg, label = label)
    }

    fun resolveLabel(context: Context, token: String): String {
        if (token.isBlank() || token == "none") return ""

        try {
            val custom = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                .getString("custom_label_${safeTokenKey(token)}", null)
            if (!custom.isNullOrBlank()) return custom
        } catch (_: Exception) {}

        val parsed = parseToken(token)

        when (parsed.type) {
            "system" -> return parsed.label
            "app" -> {
                if (parsed.packageName.isNotBlank()) {
                    return try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(parsed.packageName, 0)).toString()
                    } catch (_: Exception) { parsed.packageName }
                }
                return token
            }
            else -> {
                val cleanLabel = decode(parsed.label)
                if (cleanLabel.isNotBlank() && !cleanLabel.contains("#Intent;")) {
                    return cleanLabel
                }
                if (parsed.packageName.isNotBlank()) {
                    return try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(parsed.packageName, 0)).toString()
                    } catch (_: Exception) { parsed.packageName }
                }
                return "Shortcut"
            }
        }
    }

    fun resolveIconDrawable(context: Context, token: String): Drawable? {
        if (token.isBlank() || token == "none") return null
        drawableCache[token]?.let { return it }

        // 1. Check custom cached bitmap on disk/memory
        val cachedBmp = loadCachedBitmap(context, token)
        if (cachedBmp != null) {
            val d = BitmapDrawable(context.resources, cachedBmp)
            drawableCache[token] = d
            return d
        }

        val parsed = parseToken(token)

        // 2. Dynamic / Pinned Shortcut via LauncherApps (Android 7.1+)
        if (parsed.type == "pinned" && parsed.packageName.isNotBlank() && parsed.id.isNotBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                try {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val query = LauncherApps.ShortcutQuery().apply {
                        setPackage(parsed.packageName)
                        setShortcutIds(listOf(parsed.id))
                        setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        )
                    }
                    val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                    val shortcut = shortcuts?.firstOrNull()
                    if (shortcut != null) {
                        val d = launcherApps.getShortcutBadgedIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                            ?: launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                        if (d != null) {
                            drawableCache[token] = d
                            return d
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed loading launcher shortcut icon", e)
                }
            }
        }

        // 3. Activity Shortcut via PackageManager
        if (parsed.type == "activity" && parsed.packageName.isNotBlank() && parsed.activityName.isNotBlank()) {
            try {
                val pm = context.packageManager
                val comp = android.content.ComponentName(parsed.packageName, parsed.activityName)
                val d = pm.getActivityIcon(comp)
                drawableCache[token] = d
                return d
            } catch (_: Exception) {}
        }

        // 4. Host App / Icon Pack Fallback via LightspeedIconManager
        if (parsed.packageName.isNotBlank()) {
            val hostDrawable = LightspeedIconManager.getIconDrawable(context, parsed.packageName)
            if (hostDrawable != null) {
                drawableCache[token] = hostDrawable
                return hostDrawable
            }
        }

        // 5. Monogram Glyph Fallback (Clean Material 3 badge, NEVER an empty white circle)
        val label = resolveLabel(context, token)
        val fallbackBmp = createMonogramBitmap(context, label)
        if (fallbackBmp != null) {
            val d = BitmapDrawable(context.resources, fallbackBmp)
            drawableCache[token] = d
            return d
        }

        return null
    }

    fun resolveIconBitmap(context: Context, token: String): Bitmap? {
        if (token.isBlank() || token == "none") return null
        bitmapCache[token]?.let { return it }

        val d = resolveIconDrawable(context, token) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) {
            val bmp = d.bitmap
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
                val softwareBmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
                bitmapCache[token] = softwareBmp
                return softwareBmp
            }
            bitmapCache[token] = bmp
            return bmp
        }

        val w = d.intrinsicWidth.coerceIn(48, 192)
        val h = d.intrinsicHeight.coerceIn(48, 192)
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bitmapCache[token] = bmp
            bmp
        } catch (_: Exception) { null }
    }

    private fun createMonogramBitmap(context: Context, label: String): Bitmap? {
        return try {
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Dark subtle round tile
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(220, 28, 36, 54)
            val r = size / 2f
            canvas.drawCircle(r, r, r - 2f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * context.resources.displayMetrics.density
            paint.color = Color.argb(140, 100, 180, 255)
            canvas.drawCircle(r, r, r - 2f, paint)

            // Letter
            val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "S"
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = size * 0.48f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

            val fontMetrics = paint.fontMetrics
            val baseline = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(letter, r, baseline, paint)

            bmp
        } catch (_: Exception) { null }
    }

    fun launch(context: Context, token: String): Boolean {
        if (token.isBlank() || token == "none") return false
        val parsed = parseToken(token)

        when (parsed.type) {
            "pinned" -> {
                var launched = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(parsed.packageName, parsed.id, null, null, Process.myUserHandle())
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "LauncherApps shortcut launch failed, attempting elevated launch", e)
                    }
                }

                if (!launched && ElevatedTaskCloser.isShizukuActive) {
                    try {
                        ElevatedTaskCloser.execShizuku("cmd shortcut start-shortcut --user 0 -p ${parsed.packageName} -i ${parsed.id} || am start-shortcut -p ${parsed.packageName} -i ${parsed.id}")
                        launched = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Shizuku shortcut launch failed", e)
                    }
                }

                if (!launched) {
                    context.packageManager.getLaunchIntentForPackage(parsed.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(it); launched = true } catch (_: Exception) {}
                    }
                }
                return launched
            }

            "activity", "app_shortcut" -> {
                if (parsed.packageName.isNotBlank() && parsed.activityName.isNotBlank()) {
                    val intent = Intent().apply {
                        setClassName(parsed.packageName, parsed.activityName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        return true
                    } catch (_: Exception) {
                        try {
                            val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                setClassName(parsed.packageName, parsed.activityName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(shortcutIntent)
                            return true
                        } catch (e: Exception) {
                            if (ElevatedTaskCloser.isShizukuActive) {
                                ElevatedTaskCloser.execShizuku("am start -n ${parsed.packageName}/${parsed.activityName}")
                                return true
                            }
                        }
                    }
                }
                return false
            }

            "intent" -> {
                if (parsed.intentUri.isNotBlank()) {
                    val launchIntent = try {
                        Intent.parseUri(parsed.intentUri, Intent.URI_INTENT_SCHEME).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Intent URI parse failed: ${parsed.intentUri}", e)
                        null
                    }

                    if (launchIntent != null) {
                        // 1. Try starting as Activity
                        try {
                            context.startActivity(launchIntent)
                            return true
                        } catch (e: Exception) {
                            Log.d(TAG, "Activity launch failed for ${parsed.intentUri}: ${e.message}, trying broadcast/service")
                        }

                        // 2. Try sending as Broadcast (essential for MacroDroid, Tasker, automation shortcuts)
                        try {
                            context.sendBroadcast(launchIntent)
                            return true
                        } catch (e: Exception) {
                            Log.d(TAG, "Broadcast launch failed: ${e.message}")
                        }

                        // 3. Try starting as Service
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(launchIntent)
                            } else {
                                context.startService(launchIntent)
                            }
                            return true
                        } catch (e: Exception) {
                            Log.d(TAG, "Service launch failed: ${e.message}")
                        }

                        // 4. Shizuku elevated shell execution fallback
                        if (ElevatedTaskCloser.isShizukuActive) {
                            try {
                                val pkgArg = if (parsed.packageName.isNotBlank()) "-p ${parsed.packageName}" else ""
                                ElevatedTaskCloser.execShizuku("am start $pkgArg '${parsed.intentUri}' || am broadcast $pkgArg '${parsed.intentUri}'")
                                return true
                            } catch (_: Exception) {}
                        }
                    }
                }
                return false
            }

            else -> {
                // Try standard package launch
                if (parsed.packageName.isNotBlank()) {
                    context.packageManager.getLaunchIntentForPackage(parsed.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(it); return true } catch (_: Exception) {}
                    }
                }
                return false
            }
        }
    }
}
