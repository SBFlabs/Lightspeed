package com.sbf.lightspeed.system

import com.sbf.lightspeed.R

/**
 * LightspeedVocabulary — the master terminology dictionary for the Lightspeed app.
 *
 * Every user-facing concept in the app has two canonical names:
 *  • Vessel  — the spaceship metaphor term the app is built around
 *  • Clear   — plain Android/English equivalent for accessibility and onboarding
 *
 * The [LightspeedLanguageEngine] resolves between these based on the user's
 * chosen [LightspeedLanguageEngine.LanguageMode]. Future real-language locales
 * (Arabic, Chinese, etc.) will be added as additional lookup tables here.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * HOW TO ADD A NEW TERM:
 *  1. Add an entry to [Key]
 *  2. Add both vessel + clear entries in [vesselMap] and [clearMap] below
 *  3. Use LightspeedLanguageEngine.resolve(Key.YOUR_KEY) at the call site
 * ────────────────────────────────────────────────────────────────────────────
 */
object LightspeedVocabulary {

    // ─── Vocabulary keys ────────────────────────────────────────────────────

    enum class Key {
        // Navigation & top-level structure
        CENTRAL_COMMAND,
        CRUISE,
        COCKPIT,
        HUD_STRIP,
        TAB_LEFT,
        TAB_CENTER,
        TAB_RIGHT,

        // Gesture zones
        DEFLECTORS,
        LEFT_DEFLECTOR,
        RIGHT_DEFLECTOR,
        SENSOR_AREA,
        UNIFIED_DEFLECTORS,
        CORE_ZONE,
        UPPER_FLANK,
        LOWER_FLANK,

        // Status bar components
        HORIZON_RAIL,
        ORBITAL_CAPSULE,
        SCREEN_ORIENTATION,
        SYSTEM_ACTIONS,
        HARDWARE_CONTROLS,
        GESTURE_SCRUBBERS,
        NAVIGATION,
        MEDIA_CONTROLS,
        SHIP_MAINTENANCE,
        ANDROID_NATIVE,

        // Hardware inputs
        SUB_LIGHT_THRUSTERS,       // Volume buttons
        HULL_RESONATOR,            // Back tap
        IGNITION_OVERRIDE,         // Power button remapping

        // System features
        REFUELING_BAY,             // Charging screen / DreamService
        SHIP_DATA_VAULT,           // Backup & restore
        SHIZUKU_JETTISON,          // Task killer
        WATCHDOG_SENTINELS,        // Accessibility / crash watchdogs
        PERIMETER_DEFENSE,         // Manager / protected services
        CORE_WATCHDOG,             // Flight Deck Watchdogs & System Immunity
        GRAVITY_ENGINE,            // Synthetic gravity / physics profiles
        CORE_COOLING,              // Thermal schedule
        BATTERY_EXEMPTION,         // Universal battery exemption control deck
        SONIC_DECK,                // Multi-app sound mixer & sovereign focus manager
        VISTA_DECK,                // Display wellness, brightness & visual optics deck

        // Flight control
        MASTER_FLIGHT,
        FLIGHT_DECK,
        SYSTEM_OVERRIDES,

        EXPERIMENTAL_LABS,

        // Settings structure
        GUIDEBOOK,
        TELEMETRY_AND_INDICATORS,
        TACTICAL_HARDWARE,

        POWER_GRAB_WARNING,
        VAULT_IMPORT_WARNING,
        FLIGHT_BLACKBOX,

        // Call Deflector Mode
        CALL_DEFLECTOR_MODE_TITLE,
        CALL_DEFLECTOR_MODE_KEEP_ACTIVE,
        CALL_DEFLECTOR_MODE_HIDE,
    }

    // ─── Vessel Lore strings (res/values*/strings.xml) ───

