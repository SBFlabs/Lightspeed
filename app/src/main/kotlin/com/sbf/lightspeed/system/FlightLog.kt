package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FlightLog — Thread-safe, non-blocking persistent action ring buffer for Lightspeed.
 * Keeps the last 50 entries saved to private SharedPreferences so log entries
 * survive process death and app restarts.
 */
object FlightLog {
    private const val TAG = "FlightLog"
    private const val PREFS_NAME = "lightspeed_flight_log_prefs"
    private const val KEY_ENTRIES_JSON = "key_flight_log_entries"
    private const val MAX_ENTRIES = 50

    data class Entry(
        val timestamp: Long,
        val tag: String,
        val ok: Boolean,
        val text: String
    ) {
        fun formatTimestamp(): String {
            return try {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            } catch (_: Exception) {
                "$timestamp"
            }
        }

        fun formatFullTimestamp(): String {
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            } catch (_: Exception) {
                "$timestamp"
            }
        }
    }

    private val entries = ArrayList<Entry>()
    @Volatile
    private var isInitialized = false
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && prefs != null) return
        try {
            val appContext = context.applicationContext
            val p = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            val jsonStr = p.getString(KEY_ENTRIES_JSON, null)
            entries.clear()
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ts = obj.optLong("ts", System.currentTimeMillis())
                    val tag = obj.optString("tag", "unknown")
                    val ok = obj.optBoolean("ok", true)
                    val text = obj.optString("text", "")
                    entries.add(Entry(ts, tag, ok, text))
                }
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize FlightLog persistent buffer", e)
            entries.clear()
            isInitialized = true
        }
    }

    @Synchronized
    fun log(tag: String, ok: Boolean, text: String, maxChars: Int = 300) {
        try {
            val truncatedText = text.take(maxChars)
            val entry = Entry(
                timestamp = System.currentTimeMillis(),
                tag = tag,
                ok = ok,
                text = truncatedText
            )
            entries.add(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            saveAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Error adding FlightLog entry", e)
        }
    }

    @Synchronized
    fun getEntries(): List<Entry> {
        return ArrayList(entries)
    }

    @Synchronized
    fun getEntriesNewestFirst(): List<Entry> {
        return ArrayList(entries).reversed()
    }

    @Synchronized
    fun clearLog() {
        try {
            entries.clear()
            saveAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing FlightLog", e)
        }
    }

    private fun saveAsync() {
        val currentPrefs = prefs ?: return
        try {
            val array = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("ts", entry.timestamp)
                obj.put("tag", entry.tag)
                obj.put("ok", entry.ok)
                obj.put("text", entry.text)
                array.put(obj)
            }
            currentPrefs.edit().putString(KEY_ENTRIES_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving FlightLog entries", e)
        }
    }

    fun generateTextReport(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== LIGHTSPEED FLIGHT BLACKBOX REPORT ===")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine()

        try {
            val p = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val lastCrashTimestamp = p.getLong("key_last_crash_timestamp", 0L)
            val lastCrashMessage = p.getString("key_last_crash_message", null)
            val lastCrashStack = p.getString("key_last_crash_stack", null)

            if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                val crashTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastCrashTimestamp))
                sb.appendLine("--- LAST CRASH ANOMALY ---")
                sb.appendLine("Timestamp: $crashTimeStr")
                sb.appendLine("Message: $lastCrashMessage")
                sb.appendLine("Stacktrace:")
                sb.appendLine(lastCrashStack ?: "No stacktrace recorded")
                sb.appendLine()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting crash telemetry for report", e)
        }

        val logEntries = getEntriesNewestFirst()
        sb.appendLine("--- ACTION LOG ENTRIES (${logEntries.size}/$MAX_ENTRIES) ---")
        if (logEntries.isEmpty()) {
            sb.appendLine("No action entries recorded.")
        } else {
            for (entry in logEntries) {
                val statusStr = if (entry.ok) "OK" else "FAIL"
                sb.appendLine("[${entry.formatFullTimestamp()}] [${entry.tag}] [$statusStr] ${entry.text}")
            }
        }
        return sb.toString()
    }
}
