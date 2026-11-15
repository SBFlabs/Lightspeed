package com.sbf.lightspeed

import android.content.Context
import android.widget.Toast
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Tactical Telemetry & State Repository for Category Cruise Top HUD Strip / Widgets:
 * 1. empty             — Clean empty space, app grid expands upward
 * 2. app_and_cat       — Focused App Inspector: focused app telemetry, or "SECTOR 03 / 08" when nothing is focused
 *                        (legacy app_only / ls_privacy tokens are merged into this mode) [NIGHTLY inspector, stable strip]
 * 3. ls_screen_time    — Device/Sector/App Screen Time + Day/Week/Month/Year Timeline & Countdown [NIGHTLY]
 * 4. ls_battery_system — Battery / Top Apps / System Activity / Vitals gauges (real metrics only) [NIGHTLY]
 * 5. app_widget        — Live Interactive Android App Widget Slot (e.g. Duolingo, Calendar) [NIGHTLY]
 */
object LightspeedCruiseHudPods {

    const val MODE_EMPTY = "empty"
    const val MODE_APP_AND_CAT = "app_and_cat"
    const val MODE_APP_ONLY = "app_only"
    const val MODE_LS_SCREEN_TIME = "ls_screen_time"
    const val MODE_LS_BATTERY_SYSTEM = "ls_battery_system"
    const val MODE_LS_PRIVACY = "ls_privacy"
    const val MODE_ANDROID_WIDGET = "app_widget"

