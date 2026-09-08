package com.sbf.lightspeed.system

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

        // Gesture zones
        DEFLECTORS,
        LEFT_DEFLECTOR,
        RIGHT_DEFLECTOR,
        SENSOR_AREA,

        // Status bar components
        HORIZON_RAIL,
        ORBITAL_CAPSULE,

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
        GRAVITY_ENGINE,            // Synthetic gravity / physics profiles
        CORE_COOLING,              // Thermal schedule

        // Flight control
        MASTER_FLIGHT,
        FLIGHT_DECK,
        EXPERIMENTAL_LABS,

        // Settings structure
        GUIDEBOOK,
        TELEMETRY_AND_INDICATORS,
        TACTICAL_HARDWARE,
    }

    // ─── Vessel Lore dictionary ─────────────────────────────────────────────

    private val vesselMap: Map<Key, String> = mapOf(
        Key.CENTRAL_COMMAND         to "Central Command",
        Key.CRUISE                  to "Cruise",
        Key.COCKPIT                 to "Cockpit",
        Key.HUD_STRIP               to "HUD Strip",

        Key.DEFLECTORS              to "Left & Right Deflectors",
        Key.LEFT_DEFLECTOR          to "Port Deflector",
        Key.RIGHT_DEFLECTOR         to "Starboard Deflector",
        Key.SENSOR_AREA             to "Sensor Area",

        Key.HORIZON_RAIL            to "Horizon Rail",
        Key.ORBITAL_CAPSULE         to "Orbital Capsule",

        Key.SUB_LIGHT_THRUSTERS     to "Sub-Light Impulse Thrusters",
        Key.HULL_RESONATOR          to "Hull Kinetic Acoustic Resonator",
        Key.IGNITION_OVERRIDE       to "Ignition Override",

        Key.REFUELING_BAY           to "Refueling Bay & Cryo Stasis",
        Key.SHIP_DATA_VAULT         to "Config Vault",
        Key.SHIZUKU_JETTISON        to "Emergency Shizuku Jettison",
        Key.WATCHDOG_SENTINELS      to "Watchdog Sentinels",
        Key.PERIMETER_DEFENSE       to "Perimeter Defense",
        Key.GRAVITY_ENGINE          to "Synthetic Gravity Engine",
        Key.CORE_COOLING            to "Core Cooling Schedule",

        Key.MASTER_FLIGHT           to "Master Flight",
        Key.FLIGHT_DECK             to "Flight Control Deck",
        Key.EXPERIMENTAL_LABS       to "Experimental Labs",

        Key.GUIDEBOOK               to "The Stranded in Space Guidebook",
        Key.TELEMETRY_AND_INDICATORS to "Info Beacons",
        Key.TACTICAL_HARDWARE       to "Hull & Ship Maneuvers",
    )

    // ─── Clear Comms dictionary ──────────────────────────────────────────────

    private val clearMap: Map<Key, String> = mapOf(
        Key.CENTRAL_COMMAND         to "Settings",
        Key.CRUISE                  to "Navigation",
        Key.COCKPIT                 to "Launcher Settings",
        Key.HUD_STRIP               to "Status Bar Features",

        Key.DEFLECTORS              to "Left & Right Gesture Sidebars",
        Key.LEFT_DEFLECTOR          to "Left Gesture Sidebar",
        Key.RIGHT_DEFLECTOR         to "Right Gesture Sidebar",
        Key.SENSOR_AREA             to "Touch Strip",

        Key.HORIZON_RAIL            to "Progress Rail",
        Key.ORBITAL_CAPSULE         to "Camera Cutout HUD",

        Key.SUB_LIGHT_THRUSTERS     to "Volume Button Actions",
        Key.HULL_RESONATOR          to "Back-Tap Gestures",
        Key.IGNITION_OVERRIDE       to "Power Button Remapping",

        Key.REFUELING_BAY           to "Charging Screen",
        Key.SHIP_DATA_VAULT         to "Backup & Restore",
        Key.SHIZUKU_JETTISON        to "Task Closer (Shizuku)",
        Key.WATCHDOG_SENTINELS      to "Watchdog & Crash Guard",
        Key.PERIMETER_DEFENSE       to "Accessibility Service Manager",
        Key.GRAVITY_ENGINE          to "Orientation Preferences",
        Key.CORE_COOLING            to "Thermal Schedule",

        Key.MASTER_FLIGHT           to "Master Toggle",
        Key.FLIGHT_DECK             to "Quick Controls",
        Key.EXPERIMENTAL_LABS       to "Experimental Features",

        Key.GUIDEBOOK               to "Feature Dictionary",
        Key.TELEMETRY_AND_INDICATORS to "Telemetry & Indicators",
        Key.TACTICAL_HARDWARE       to "Hardware & Kinetic Gestures",
    )

    // ─── Lookup functions ───────────────────────────────────────────────────

    /**
     * Returns the spaceship-metaphor label for [key].
     * Falls back to the key name if somehow unmapped (should never happen).
     */
    fun vessel(key: Key): String = vesselMap[key] ?: key.name

    /**
     * Returns the plain Android/English label for [key].
     * Falls back to [vessel] if not found.
     */
    fun clear(key: Key): String = clearMap[key] ?: vessel(key)
}