    private val vesselRes: Map<Key, Int> = mapOf(
        Key.CENTRAL_COMMAND to R.string.vocab_vessel_central_command,
        Key.CRUISE to R.string.vocab_vessel_cruise,
        Key.COCKPIT to R.string.vocab_vessel_cockpit,
        Key.HUD_STRIP to R.string.vocab_vessel_hud_strip,
        Key.TAB_LEFT to R.string.vocab_vessel_tab_left,
        Key.TAB_CENTER to R.string.vocab_vessel_tab_center,
        Key.TAB_RIGHT to R.string.vocab_vessel_tab_right,
        Key.DEFLECTORS to R.string.vocab_vessel_deflectors,
        Key.LEFT_DEFLECTOR to R.string.vocab_vessel_left_deflector,
        Key.RIGHT_DEFLECTOR to R.string.vocab_vessel_right_deflector,
        Key.SENSOR_AREA to R.string.vocab_vessel_sensor_area,
        Key.UNIFIED_DEFLECTORS to R.string.vocab_vessel_unified_deflectors,
        Key.CORE_ZONE to R.string.vocab_vessel_core_zone,
        Key.UPPER_FLANK to R.string.vocab_vessel_upper_flank,
        Key.LOWER_FLANK to R.string.vocab_vessel_lower_flank,
        Key.HORIZON_RAIL to R.string.vocab_vessel_horizon_rail,
        Key.ORBITAL_CAPSULE to R.string.vocab_vessel_orbital_capsule,
        Key.SCREEN_ORIENTATION to R.string.vocab_vessel_screen_orientation,
        Key.SYSTEM_ACTIONS to R.string.vocab_vessel_system_actions,
        Key.HARDWARE_CONTROLS to R.string.vocab_vessel_hardware_controls,
        Key.GESTURE_SCRUBBERS to R.string.vocab_vessel_gesture_scrubbers,
        Key.NAVIGATION to R.string.vocab_vessel_navigation,
        Key.MEDIA_CONTROLS to R.string.vocab_vessel_media_controls,
        Key.SHIP_MAINTENANCE to R.string.vocab_vessel_ship_maintenance,
        Key.ANDROID_NATIVE to R.string.sub_android_native,
        Key.SUB_LIGHT_THRUSTERS to R.string.vocab_vessel_sub_light_thrusters,
        Key.HULL_RESONATOR to R.string.vocab_vessel_hull_resonator,
        Key.IGNITION_OVERRIDE to R.string.vocab_vessel_ignition_override,
        Key.REFUELING_BAY to R.string.vocab_vessel_refueling_bay,
        Key.SHIP_DATA_VAULT to R.string.vocab_vessel_ship_data_vault,
        Key.SHIZUKU_JETTISON to R.string.vocab_vessel_shizuku_jettison,
        Key.WATCHDOG_SENTINELS to R.string.vocab_vessel_watchdog_sentinels,
        Key.PERIMETER_DEFENSE to R.string.vocab_vessel_perimeter_defense,
        Key.CORE_WATCHDOG to R.string.vocab_vessel_core_watchdog,
        Key.GRAVITY_ENGINE to R.string.vocab_vessel_gravity_engine,
        Key.CORE_COOLING to R.string.vocab_vessel_core_cooling,
        Key.BATTERY_EXEMPTION to R.string.vocab_vessel_battery_exemption,
        Key.SONIC_DECK to R.string.vocab_vessel_sonic_deck,
        Key.VISTA_DECK to R.string.vocab_vessel_vista_deck,
        Key.SYSTEM_OVERRIDES to R.string.vocab_vessel_system_overrides,
        Key.MASTER_FLIGHT to R.string.vocab_vessel_master_flight,
        Key.FLIGHT_DECK to R.string.vocab_vessel_flight_deck,
        Key.EXPERIMENTAL_LABS to R.string.vocab_vessel_experimental_labs,
        Key.GUIDEBOOK to R.string.vocab_vessel_guidebook,
        Key.TELEMETRY_AND_INDICATORS to R.string.vocab_vessel_telemetry_and_indicators,
        Key.TACTICAL_HARDWARE to R.string.vocab_vessel_tactical_hardware,
        Key.POWER_GRAB_WARNING to R.string.vocab_vessel_power_grab_warning,
        Key.VAULT_IMPORT_WARNING to R.string.vocab_vessel_vault_import_warning,
        Key.FLIGHT_BLACKBOX to R.string.vocab_vessel_flight_blackbox,
        Key.CALL_DEFLECTOR_MODE_TITLE to R.string.vocab_vessel_call_deflector_mode_title,
        Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE to R.string.vocab_vessel_call_deflector_mode_keep_active,
        Key.CALL_DEFLECTOR_MODE_HIDE to R.string.vocab_vessel_call_deflector_mode_hide,
    )

    // ─── Clear Comms strings ───