    val HUD_MODE_OPTIONS_ROW1: List<Pair<String, String>>
        get() = if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            listOf(
                MODE_EMPTY to "EMPTY",
                MODE_APP_AND_CAT to "FOCUS INSPECTOR",
                MODE_ANDROID_WIDGET to "APP WIDGET"
            )
        } else {
            listOf(
                MODE_EMPTY to "EMPTY",
                MODE_APP_AND_CAT to "APP + CAT"
            )
        }

    val HUD_MODE_OPTIONS_ROW2: List<Pair<String, String>>
        get() = if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            listOf(
                MODE_LS_SCREEN_TIME to "SCREEN TIME",
                MODE_LS_BATTERY_SYSTEM to "BATTERY & SYS"
            )
        } else {
            emptyList()
        }

    val ALL_HUD_MODE_OPTIONS: List<Pair<String, String>>
        get() = HUD_MODE_OPTIONS_ROW1 + HUD_MODE_OPTIONS_ROW2

    fun isExperimentalMode(mode: String): Boolean =
        mode == MODE_ANDROID_WIDGET ||
        mode == MODE_LS_SCREEN_TIME ||
        mode == MODE_LS_BATTERY_SYSTEM ||
        mode == MODE_LS_PRIVACY

    val TIME_SCOPE_OPTIONS = listOf(
        "sec" to "SECTOR",
        "dev" to "DEVICE"
    )

    val TIME_SPAN_OPTIONS = listOf(
        "1d" to "DAY",
        "7d" to "WK",
        "30d" to "MO",
        "365d" to "YR"
    )

    // Token "chart" is a persisted value and must never be renamed; it now renders the graph-free battery readout.
    val BATTERY_MODE_OPTIONS = listOf(
        "chart" to "BATTERY",
        "apps" to "APPS",
        "system" to "SYSTEM",
        "vitals" to "VITALS"
    )

    val BATTERY_SPAN_OPTIONS = listOf(
        "last_charge" to "LAST CHG",
        "today" to "TODAY",
        "7d" to "7-DAY"
    )

    val PRIVACY_VIEW_OPTIONS = listOf(
        "alerts" to "ALERTS",
        "firewall" to "FIREWALL",
        "score" to "SCORE"
    )

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    internal val usageCacheBySpan = ConcurrentHashMap<String, Map<String, Long>>()
    @Volatile internal var lastRefreshMs = 0L
    @Volatile var hasBatterySnapshot: Boolean = false
        internal set
    @Volatile internal var lastBatteryRefreshMs: Long = 0L
    @Volatile internal var isRefreshingBattery: Boolean = false
    internal val lastUsageRefreshMsBySpan = ConcurrentHashMap<String, Long>()
    internal val inFlightUsageSpans: MutableSet<String> = ConcurrentHashMap.newKeySet()

    data class ExternalStorageInfo(
        val label: String,          // e.g. "SD CARD", "USB OTG", "EXT DRIVE"
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long,
        val totalGb: Int,
        val usedGb: Int,
        val freeGb: Int
    )

    fun formatStorageSize(bytes: Long, fallbackGb: Int = 0): String {
        if (bytes <= 0L) {
            return if (fallbackGb > 0) "${fallbackGb}G" else "0G"
        }
        val tb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0 * 1024.0)
        if (tb >= 0.95) {
            return String.format(Locale.US, "%.1fT", tb)
        }
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 10.0) {
            "${gb.roundToInt()}G"
        } else {
            String.format(Locale.US, "%.1fG", gb)
        }
    }

    data class BatterySnapshot(
        val levelPct: Int = 0,
        val isCharging: Boolean = false,
        val wattageStr: String = "IDLE",
        val tempC: Int = 30,
        val voltageMv: Int = 3850,
        val usedRamGb: Double = 0.0,
        val totalRamGb: Double = 0.0,
        val ramPct: Int = 0,
        val freeIntGb: Int = 0,
        val totalIntGb: Int = 0,
        val usedIntGb: Int = 0,
        val totalIntBytes: Long = 0L,
        val usedIntBytes: Long = 0L,
        val freeIntBytes: Long = 0L,
        val freeSdGb: Int? = null,
        val externalStorage: ExternalStorageInfo? = null,
        val sinceChargeScrn: String = "--",
        val sevenDayDailyAvgHrs: Double = 0.0,
        val swapUsedBytes: Long = 0L,
        val swapTotalBytes: Long = 0L,
        val awakePct: Int = 0,          // share of time since boot the CPU was awake (uptime / elapsed realtime)
        val mobileBytes: Long = -1L,    // mobile data rx+tx since boot, -1 when unsupported
        val wifiBytes: Long = -1L,      // non-mobile rx+tx since boot, -1 when unsupported
        val mobileRxBytes: Long = -1L,
        val mobileTxBytes: Long = -1L,
        val wifiRxBytes: Long = -1L,
        val wifiTxBytes: Long = -1L,
        val screenTodayMs: Long = 0L,   // real foreground time from usage stats (last day)
        val chargeMah: Int = -1,        // current battery charge in mAh (-1 if unavailable)
        val designCapacityMah: Int = -1,// full design capacity in mAh (-1 if unavailable)
        val healthLabel: String = "GOOD",
        val cycleCount: Int = -1,
        val peakChargeWatts: Float = -1f,
        val minChargeWatts: Float = -1f,
        val chgPeakMinStr: String = "IDLE",
        val estRemainingStr: String = "--"
    )

    /** Real manifest & grant-state privacy profile of an app. */
    data class AppPrivacyProfile(
        val score: Int,
        val hasInternet: Boolean,
        val flags: List<String>,
        val grantedCount: Int = 0,
        val requestedSensitiveCount: Int = 0,
        val isFirewallEnforced: Boolean = false
    ) {
        val summary: String get() = if (flags.isEmpty()) "100% OFFLINE" else flags.take(4).joinToString("+")
    }

    /** Everything the Focused App Inspector shows about one app. */
    data class FocusedAppTelemetry(
        val packageName: String,
        val label: String,
        val profile: AppPrivacyProfile,
        val screenTimeTodayMs: Long?,   // null when usage access is not granted
        val alertCount: Int,
        val firewallBlocked: Boolean
    )

    @Volatile var latestBatterySnapshot: BatterySnapshot = BatterySnapshot()
        internal set

    @Volatile internal var wasChargingSession: Boolean = false
    @Volatile internal var sessionPeakChargeWatts: Float = -1f
    @Volatile internal var sessionMinChargeWatts: Float = -1f
    @Volatile internal var sessionPeakDischargeWatts: Float = -1f
    @Volatile internal var cachedDesignCapacityMah: Int = -1

    @Volatile var onSnapshotUpdated: (() -> Unit)? = null

    // Telemetry Refresh & Entrance State (immediate truthful readout without fake digit-scrambling)
    @Volatile var isRefreshingTelemetry: Boolean = false
        internal set
    @Volatile private var lastHudCategoryId: String = ""

    fun notifyHudEnteredView() {
        // Intentional no-op: telemetry snaps directly to real values on arrival.
    }

    fun resetHudEntranceState() {
        lastHudCategoryId = ""
        onSnapshotUpdated = null
    }

    fun onHudFrameDrawn(categoryId: String) {
        if (categoryId != lastHudCategoryId) {
            lastHudCategoryId = categoryId
        }
    }

    fun isDigitFlipActive(): Boolean = false

    fun flipDigitsIfActive(text: String, seedOffset: Int = 0): String = text

    fun scrambleDigits(text: String, seedOffset: Int = 0): String = text

    /** Maps legacy item types (app_only, ls_privacy) onto the unified Focused App Inspector type. */
    fun canonicalHudType(raw: String): String = when (raw) {
        MODE_APP_ONLY, MODE_LS_PRIVACY -> MODE_APP_AND_CAT
        else -> raw
    }

    fun normalizeHudMode(raw: String?): String {
        return when (raw) {
            MODE_EMPTY, "empty" -> MODE_EMPTY
            MODE_APP_AND_CAT, "focused_app", "app_and_cat" -> MODE_APP_AND_CAT
            MODE_APP_ONLY, "app_only" -> MODE_APP_AND_CAT
            MODE_LS_SCREEN_TIME, "pod_screen_time" -> MODE_LS_SCREEN_TIME
            MODE_LS_BATTERY_SYSTEM, "pod_system_vitals" -> MODE_LS_BATTERY_SYSTEM
            MODE_LS_PRIVACY, "pod_privacy_shield" -> MODE_APP_AND_CAT
            MODE_ANDROID_WIDGET, "android_widget", "widget" -> MODE_ANDROID_WIDGET
            else -> MODE_APP_AND_CAT
        }
    }

    fun defaultHudModeForCategory(categoryId: String): String {
        if (!NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            return MODE_APP_AND_CAT
        }
        return when (categoryId) {
            "games", "productivity" -> MODE_LS_SCREEN_TIME
            "settings", "utilities" -> MODE_LS_BATTERY_SYSTEM
            else -> MODE_APP_AND_CAT
        }
    }

    fun getCategoryHudMode(context: Context, categoryId: String): String {
        val saved = context.defaultPrefs().getString(
            LightspeedPreferences.KEY_CATEGORY_HUD_MODE_PREFIX + categoryId,
            null
        )
        val mode = if (!saved.isNullOrBlank()) normalizeHudMode(saved) else defaultHudModeForCategory(categoryId)
        return if (!NightlyFeatures.isCategoryCruiseHudWidgetsEnabled && isExperimentalMode(mode)) {
            MODE_APP_AND_CAT
        } else {
            mode
        }
    }

    fun setCategoryHudMode(context: Context, categoryId: String, mode: String) {
        context.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_CATEGORY_HUD_MODE_PREFIX + categoryId, mode)
            .apply()
        refreshTelemetryAsync(context, force = true)
    }

    fun getCategoryHudSlot2Mode(context: Context, categoryId: String): String {
        val saved = context.defaultPrefs().getString(
            LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_MODE_PREFIX + categoryId,
            null
        )
        val mode = if (!saved.isNullOrBlank()) normalizeHudMode(saved) else MODE_EMPTY
        return if (!NightlyFeatures.isCategoryCruiseHudWidgetsEnabled && isExperimentalMode(mode)) {
            MODE_EMPTY
        } else {
            mode
        }
    }

    fun setCategoryHudSlot2Mode(context: Context, categoryId: String, mode: String) {
        context.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_MODE_PREFIX + categoryId, mode)
            .apply()
        refreshTelemetryAsync(context, force = true)
    }

    /**
     * Resolves a per-category, per-slot preference key so slots 1, 2, and 3+
     * stay isolated from one another without altering slot 1/2 key formats.
     */
    private fun resolveSlotPrefKey(
        slot1Prefix: String,
        slot2Prefix: String,
        categoryId: String,
        slotIndex: Int
    ): String = when {
        slotIndex <= 1 -> slot1Prefix + categoryId
        slotIndex == 2 -> slot2Prefix + categoryId
        else -> "${slot1Prefix}slot${slotIndex}_$categoryId"
    }

    private fun readSlotStringPref(
        context: Context,
        categoryId: String?,
        slotIndex: Int,
        slot1Prefix: String,
        slot2Prefix: String,
        globalKey: String,
        defaultValue: String
    ): String {
        val prefs = context.defaultPrefs()
        if (!categoryId.isNullOrBlank()) {
            val slotKey = resolveSlotPrefKey(slot1Prefix, slot2Prefix, categoryId, slotIndex)
            if (prefs.contains(slotKey)) {
                return prefs.getString(slotKey, defaultValue) ?: defaultValue
            }
        }
        return prefs.getString(globalKey, defaultValue) ?: defaultValue
    }

    private fun writeSlotStringPref(
        context: Context,
        categoryId: String?,
        slotIndex: Int,
        slot1Prefix: String,
        slot2Prefix: String,
        globalKey: String,
        value: String
    ) {
        val targetKey = if (!categoryId.isNullOrBlank()) {
            resolveSlotPrefKey(slot1Prefix, slot2Prefix, categoryId, slotIndex)
        } else {
            globalKey
        }
        context.defaultPrefs().edit().putString(targetKey, value).apply()
    }

    fun getTimeScope(context: Context, categoryId: String? = null, slotIndex: Int = 1): String =
        readSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SCOPE_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_TIME_SCOPE_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SCOPE,
            defaultValue = "dev"
        )

    fun setTimeScope(context: Context, categoryId: String?, scope: String, slotIndex: Int = 1) {
        writeSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SCOPE_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_TIME_SCOPE_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SCOPE,
            value = scope
        )
    }

    fun cycleTimeScope(context: Context, categoryId: String? = null, slotIndex: Int = 1): String {
        val current = getTimeScope(context, categoryId, slotIndex)
        val idx = TIME_SCOPE_OPTIONS.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
        val next = TIME_SCOPE_OPTIONS[(idx + 1) % TIME_SCOPE_OPTIONS.size].first
        setTimeScope(context, categoryId, next, slotIndex)
        notifyHudEnteredView()
        return next
    }

    fun getTimeSpan(context: Context, categoryId: String? = null, slotIndex: Int = 1): String =
        readSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SPAN_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_TIME_SPAN_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SPAN,
            defaultValue = "1d"
        )

    fun setTimeSpan(context: Context, categoryId: String?, span: String, slotIndex: Int = 1) {
        val editor = context.defaultPrefs().edit()
        if (!categoryId.isNullOrBlank()) {
            editor.putString(
                resolveSlotPrefKey(
                    LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SPAN_PREFIX,
                    LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_TIME_SPAN_PREFIX,
                    categoryId,
                    slotIndex
                ),
                span
            )
        } else {
            editor.putString(LightspeedPreferences.KEY_CATEGORY_HUD_TIME_SPAN, span)
        }
        editor.apply()
        refreshTelemetryAsync(
            context = context,
            force = true,
            activeCategoryId = categoryId,
            activeModes = setOf(MODE_LS_SCREEN_TIME)
        )
    }

    fun cycleTimeSpan(context: Context, categoryId: String? = null, slotIndex: Int = 1): String {
        val current = getTimeSpan(context, categoryId, slotIndex)
        val idx = TIME_SPAN_OPTIONS.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
        val next = TIME_SPAN_OPTIONS[(idx + 1) % TIME_SPAN_OPTIONS.size].first
        setTimeSpan(context, categoryId, next, slotIndex)
        notifyHudEnteredView()
        return next
    }

    fun getBatteryMode(context: Context, categoryId: String? = null, slotIndex: Int = 1): String =
        readSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_MODE_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_BATTERY_MODE_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_MODE,
            defaultValue = "chart"
        )

    fun setBatteryMode(context: Context, categoryId: String?, mode: String, slotIndex: Int = 1) {
        val editor = context.defaultPrefs().edit()
        if (!categoryId.isNullOrBlank()) {
            editor.putString(
                resolveSlotPrefKey(
                    LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_MODE_PREFIX,
                    LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_BATTERY_MODE_PREFIX,
                    categoryId,
                    slotIndex
                ),
                mode
            )
        } else {
            editor.putString(LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_MODE, mode)
        }
        editor.apply()
    }

    fun cycleBatteryMode(context: Context, categoryId: String? = null, slotIndex: Int = 1): String {
        val current = getBatteryMode(context, categoryId, slotIndex)
        val idx = BATTERY_MODE_OPTIONS.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
        val next = BATTERY_MODE_OPTIONS[(idx + 1) % BATTERY_MODE_OPTIONS.size].first
        setBatteryMode(context, categoryId, next, slotIndex)
        notifyHudEnteredView()
        refreshTelemetryAsync(
            context = context,
            force = true,
            activeCategoryId = categoryId,
            activeModes = setOf(MODE_LS_BATTERY_SYSTEM)
        )
        return next
    }

    fun getBatterySpan(context: Context, categoryId: String? = null, slotIndex: Int = 1): String =
        readSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_SPAN_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_BATTERY_SPAN_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_SPAN,
            defaultValue = "7d"
        )

    fun setBatterySpan(context: Context, categoryId: String?, span: String, slotIndex: Int = 1) {
        val editor = context.defaultPrefs().edit()
        if (!categoryId.isNullOrBlank()) {
            editor.putString(
                resolveSlotPrefKey(
                    LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_SPAN_PREFIX,
                    LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_BATTERY_SPAN_PREFIX,
                    categoryId,
                    slotIndex
                ),
                span
            )
        } else {
            editor.putString(LightspeedPreferences.KEY_CATEGORY_HUD_BATTERY_SPAN, span)
        }
        editor.apply()
        refreshTelemetryAsync(
            context = context,
            force = true,
            activeCategoryId = categoryId,
            activeModes = setOf(MODE_LS_BATTERY_SYSTEM)
        )
    }

    fun cycleBatterySpan(context: Context, categoryId: String? = null, slotIndex: Int = 1): String {
        val current = getBatterySpan(context, categoryId, slotIndex)
        val idx = BATTERY_SPAN_OPTIONS.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
        val next = BATTERY_SPAN_OPTIONS[(idx + 1) % BATTERY_SPAN_OPTIONS.size].first
        setBatterySpan(context, categoryId, next, slotIndex)
        notifyHudEnteredView()
        return next
    }

    fun getPrivacyView(context: Context, categoryId: String? = null, slotIndex: Int = 1): String =
        readSlotStringPref(
            context = context,
            categoryId = categoryId,
            slotIndex = slotIndex,
            slot1Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_PRIVACY_VIEW_PREFIX,
            slot2Prefix = LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_PRIVACY_VIEW_PREFIX,
            globalKey = LightspeedPreferences.KEY_CATEGORY_HUD_PRIVACY_VIEW,
            defaultValue = "alerts"
        )

    fun setPrivacyView(context: Context, categoryId: String?, view: String, slotIndex: Int = 1) {
        val editor = context.defaultPrefs().edit()
        if (!categoryId.isNullOrBlank()) {
            editor.putString(
                resolveSlotPrefKey(
                    LightspeedPreferences.KEY_CATEGORY_HUD_PRIVACY_VIEW_PREFIX,
                    LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_PRIVACY_VIEW_PREFIX,
                    categoryId,
                    slotIndex
                ),
                view
            )
        } else {
            editor.putString(LightspeedPreferences.KEY_CATEGORY_HUD_PRIVACY_VIEW, view)
        }
        editor.apply()
    }

    fun cyclePrivacyView(context: Context, categoryId: String? = null, slotIndex: Int = 1): String {
        val current = getPrivacyView(context, categoryId, slotIndex)
        val idx = PRIVACY_VIEW_OPTIONS.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
        val next = PRIVACY_VIEW_OPTIONS[(idx + 1) % PRIVACY_VIEW_OPTIONS.size].first
        setPrivacyView(context, categoryId, next, slotIndex)
        return next
    }

    /** LEGACY (read-only): old per-category visibility flag, used only to seed per-widget scope when migrating tokens. */
    fun isHudInsideCategoryOnly(context: Context, categoryId: String? = null): Boolean {
        val prefs = context.defaultPrefs()
        if (categoryId != null && prefs.contains(LightspeedPreferences.KEY_CATEGORY_HUD_INSIDE_ONLY_PREFIX + categoryId)) {
            return prefs.getBoolean(LightspeedPreferences.KEY_CATEGORY_HUD_INSIDE_ONLY_PREFIX + categoryId, false)
        }
        return prefs.getBoolean(LightspeedPreferences.KEY_HUD_INSIDE_CATEGORY_ONLY, false)
    }

    fun isAppFirewallBlocked(context: Context, packageName: String): Boolean {
        val blocked = context.defaultPrefs()
            .getStringSet(LightspeedPreferences.KEY_FIREWALL_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        return packageName in blocked
    }

    fun toggleAppFirewall(context: Context, packageName: String, appLabel: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val prefs = context.defaultPrefs()
        val current = prefs.getStringSet(LightspeedPreferences.KEY_FIREWALL_BLOCKED_PACKAGES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        val nowBlocked = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        prefs.edit().putStringSet(LightspeedPreferences.KEY_FIREWALL_BLOCKED_PACKAGES, current).apply()
        LightspeedPrivacyProfiler.invalidate(packageName)

        if (ElevatedTaskCloser.isShizukuActive) {
            scope.launch {
                val enableFlag = if (nowBlocked) "false" else "true"
                ElevatedTaskCloser.execShizuku("cmd connectivity set-package-networking-enabled $enableFlag $packageName")
            }
            val stateLabel = if (nowBlocked) "BLOCKED" else "ALLOWED"
            Toast.makeText(context, "Firewall [$stateLabel]: $appLabel", Toast.LENGTH_SHORT).show()
        } else {
            val stateLabel = if (nowBlocked) "MARKED BLOCKED (Start Shizuku to enforce)" else "ALLOWED"
            Toast.makeText(context, "Firewall [$stateLabel]: $appLabel", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshTelemetryAsync(
        context: Context,
        force: Boolean = false,
        activeCategoryId: String? = null,
        activeModes: Set<String>? = null
    ) {
        LightspeedCruiseHudTelemetryPoller.refreshTelemetryAsync(
            context = context,
            force = force,
            activeCategoryId = activeCategoryId,
            activeModes = activeModes
        )
    }

    /** Granted-state permission & network privacy profile for [packageName] (short TTL cached). */
    fun getAppPrivacyProfile(context: Context, packageName: String): AppPrivacyProfile =
        LightspeedPrivacyProfiler.getProfile(context, packageName)

    fun isTempFahrenheit(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_VITALS_TEMP_UNIT_FAHRENHEIT, false)

    fun toggleTempUnit(context: Context): Boolean {
        val next = !isTempFahrenheit(context)
        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_VITALS_TEMP_UNIT_FAHRENHEIT, next)
            .apply()
        return next
    }

    fun formatTemp(context: Context, tempC: Int): String {
        return if (isTempFahrenheit(context)) {
            val tempF = ((tempC * 9f / 5f) + 32f).roundToInt()
            "${tempF}°F"
        } else {
            "${tempC}°C"
        }
    }

    /** Real telemetry for the Focused App Inspector and the long-press menu header. */
    fun focusedAppTelemetry(context: Context, packageName: String, label: String): FocusedAppTelemetry {
        LightspeedNotificationListener.ensureAlertsLoaded(context)
        val usage = usageCacheBySpan["1d"]
        val hasAccess = LightspeedUsageStats.hasUsageAccess(context)
        return FocusedAppTelemetry(
            packageName = packageName,
            label = label,
            profile = getAppPrivacyProfile(context, packageName),
            screenTimeTodayMs = if (hasAccess && usage != null) (usage[packageName] ?: 0L) else null,
            alertCount = LightspeedNotificationListener.recentAlerts.filter { it.packageName == packageName }.sumOf { it.count },
            firewallBlocked = isAppFirewallBlocked(context, packageName)
        )
    }

    fun formatDurationCompact(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
    }

    fun getUsageMap(span: String): Map<String, Long> = usageCacheBySpan[span] ?: emptyMap()
}
