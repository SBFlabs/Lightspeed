package com.sbf.lightspeed.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% offline local CalendarContract reader + unified/per-widget countdown configuration store
 * for the Category Cruise Screen Time HUD widget.
 */
object LightspeedCalendarCountdownEngine {

    const val SOURCE_MANUAL = "manual"
    const val SOURCE_CALENDAR = "calendar"

    data class DeviceCalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val color: Int
    )

    data class NextCalendarEvent(
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val calendarId: Long,
        val calendarName: String,
        val isAllDay: Boolean,
        val prevEventEndMs: Long = 0L
    )

    data class CountdownConfig(
        val usePerWidget: Boolean,
        val enabled: Boolean,
        val source: String,
        val title: String,
        val targetMs: Long,
        val calendarIds: Set<String>,
        val startMs: Long = 0L
    )

    data class DisplayCountdown(
        val title: String,
        val targetMs: Long,
        val progressFraction: Float
    )

    @Volatile
    private var cachedCalendars: List<DeviceCalendarInfo> = emptyList()
    @Volatile
    private var lastCalendarsFetchMs: Long = 0L

    private data class CachedEventEntry(
        val event: NextCalendarEvent?,
        val fetchedAtMs: Long
    )

    private val nextEventCache = ConcurrentHashMap<String, CachedEventEntry>()

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun invalidateCaches() {
        lastCalendarsFetchMs = 0L
        nextEventCache.clear()
    }

    fun getDeviceCalendars(context: Context, forceRefresh: Boolean = false): List<DeviceCalendarInfo> {
        if (!hasCalendarPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCalendars.isNotEmpty() && (now - lastCalendarsFetchMs) < 60_000L) {
            return cachedCalendars
        }
        val list = ArrayList<DeviceCalendarInfo>()
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE
            )
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val colIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val acc = if (accIdx >= 0) cursor.getString(accIdx)?.trim().orEmpty() else ""
                    val color = if (colIdx >= 0) cursor.getInt(colIdx) else 0xFF38BDF8.toInt()
                    val label = name.ifBlank { acc.ifBlank { "Calendar #$id" } }
                    list.add(DeviceCalendarInfo(id = id, displayName = label, accountName = acc, color = color))
                }
            }
        } catch (_: Throwable) {}
        cachedCalendars = list
        lastCalendarsFetchMs = now
        return list
    }

    fun getNextUpcomingEvent(
        context: Context,
        selectedCalendarIds: Set<String>,
        forceRefresh: Boolean = false
    ): NextCalendarEvent? {
        if (!hasCalendarPermission(context)) return null
        val now = System.currentTimeMillis()
        val cacheKey = selectedCalendarIds.sorted().joinToString(",")
        if (!forceRefresh) {
            val cached = nextEventCache[cacheKey]
            if (cached != null && (now - cached.fetchedAtMs) < 60_000L) {
                val ev = cached.event
                if (ev == null || ev.startMs > now) return ev
            }
        }
        val calNamesById = getDeviceCalendars(context).associate { it.id to it.displayName }
        var best: NextCalendarEvent? = null
        var lastPrevEndMs = 0L
        try {
            val lookbackStart = now - 86_400_000L
            val endWindow = now + 365L * 86_400_000L
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, lookbackStart)
            ContentUris.appendId(builder, endWindow)
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.ALL_DAY
            )
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext()) {
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else continue
                    val calId = if (calIdIdx >= 0) cursor.getLong(calIdIdx) else -1L
                    if (selectedCalendarIds.isNotEmpty() && calId.toString() !in selectedCalendarIds) continue
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
                    if (begin <= now) {
                        if (end in lookbackStart..now && end > lastPrevEndMs) {
                            lastPrevEndMs = end
                        }
                        continue
                    }
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx)?.trim().orEmpty() else ""
                    if (title.isBlank()) continue
                    val isAllDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) != 0 else false
                    best = NextCalendarEvent(
                        title = title,
                        startMs = begin,
                        endMs = end,
                        calendarId = calId,
                        calendarName = calNamesById[calId] ?: "Calendar",
                        isAllDay = isAllDay,
                        prevEventEndMs = lastPrevEndMs
                    )
                    break
                }
            }
        } catch (_: Throwable) {}
        nextEventCache[cacheKey] = CachedEventEntry(best, now)
        return best
    }

    private fun slotPrefix(catId: String, slotIndex: Int): String =
        "${LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_SLOT_PREFIX}${catId}_${slotIndex}_"

    fun loadConfig(context: Context, catId: String, slotIndex: Int): CountdownConfig {
        val prefs = context.defaultPrefs()
        val pfx = slotPrefix(catId, slotIndex)
        val usePerWidget = prefs.getBoolean("${pfx}override", false)
        return if (usePerWidget) {
            CountdownConfig(
                usePerWidget = true,
                enabled = prefs.getBoolean("${pfx}enabled", false),
                source = prefs.getString("${pfx}source", SOURCE_MANUAL) ?: SOURCE_MANUAL,
                title = prefs.getString("${pfx}title", "TARGET")?.trim()?.ifBlank { "TARGET" } ?: "TARGET",
                targetMs = prefs.getLong("${pfx}target_ms", 0L),
                calendarIds = prefs.getStringSet("${pfx}calendar_ids", emptySet())?.toSet() ?: emptySet(),
                startMs = prefs.getLong("${pfx}start_ms", 0L)
            )
        } else {
            CountdownConfig(
                usePerWidget = false,
                enabled = prefs.getBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, false),
                source = prefs.getString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_SOURCE, SOURCE_MANUAL) ?: SOURCE_MANUAL,
                title = prefs.getString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, "TARGET")?.trim()?.ifBlank { "TARGET" } ?: "TARGET",
                targetMs = prefs.getLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, 0L),
                calendarIds = prefs.getStringSet(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_CALENDAR_IDS, emptySet())?.toSet() ?: emptySet(),
                startMs = prefs.getLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_START_MS, 0L)
            )
        }
    }

    fun saveConfig(context: Context, catId: String, slotIndex: Int, config: CountdownConfig) {
        val editor = context.defaultPrefs().edit()
        val pfx = slotPrefix(catId, slotIndex)
        editor.putBoolean("${pfx}override", config.usePerWidget)
        if (config.usePerWidget) {
            editor.putBoolean("${pfx}enabled", config.enabled)
                .putString("${pfx}source", config.source)
                .putString("${pfx}title", config.title)
                .putLong("${pfx}start_ms", config.startMs)
                .putLong("${pfx}target_ms", config.targetMs)
                .putStringSet("${pfx}calendar_ids", config.calendarIds.toSet())
        } else {
            editor.putBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, config.enabled)
                .putString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_SOURCE, config.source)
                .putString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, config.title)
                .putLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_START_MS, config.startMs)
                .putLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, config.targetMs)
                .putStringSet(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_CALENDAR_IDS, config.calendarIds.toSet())
        }
        editor.apply()
    }

    private fun startOfDayMs(nowMs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Resolves the active countdown (title, targetMs, progressFraction) to display on a Screen Time
     * HUD widget, supporting both Manual targets and live Next Calendar Event countdowns.
     */
    fun resolveDisplayCountdownDetails(context: Context, catId: String, slotIndex: Int): DisplayCountdown? {
        val cfg = loadConfig(context, catId, slotIndex)
        if (!cfg.enabled) return null
        val now = System.currentTimeMillis()
        return if (cfg.source == SOURCE_CALENDAR) {
            val ev = getNextUpcomingEvent(context, cfg.calendarIds) ?: return null
            if (ev.startMs <= now) return null
            val lookbackFloor = now - 86_400_000L
            val windowStartMs = if (ev.prevEventEndMs in lookbackFloor..now && ev.prevEventEndMs < ev.startMs) {
                ev.prevEventEndMs
            } else {
                val leadIn24h = ev.startMs - 86_400_000L
                val sod = startOfDayMs(now)
                if (now >= leadIn24h) maxOf(leadIn24h, sod) else sod
            }
            val denom = (ev.startMs - windowStartMs).coerceAtLeast(60_000L)
            val progress = ((now - windowStartMs).toFloat() / denom.toFloat()).coerceIn(0f, 1f)
            DisplayCountdown(title = ev.title, targetMs = ev.startMs, progressFraction = progress)
        } else {
            if (cfg.targetMs <= now) return null
            val windowStartMs = if (cfg.startMs in 1L until cfg.targetMs) {
                cfg.startMs
            } else {
                cfg.targetMs - 86_400_000L
            }
            val denom = (cfg.targetMs - windowStartMs).coerceAtLeast(60_000L)
            val progress = ((now - windowStartMs).toFloat() / denom.toFloat()).coerceIn(0f, 1f)
            DisplayCountdown(title = cfg.title, targetMs = cfg.targetMs, progressFraction = progress)
        }
    }

    /**
     * Backward-compatible pair resolver for callers expecting (title, targetMs).
     */
    fun resolveDisplayCountdown(context: Context, catId: String, slotIndex: Int): Pair<String, Long>? =
        resolveDisplayCountdownDetails(context, catId, slotIndex)?.let { it.title to it.targetMs }
}
