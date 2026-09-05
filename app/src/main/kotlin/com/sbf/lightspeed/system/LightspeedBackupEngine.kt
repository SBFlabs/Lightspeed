package com.sbf.lightspeed.system

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import com.sbf.lightspeed.LightspeedAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LightspeedBackupEngine {
    private const val TAG = "LightspeedBackup"
    const val BACKUP_VERSION = 1

    fun generateDefaultFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "lightspeed_backup_$dateStr.json"
    }

    fun exportToJson(context: Context): String {
        val prefs = context.defaultPrefs()
        val allEntries = prefs.all

        val root = JSONObject().apply {
            put("app", "Lightspeed")
            put("packageName", context.packageName)
            put("backupVersion", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("exportedAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")

            val settingsObject = JSONObject()
            for ((key, value) in allEntries) {
                if (key.startsWith("pending_")) continue // Skip transient state
                when (value) {
                    is Boolean -> settingsObject.put(key, value)
                    is Int -> settingsObject.put(key, value)
                    is Long -> settingsObject.put(key, value)
                    is Float -> settingsObject.put(key, value.toDouble())
                    is String -> settingsObject.put(key, value)
                    is Set<*> -> {
                        val arr = JSONArray()
                        value.forEach { item ->
                            if (item != null) arr.put(item.toString())
                        }
                        settingsObject.put(key, arr)
                    }
                }
            }
            put("settings", settingsObject)

            // Embed custom shortcut icon bitmaps as Base64 strings
            // Includes: actual shortcuts (shortcut:/custom:) AND manually overridden app icons
            val iconsObject = JSONObject()
            try {
                val validShortcutHashes = mutableSetOf<String>()

                // 1. Active shortcut/custom tokens from settings
                settingsObject.keys().forEach { k ->
                    val v = settingsObject.optString(k, "")
                    if (v.startsWith("shortcut:") || v.startsWith("custom:")) {
                        validShortcutHashes.add(v.hashCode().toString())
                    }
                    if (k.startsWith("gear_set_") && k.contains("_packages")) {
                        v.split(",").map { it.trim() }.filter { it.startsWith("shortcut:") || it.startsWith("custom:") }.forEach {
                            validShortcutHashes.add(it.hashCode().toString())
                        }
                    }
                }

                // 2. Manually overridden app: tokens (e.g. user replaced Settings icon via cockpit)
                val manualOverrides = LightspeedIconManager.getManuallyOverriddenTokens(context)
                manualOverrides.forEach { token ->
                    validShortcutHashes.add(token.hashCode().toString())
                }

                val dir = java.io.File(context.filesDir, "shortcut_icons")
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { iconFile ->
                        val key = iconFile.nameWithoutExtension
                        if (iconFile.isFile && iconFile.length() > 0 && iconFile.name.endsWith(".png") && validShortcutHashes.contains(key)) {
                            val bytes = iconFile.readBytes()
                            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            iconsObject.put(key, b64)
                        }
                    }
                }
            } catch (_: Exception) {}
            if (iconsObject.length() > 0) {
                put("shortcut_icons", iconsObject)
            }

            // Record which tokens were manually overridden so import can restore the flag
            val manualOverrides = LightspeedIconManager.getManuallyOverriddenTokens(context)
            if (manualOverrides.isNotEmpty()) {
                val arr = JSONArray()
                manualOverrides.forEach { arr.put(it) }
                put("manual_icon_overrides", arr)
            }
        }

        return root.toString(2)
    }

    fun exportToFile(context: Context, uri: Uri): Result<Int> {
        return try {
            val jsonString = exportToJson(context)
            val bytes = jsonString.toByteArray(Charsets.UTF_8)

            val written = try {
                context.contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                        try { pfd.fileDescriptor.sync() } catch (_: Exception) {}
                    }
                }
                true
            } catch (_: Exception) {
                false
            }

            if (!written) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                } ?: return Result.failure(IllegalStateException("Could not open output stream for $uri"))
            }

            val count = context.defaultPrefs().all.size
            Log.i(TAG, "Exported $count entries (${bytes.size} bytes) successfully to $uri")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

    fun importFromJson(context: Context, jsonString: String): Result<Int> {
        return try {
            val cleanJson = jsonString.trim().removePrefix("\uFEFF").trim()
            if (cleanJson.isBlank()) {
                return Result.failure(IllegalArgumentException("Backup payload is empty"))
            }

            val root = JSONObject(cleanJson)
            val settingsObject = if (root.has("settings") && root.optJSONObject("settings") != null) {
                root.getJSONObject("settings")
            } else {
                root
            }
            val prefs = context.defaultPrefs()
            val editor = prefs.edit()

            var importedCount = 0
            val keys = settingsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "app" || key == "packageName" || key == "backupVersion" || key == "exportedAt" || key == "exportedAtFormatted" || key == "device") {
                    continue
                }

                if (settingsObject.isNull(key)) {
                    continue
                }

                val value = settingsObject.get(key)
                when (value) {
                    is Boolean -> {
                        editor.putBoolean(key, value)
                        importedCount++
                    }
                    is Number -> {
                        if (key.endsWith("_time") || key.endsWith("_timestamp")) {
                            editor.putLong(key, value.toLong())
                        } else if (key == LightspeedPreferences.KEY_BACK_TAP_THRESHOLD) {
                            editor.putFloat(key, value.toFloat())
                        } else {
                            editor.putInt(key, value.toInt())
                        }
                        importedCount++
                    }
                    is String -> {
                        when (value) {
                            "true", "false" -> {
                                editor.putBoolean(key, value.toBoolean())
                            }
                            else -> {
                                if (key == LightspeedPreferences.KEY_BACK_TAP_THRESHOLD) {
                                    val floatVal = value.toFloatOrNull()
                                    if (floatVal != null) {
                                        editor.putFloat(key, floatVal)
                                    } else {
                                        editor.putString(key, value)
                                    }
                                } else {
                                    val intVal = value.toIntOrNull()
                                    if (intVal != null && (key.startsWith("pref_hardware_") || key.startsWith("pref_sidebar_") || key.startsWith("pref_statusbar_") || key.startsWith("pref_notch_") || key.startsWith("pref_horizon_rail_") || key.startsWith("last_active_set_index") || key == LightspeedPreferences.KEY_REFUELING_WIDGET_ID || key == LightspeedPreferences.KEY_CAPSULE_WIDGET_ID)) {
                                        editor.putInt(key, intVal)
                                    } else {
                                        editor.putString(key, value)
                                    }
                                }
                            }
                        }
                        importedCount++
                    }
                    is JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) {
                            set.add(value.getString(i))
                        }
                        editor.putStringSet(key, set)
                        importedCount++
                    }
                    else -> {
                        editor.putString(key, value.toString())
                        importedCount++
                    }
                }
            }

            val success = editor.commit()
            if (!success) {
                editor.apply()
            }

            // Restore custom shortcut icons from Base64
            // Includes shortcuts AND manually overridden app: tokens
            val iconsObject = root.optJSONObject("shortcut_icons")
            if (iconsObject != null) {
                try {
                    val validShortcutHashes = mutableSetOf<String>()
                    val settingsObj = root.optJSONObject("settings")
                    if (settingsObj != null) {
                        settingsObj.keys().forEach { k ->
                            val v = settingsObj.optString(k, "")
                            if (v.startsWith("shortcut:") || v.startsWith("custom:")) {
                                validShortcutHashes.add(v.hashCode().toString())
                            }
                            if (k.startsWith("gear_set_") && k.contains("_packages")) {
                                v.split(",").map { it.trim() }.filter { it.startsWith("shortcut:") || it.startsWith("custom:") }.forEach {
                                    validShortcutHashes.add(it.hashCode().toString())
                                }
                            }
                        }
                    }

                    // Also allow manually overridden tokens from backup metadata
                    val manualOverridesArr = root.optJSONArray("manual_icon_overrides")
                    val restoredManualTokens = mutableSetOf<String>()
                    if (manualOverridesArr != null) {
                        for (i in 0 until manualOverridesArr.length()) {
                            val token = manualOverridesArr.optString(i, "")
                            if (token.isNotBlank()) {
                                validShortcutHashes.add(token.hashCode().toString())
                                restoredManualTokens.add(token)
                            }
                        }
                    }

                    val dir = java.io.File(context.filesDir, "shortcut_icons")
                    if (!dir.exists()) dir.mkdirs()
                    val iconKeys = iconsObject.keys()
                    while (iconKeys.hasNext()) {
                        val key = iconKeys.next()
                        if (validShortcutHashes.contains(key)) {
                            val b64 = iconsObject.getString(key)
                            if (b64.isNotBlank()) {
                                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                                val file = java.io.File(dir, "$key.png")
                                file.writeBytes(bytes)
                            }
                        }
                    }

                    // Restore the manual override flag set in SharedPreferences
                    if (restoredManualTokens.isNotEmpty()) {
                        restoredManualTokens.forEach { token ->
                            LightspeedIconManager.markAsManuallyOverridden(context, token)
                        }
                    }

                    LightspeedShortcutManager.clearMemoryCache()
                    LightspeedIconManager.clearCache()
                } catch (_: Exception) {}
            }

            try {
                LightspeedAccessibilityService.instance?.reloadPreferences()
            } catch (_: Exception) {}
            Log.i(TAG, "Imported and committed $importedCount settings entries successfully")
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.failure(e)
        }
    }

    private fun readStringFromUri(context: Context, uri: Uri): String {
        // Tier 1: Direct File check
        if (uri.scheme == "file" || uri.path?.startsWith("/storage/") == true) {
            try {
                val path = if (uri.scheme == "file") (uri.path ?: "") else uri.path!!
                val directFile = java.io.File(path)
                if (directFile.exists() && directFile.length() > 0) {
                    val content = directFile.readText(Charsets.UTF_8)
                    if (content.isNotBlank()) return content
                }
            } catch (_: Exception) {}
        }

        // Tier 2: Raw byte read from ContentResolver openInputStream
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty()) {
                    val content = String(bytes, Charsets.UTF_8)
                    if (content.isNotBlank()) return content
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 openInputStream failed: ${e.message}")
        }

        // Tier 3: Direct ParcelFileDescriptor read
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
                    val bytes = fis.readBytes()
                    if (bytes.isNotEmpty()) {
                        val content = String(bytes, Charsets.UTF_8)
                        if (content.isNotBlank()) return content
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 3 openFileDescriptor failed: ${e.message}")
        }

        // Tier 4: Document raw path extraction
        try {
            val path = uri.path ?: ""
            if (path.contains("raw:")) {
                val rawPath = path.substringAfter("raw:")
                val f = java.io.File(rawPath)
                if (f.exists() && f.length() > 0) {
                    val content = f.readText(Charsets.UTF_8)
                    if (content.isNotBlank()) return content
                }
            }
        } catch (_: Exception) {}

        throw IllegalStateException("Selected file returned 0 bytes (payload empty). Please try picking the file directly from Internal Storage or Downloads.")
    }

    fun importFromFile(context: Context, uri: Uri): Result<Int> {
        return try {
            Log.i(TAG, "Importing from uri: $uri (scheme=${uri.scheme})")
            val jsonString = readStringFromUri(context, uri)
            Log.i(TAG, "Read ${jsonString.length} chars from $uri")
            importFromJson(context, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "File read during import failed for $uri", e)
            Result.failure(e)
        }
    }

    fun resetToDefaults(context: Context) {
        val prefs = context.defaultPrefs()
        prefs.edit().clear().apply()
        Log.i(TAG, "All preferences reset to default values")
    }
}