    private val clearRes: Map<Key, Int> = mapOf(
        Key.CENTRAL_COMMAND to R.string.vocab_clear_central_command,
        Key.CRUISE to R.string.vocab_clear_cruise,
        Key.COCKPIT to R.string.vocab_clear_cockpit,
        Key.HUD_STRIP to R.string.vocab_clear_hud_strip,
        Key.TAB_LEFT to R.string.vocab_clear_tab_left,
        Key.TAB_CENTER to R.string.vocab_clear_tab_center,
        Key.TAB_RIGHT to R.string.vocab_clear_tab_right,
        Key.DEFLECTORS to R.string.vocab_clear_deflectors,
        Key.LEFT_DEFLECTOR to R.string.vocab_clear_left_deflector,
        Key.RIGHT_DEFLECTOR to R.string.vocab_clear_right_deflector,
        Key.SENSOR_AREA to R.string.vocab_clear_sensor_area,
        Key.UNIFIED_DEFLECTORS to R.string.vocab_clear_unified_deflectors,
        Key.CORE_ZONE to R.string.vocab_clear_core_zone,
        Key.UPPER_FLANK to R.string.vocab_clear_upper_flank,
        Key.LOWER_FLANK to R.string.vocab_clear_lower_flank,
        Key.HORIZON_RAIL to R.string.vocab_clear_horizon_rail,
        Key.ORBITAL_CAPSULE to R.string.vocab_clear_orbital_capsule,
        Key.SCREEN_ORIENTATION to R.string.vocab_clear_screen_orientation,
        Key.SYSTEM_ACTIONS to R.string.vocab_clear_system_actions,
        Key.HARDWARE_CONTROLS to R.string.vocab_clear_hardware_controls,
        Key.GESTURE_SCRUBBERS to R.string.vocab_clear_gesture_scrubbers,
        Key.NAVIGATION to R.string.vocab_clear_navigation,
        Key.MEDIA_CONTROLS to R.string.vocab_clear_media_controls,
        Key.SHIP_MAINTENANCE to R.string.vocab_clear_ship_maintenance,
        Key.ANDROID_NATIVE to R.string.sub_android_native,
        Key.SUB_LIGHT_THRUSTERS to R.string.vocab_clear_sub_light_thrusters,
        Key.HULL_RESONATOR to R.string.vocab_clear_hull_resonator,
        Key.IGNITION_OVERRIDE to R.string.vocab_clear_ignition_override,
        Key.REFUELING_BAY to R.string.vocab_clear_refueling_bay,
        Key.SHIP_DATA_VAULT to R.string.vocab_clear_ship_data_vault,
        Key.SHIZUKU_JETTISON to R.string.vocab_clear_shizuku_jettison,
        Key.WATCHDOG_SENTINELS to R.string.vocab_clear_watchdog_sentinels,
        Key.PERIMETER_DEFENSE to R.string.vocab_clear_perimeter_defense,
        Key.CORE_WATCHDOG to R.string.vocab_clear_core_watchdog,
        Key.GRAVITY_ENGINE to R.string.vocab_clear_gravity_engine,
        Key.SYSTEM_OVERRIDES to R.string.vocab_clear_system_overrides,
        Key.BATTERY_EXEMPTION to R.string.vocab_clear_battery_exemption,
        Key.SONIC_DECK to R.string.vocab_clear_sonic_deck,
        Key.VISTA_DECK to R.string.vocab_clear_vista_deck,
        Key.CORE_COOLING to R.string.vocab_clear_core_cooling,
        Key.MASTER_FLIGHT to R.string.vocab_clear_master_flight,
        Key.FLIGHT_DECK to R.string.vocab_clear_flight_deck,
        Key.EXPERIMENTAL_LABS to R.string.vocab_clear_experimental_labs,
        Key.GUIDEBOOK to R.string.vocab_clear_guidebook,
        Key.TELEMETRY_AND_INDICATORS to R.string.vocab_clear_telemetry_and_indicators,
        Key.TACTICAL_HARDWARE to R.string.vocab_clear_tactical_hardware,
        Key.POWER_GRAB_WARNING to R.string.vocab_clear_power_grab_warning,
        Key.VAULT_IMPORT_WARNING to R.string.vocab_clear_vault_import_warning,
        Key.FLIGHT_BLACKBOX to R.string.vocab_clear_flight_blackbox,
        Key.CALL_DEFLECTOR_MODE_TITLE to R.string.vocab_clear_call_deflector_mode_title,
        Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE to R.string.vocab_clear_call_deflector_mode_keep_active,
        Key.CALL_DEFLECTOR_MODE_HIDE to R.string.vocab_clear_call_deflector_mode_hide,
    )

    // ─── Co-Pilot (Bilingual) subtitle strings ───

    private val copilotSubtitleRes: Map<Key, Int> = mapOf(
        Key.TELEMETRY_AND_INDICATORS to R.string.vocab_sub_telemetry_and_indicators,
        Key.REFUELING_BAY to R.string.vocab_sub_refueling_bay,
        Key.POWER_GRAB_WARNING to R.string.vocab_sub_power_grab_warning,
    )

    // ─── Lookup functions ───────────────────────────────────────────────────
    // Every lookup resolves through [lsString] so the active real-world language (EN / PT / ES)
    // applies, and so [LightspeedLocaleEngine.secondaryFor] can find the Bilingual twin.

    private fun text(id: Int?): String? = id?.let { lsString(it) }?.takeIf { it.isNotEmpty() }

    /**
     * Returns the spaceship-metaphor label for [key].
     * Falls back to the key name if somehow unmapped (should never happen).
     */
    fun vessel(key: Key): String = text(vesselRes[key]) ?: key.name

    /**
     * Returns the plain Android label for [key].
     * Falls back to [vessel] if not found.
     */
    fun clear(key: Key): String = text(clearRes[key]) ?: vessel(key)

    /**
     * Returns the plain-language subtitle label for [key] in Co-Pilot mode.
     */
    fun copilotSubtitle(key: Key): String = text(copilotSubtitleRes[key]) ?: clear(key)
}
