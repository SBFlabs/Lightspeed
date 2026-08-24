package com.sbf.lightspeed.system

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import com.sbf.lightspeed.LightspeedAccessibilityService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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
                        value.forEach { arr.put(it) }
                        settingsObject.put(key, arr)
                    }
                }
            }
            put("settings", settingsObject)
        }

        return root.toString(2)
    }

    fun exportToFile(context: Context, uri: Uri): Result<Int> {
        return try {
            val jsonString = exportToJson(context)
            val bytes = jsonString.toByteArray(Charsets.UTF_8)
            val outputStream = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                context.contentResolver.openOutputStream(uri)
            } ?: return Result.failure(IllegalStateException("Could not open output stream for $uri"))

            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            val count = context.getSharedPreferences("default", Context.MODE_PRIVATE).all.size
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
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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
                                // If the key expects an integer but was serialized as string
                                val intVal = value.toIntOrNull()
                                if (intVal != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_statusbar_") || key == "last_active_set_index")) {
                                    editor.putInt(key, intVal)
                                } else {
                                    editor.putString(key, value)
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

    fun importFromFile(context: Context, uri: Uri): Result<Int> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: return Result.failure(IllegalStateException("Could not open input stream for $uri"))

            importFromJson(context, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "File read during import failed", e)
            Result.failure(e)
        }
    }

    fun resetToDefaults(context: Context) {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.i(TAG, "All preferences reset to default values")
    }
}
