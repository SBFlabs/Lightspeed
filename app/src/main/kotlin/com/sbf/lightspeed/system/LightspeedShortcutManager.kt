package com.sbf.lightspeed.system

import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
    private var managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val TAG = "LightspeedShortcut"

    private val bitmapCache = android.util.LruCache<String, Bitmap>(150)
    private val drawableCache = android.util.LruCache<String, Drawable>(150)

    fun clearMemoryCache() {
        managerScope.cancel()
        managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        bitmapCache.evictAll()
        drawableCache.evictAll()
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
        bitmapCache.put(token, bitmap)
        drawableCache.put(token, BitmapDrawable(context.resources, bitmap))

        managerScope.launch {
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

    fun isCorruptBitmap(bmp: Bitmap): Boolean {
        if (bmp.width <= 0 || bmp.height <= 0) return true
        val w = bmp.width
        val h = bmp.height
        val first = bmp.getPixel(0, 0)
        // If whole image is solid single color (e.g. solid unbroken black/transparent), check samples across center
        val center = bmp.getPixel(w / 2, h / 2)
        if (first != center) return false
        val stepX = (w / 8).coerceAtLeast(1)
        val stepY = (h / 8).coerceAtLeast(1)
        for (x in stepX until w step stepX) {
            for (y in stepY until h step stepY) {
                if (bmp.getPixel(x, y) != first) {
                    return false
                }
            }
        }
        return true
    }

    fun purgeCorruptedIcons(context: Context) {
        managerScope.launch {
            try {
                val prefs = context.defaultPrefs()
                val allShortcutHashes = mutableSetOf<String>()
                prefs.all.forEach { (k, v) ->
                    val str = v?.toString() ?: ""
                    if (str.startsWith("shortcut:") || str.startsWith("custom:")) {
                        allShortcutHashes.add(str.hashCode().toString())
                    }
                    if (k.startsWith("gear_set_") && k.contains("_packages")) {
                        str.split(",").map { it.trim() }.filter { it.startsWith("shortcut:") || it.startsWith("custom:") }.forEach {
                            allShortcutHashes.add(it.hashCode().toString())
                        }
                    }
                }

                val dir = File(context.filesDir, "shortcut_icons")
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".png")) {
                            val key = file.nameWithoutExtension
                            // Delete if not an active shortcut or if bitmap is corrupted
                            if (!allShortcutHashes.contains(key)) {
                                file.delete()
                            } else {
                                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                if (bmp == null || isCorruptBitmap(bmp)) {
                                    file.delete()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadCachedBitmap(context: Context, token: String): Bitmap? {
        if (!token.startsWith("shortcut:") && !token.startsWith("custom:")) return null
        bitmapCache.get(token)?.let {
            if (!isCorruptBitmap(it)) return it
            bitmapCache.remove(token)
        }
        try {
            val file = File(context.filesDir, "shortcut_icons/${safeTokenKey(token)}.png")
            if (file.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    if (isCorruptBitmap(bmp)) {
                        file.delete()
                        return null
                    }
                    bitmapCache.put(token, bmp)
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

        // 0. Handle malformed tokens without proper structure (e.g., "shortcut:knstead")
        if (!body.contains("=") && !body.contains(";")) {
            // Case 1: Token is just a label without pkg (e.g., "shortcut:knstead")
            val label = decode(body)
            return ParsedShortcut("generic", "", label = label)
        }

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

        // 3. Home shortcut format: ;pkg=...;label=...;type=home_shortcut;id=...;
        if (body.contains("type=home_shortcut")) {
            val type = "home_shortcut"
            val pkg = if (body.contains("pkg=")) decode(body.substringAfter("pkg=").substringBefore(";")) else ""
            val id = if (body.contains("id=")) decode(body.substringAfter("id=").substringBefore(";")) else ""
            val label = if (body.contains("label=")) decode(body.substringAfter("label=").substringBefore(";")) else ""
            return ParsedShortcut(type, pkg, id = id, label = label)
        }

        // 4. Legacy Intent shortcut: intent:#Intent;...;custom_label=...;
        if (body.contains("#Intent;")) {
            val customLabel = if (body.contains("custom_label=")) decode(body.substringAfter("custom_label=").substringBefore(";"))
                              else if (body.contains("label=")) decode(body.substringAfter("label=").substringBefore(";")) else ""
            
            var cleanUri = body
            if (cleanUri.contains(";custom_label=")) cleanUri = cleanUri.substringBefore(";custom_label=")
            if (cleanUri.contains(";label=")) cleanUri = cleanUri.substringBefore(";label=")
            while (cleanUri.startsWith("intent:intent:")) {
                cleanUri = cleanUri.removePrefix("intent:")
            }
            if (!cleanUri.startsWith("intent:") && cleanUri.startsWith("#Intent;")) {
                cleanUri = "intent:$cleanUri"
            }

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

        // 5. Fallback legacy tokens
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
            val custom = context.defaultPrefs()
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
            "activity", "home_shortcut", "pinned" -> {
                val appLabel = if (parsed.packageName.isNotBlank()) {
                    try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(parsed.packageName, 0)).toString()
                    } catch (_: Exception) { "" }
                } else ""

                val cleanLabel = decode(parsed.label)
                if (cleanLabel.isNotBlank() && !cleanLabel.contains("#Intent;")) {
                    if (cleanLabel.contains("(") && cleanLabel.contains(")")) {
                        return cleanLabel
                    }
                    if (appLabel.isNotBlank() && !cleanLabel.equals(appLabel, ignoreCase = true)) {
                        return "$appLabel ($cleanLabel)"
                    }
                    return cleanLabel
                }
                if (appLabel.isNotBlank()) return appLabel
                return "Shortcut"
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
        drawableCache.get(token)?.let { return it }

        // 1. Check custom cached bitmap on disk/memory
        val cachedBmp = loadCachedBitmap(context, token)
        if (cachedBmp != null) {
            val d = BitmapDrawable(context.resources, cachedBmp)
            drawableCache.put(token, d)
            return d
        }

        val parsed = parseToken(token)

        // 2. Dynamic / Pinned / Home Shortcut via LauncherApps (Android 7.1+)
        if ((parsed.type == "pinned" || parsed.type == "home_shortcut") && parsed.packageName.isNotBlank() && parsed.id.isNotBlank()) {
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
                            drawableCache.put(token, d)
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
                drawableCache.put(token, d)
                return d
            } catch (_: Exception) {}
        }

        // 4. Host App / Icon Pack Fallback via LightspeedIconManager
        if (parsed.packageName.isNotBlank()) {
            val hostDrawable = LightspeedIconManager.getIconDrawable(context, parsed.packageName)
            if (hostDrawable != null) {
                drawableCache.put(token, hostDrawable)
                return hostDrawable
            }
        }

        // 5. Monogram Glyph Fallback (Clean Material 3 badge, NEVER an empty white circle)
        val label = resolveLabel(context, token)
        val fallbackBmp = createMonogramBitmap(context, label)
        if (fallbackBmp != null) {
            val d = BitmapDrawable(context.resources, fallbackBmp)
            drawableCache.put(token, d)
            return d
        }

        return null
    }

    fun resolveIconBitmap(context: Context, token: String): Bitmap? {
        if (token.isBlank() || token == "none") return null
        bitmapCache.get(token)?.let { return it }

        val d = resolveIconDrawable(context, token) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) {
            val bmp = d.bitmap
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
                val softwareBmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
                bitmapCache.put(token, softwareBmp)
                return softwareBmp
            }
            bitmapCache.put(token, bmp)
            return bmp
        }

        val w = d.intrinsicWidth.coerceIn(48, 192)
        val h = d.intrinsicHeight.coerceIn(48, 192)
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bitmapCache.put(token, bmp)
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
        Log.i(TAG, "launch: token='$token' type='${parsed.type}' pkg='${parsed.packageName}' id='${parsed.id}' label='${parsed.label}' shizukuActive=${ElevatedTaskCloser.isShizukuActive}")

        when (parsed.type) {
            "pinned", "home_shortcut" -> {
                var launched = false

                // 1. Primary: LauncherApps.startShortcut (works if default launcher or privileged)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(parsed.packageName, parsed.id, null, null, Process.myUserHandle())
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "LauncherApps startShortcut rejected: ${e.message}")
                    }
                }

                // 2. Secondary: Elevated Shizuku Home Shortcut Resolver (Contacts, WhatsApp, MacroDroid, Deep Links)
                if (!launched) {
                    launched = launchElevatedHomeShortcut(context, parsed)
                }

                // 3. Fallback: Standard package launcher intent
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
                var uri = parsed.intentUri.trim()
                if (uri.isNotBlank()) {
                    while (uri.startsWith("intent:intent:")) {
                        uri = uri.removePrefix("intent:")
                    }
                    if (!uri.startsWith("intent:") && uri.startsWith("#Intent;")) {
                        uri = "intent:$uri"
                    }

                    val launchIntent = try {
                        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Intent URI parse failed: $uri", e)
                        null
                    }

                    if (launchIntent != null) {
                        // 0. Unpack OEM Dialer / Hidden Intents
                        if (launchIntent.hasExtra("shortcut_action")) {
                            launchIntent.action = launchIntent.getStringExtra("shortcut_action")
                            launchIntent.component = null
                            launchIntent.setPackage(null)
                        }

                        // Unpack nested ShortcutMaker intent if present
                        val extraIntent = launchIntent.getStringExtra("extra_intent")
                        if (!extraIntent.isNullOrBlank()) {
                            try {
                                val decodedInner = Uri.decode(extraIntent)
                                val innerIntent = Intent.parseUri(decodedInner, Intent.URI_INTENT_SCHEME).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(innerIntent)
                                return true
                            } catch (_: Exception) {}
                        }

                        // 1. Try starting as Activity
                        try {
                            context.startActivity(launchIntent)
                            return true
                        } catch (e: Exception) {
                            Log.d(TAG, "Activity launch failed for $uri: ${e.message}, trying broadcast/service")
                        }

                        // 2. Try sending as Broadcast
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

                        // 4. Elevated Shizuku fallback
                        if (ElevatedTaskCloser.isShizukuActive) {
                            try {
                                ElevatedTaskCloser.execShizuku(intentToAmStartCommand(launchIntent))
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
    private fun handleLaunchException(context: Context, e: Exception) {
        if (e is SecurityException && e.message?.contains("Permission Denial") == true) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Permission Required: Please grant in App Settings", android.widget.Toast.LENGTH_LONG).show()
            }
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        } else if (e is android.content.ActivityNotFoundException) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Activity Not Found: Target app might be restricted or missing", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchElevatedHomeShortcut(context: Context, parsed: ParsedShortcut): Boolean {
        Log.i(TAG, "launchElevatedHomeShortcut: pkg=${parsed.packageName}, id='${parsed.id}', label='${parsed.label}', shizuku=${ElevatedTaskCloser.isShizukuActive}")
        if (parsed.packageName.isBlank()) return false

        try {
            val proc = ElevatedTaskCloser.execShizuku("cmd shortcut get-shortcuts ${parsed.packageName}") ?: return false
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            val rawLabel = if (parsed.label.contains("(") && parsed.label.endsWith(")")) {
                parsed.label.substringAfter("(").substringBeforeLast(")").trim()
            } else parsed.label

            val blocks = output.split("ShortcutInfo {").drop(1)
            val block = blocks.firstOrNull { b ->
                (parsed.id.isNotBlank() && (b.contains("id=${parsed.id},") || b.contains("id=${parsed.id}\n") || b.contains("id=${parsed.id}\r\n") || b.contains("id=${parsed.id} "))) ||
                (rawLabel.isNotBlank() && (b.contains("shortLabel=$rawLabel,") || b.contains("shortLabel=$rawLabel\n") || b.contains("shortLabel=$rawLabel\r\n") || b.contains("shortLabel=\"$rawLabel\""))) ||
                (parsed.label.isNotBlank() && (b.contains("shortLabel=${parsed.label},") || b.contains("shortLabel=${parsed.label}\n") || b.contains("shortLabel=${parsed.label}\r\n") || b.contains("shortLabel=\"${parsed.label}\"")))
            } ?: return false

            Log.i(TAG, "launchElevatedHomeShortcut: matched shortcut block successfully")

            // Case A: OEM Direct Dial / Contact Shortcut
            val contactId = if (block.contains("contactId=")) {
                block.substringAfter("contactId=").substringBefore(",").substringBefore("}").substringBefore("\n").substringBefore("\r").trim()
            } else ""

            if (contactId.isNotBlank() && contactId.all { it.isDigit() }) {
                Log.i(TAG, "launchElevatedHomeShortcut: contactId=$contactId")
                val phoneProc = ElevatedTaskCloser.execShizuku(
                    "content query --uri content://com.android.contacts/data/phones --projection data1 --where 'contact_id=$contactId'"
                )
                val phoneOutput = phoneProc?.inputStream?.bufferedReader()?.readText().orEmpty()
                phoneProc?.waitFor()

                val rawNumber = if (phoneOutput.contains("data1=")) {
                    phoneOutput.substringAfter("data1=").substringBefore("\n").substringBefore("\r").substringBefore(",").trim()
                } else ""

                if (rawNumber.isNotBlank()) {
                    val cleanNumber = rawNumber.replace(" ", "")
                    Log.i(TAG, "launchElevatedHomeShortcut: dialing $cleanNumber")
                    try {
                        val callIntent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$cleanNumber")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                        return true
                    } catch (e: Exception) {
                        val amProc = ElevatedTaskCloser.execShizuku("am start -a android.intent.action.CALL -d 'tel:$cleanNumber'")
                        return amProc?.waitFor() == 0
                    }
                }
            }

            // Case B: General App Shortcuts (WhatsApp, MacroDroid, Amazon, etc.)
            if (block.contains("Intent {")) {
                val intentBody = block.substringAfter("Intent {").substringBefore("}")
                val bundleBody = if (block.contains("PersistableBundle[{")) {
                    block.substringAfter("PersistableBundle[{").substringBefore("}]")
                } else ""

                fun extractField(key: String): String {
                    if (!intentBody.contains("$key=")) return ""
                    return intentBody.substringAfter("$key=").substringBefore(" ").trim()
                }

                val act = extractField("act")
                val cmp = extractField("cmp")
                val dat = extractField("dat")
                val flg = extractField("flg")

                val cmd = StringBuilder("am start ")
                if (act.isNotBlank()) cmd.append("-a $act ")
                if (cmp.isNotBlank()) cmd.append("-n $cmp ")
                if (dat.isNotBlank() && dat != "null" && !dat.endsWith("/...")) cmd.append("-d '$dat' ")
                if (flg.isNotBlank()) cmd.append("-f $flg ")

                if (bundleBody.isNotBlank()) {
                    val entries = bundleBody.split(", ")
                    for (entry in entries) {
                        val key = entry.substringBefore("=").trim()
                        val value = entry.substringAfter("=").trim()
                        if (key.isNotEmpty() && value.isNotEmpty() && value != "null") {
                            if (value.toLongOrNull() != null) {
                                cmd.append("--el '$key' $value ")
                            } else if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                                cmd.append("--ez '$key' $value ")
                            } else {
                                cmd.append("--es '$key' '${value.replace("'", "'\\''")}' ")
                            }
                        }
                    }
                }

                Log.i(TAG, "launchElevatedHomeShortcut: running: $cmd")
                val startProc = ElevatedTaskCloser.execShizuku(cmd.toString().trim())
                val exitCode = startProc?.waitFor() ?: -1
                Log.i(TAG, "launchElevatedHomeShortcut: am start exited with $exitCode")
                return exitCode == 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchElevatedHomeShortcut error", e)
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun intentToAmStartCommand(intent: Intent): String {
        val sb = java.lang.StringBuilder("am start ")
        intent.action?.let { sb.append("-a $it ") }
        intent.dataString?.let { sb.append("-d '$it' ") }
        intent.type?.let { sb.append("-t '$it' ") }
        intent.component?.let { sb.append("-n ${it.flattenToShortString()} ") }
        intent.extras?.keySet()?.forEach { key ->
            when (val value = intent.extras?.get(key)) {
                is String -> sb.append("--es '$key' '${value.replace("'", "'\\''")}' ")
                is Boolean -> sb.append("--ez '$key' $value ")
                is Int -> sb.append("--ei '$key' $value ")
                is Long -> sb.append("--el '$key' $value ")
                is Float -> sb.append("--ef '$key' $value ")
            }
        }
        return sb.toString().trim()
    }
}
