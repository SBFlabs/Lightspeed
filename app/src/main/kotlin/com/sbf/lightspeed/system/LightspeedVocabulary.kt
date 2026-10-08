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
        FLIGHT_BLACKBOX,

        // Call Deflector Mode
        CALL_DEFLECTOR_MODE_TITLE,
        CALL_DEFLECTOR_MODE_KEEP_ACTIVE,
        CALL_DEFLECTOR_MODE_HIDE,
    }

    // ─── Vessel Lore dictionary ─────────────────────────────────────────────

    private val vesselMap: Map<Key, String> = mapOf(
        Key.CENTRAL_COMMAND         to "Central Command",
        Key.CRUISE                  to "Cruise",
        Key.COCKPIT                 to "Cockpit",
        Key.HUD_STRIP               to "HUD Strip",
        Key.TAB_LEFT                to "◀ DEFLECTOR",
        Key.TAB_CENTER              to "HUD STRIP",
        Key.TAB_RIGHT               to "DEFLECTOR ▶",

        Key.DEFLECTORS              to "Left & Right Deflectors",
        Key.LEFT_DEFLECTOR          to "Left Deflector",
        Key.RIGHT_DEFLECTOR         to "Right Deflector",
        Key.SENSOR_AREA             to "Sensor Area",
        Key.UNIFIED_DEFLECTORS      to "Unified Deflectors",
        Key.CORE_ZONE               to "Core Astrogation",
        Key.UPPER_FLANK             to "Upper Flank",
        Key.LOWER_FLANK             to "Lower Flank",

        Key.HORIZON_RAIL            to "Horizon Rail",
        Key.ORBITAL_CAPSULE         to "Orbital Capsule",
        Key.SCREEN_ORIENTATION      to "Synthetic Gravity",
        Key.SYSTEM_ACTIONS          to "System Actions",
        Key.HARDWARE_CONTROLS       to "Hardware & System Controls",
        Key.GESTURE_SCRUBBERS       to "Scrubbers",
        Key.NAVIGATION              to "Navigation",
        Key.MEDIA_CONTROLS          to "Media Controls",
        Key.SHIP_MAINTENANCE        to "Ship Maintenance",

        Key.SUB_LIGHT_THRUSTERS     to "Sub-Light Impulse Thrusters",
        Key.HULL_RESONATOR          to "Hull Kinetic Acoustic Resonator",
        Key.IGNITION_OVERRIDE       to "Ignition Override",

        Key.REFUELING_BAY           to "Refueling Bay & Cryo Stasis",
        Key.SHIP_DATA_VAULT         to "Config Vault",
        Key.SHIZUKU_JETTISON        to "Sub-Space Cloaked Egress",
        Key.WATCHDOG_SENTINELS      to "Watchdog Sentinels",
        Key.PERIMETER_DEFENSE       to "Perimeter Watchdog",
        Key.CORE_WATCHDOG           to "Core Watchdog",
        Key.GRAVITY_ENGINE          to "Synthetic Gravity Engine",
        Key.CORE_COOLING            to "System Reboot",
        Key.BATTERY_EXEMPTION       to "Battery Exemption Control",
        Key.SONIC_DECK              to "Sonic Deck",

        Key.SYSTEM_OVERRIDES        to "System Override",

        Key.MASTER_FLIGHT           to "Master Flight",
        Key.FLIGHT_DECK             to "Flight Control Deck",
        Key.EXPERIMENTAL_LABS       to "Experimental Labs",

        Key.GUIDEBOOK               to "The Stranded in Space Guidebook",
        Key.TELEMETRY_AND_INDICATORS to "Info Beacons",
        Key.TACTICAL_HARDWARE       to "Hull & Ship Maneuvers",
        Key.POWER_GRAB_WARNING      to "Experimental module: with grab active, power key may fail to wake dormant display on certain hardware. If encountered, disengage grab or wake via biometric sensor or power supply.",
        Key.FLIGHT_BLACKBOX         to "Flight Blackbox",

        Key.CALL_DEFLECTOR_MODE_TITLE       to "Comms Channel Deflector Protocol",
        Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE to "🛡️ Maintain Active Deflectors During Comms",
        Key.CALL_DEFLECTOR_MODE_HIDE        to "🙈 Cloak Deflectors During Comms",
    )

    // ─── Clear Comms dictionary ──────────────────────────────────────────────

    private val clearMap: Map<Key, String> = mapOf(
        Key.CENTRAL_COMMAND         to "Central Command",
        Key.CRUISE                  to "Navigation",
        Key.COCKPIT                 to "Launcher Settings",
        Key.HUD_STRIP               to "System & Overlay Features",
        Key.TAB_LEFT                to "◀ LEFT",
        Key.TAB_CENTER              to "SYSTEMS",
        Key.TAB_RIGHT               to "RIGHT ▶",

        Key.DEFLECTORS              to "Left & Right Gesture Sidebars",
        Key.LEFT_DEFLECTOR          to "Left Gesture Sidebar",
        Key.RIGHT_DEFLECTOR         to "Right Gesture Sidebar",
        Key.SENSOR_AREA             to "Gesture Area",
        Key.UNIFIED_DEFLECTORS      to "Unified Gesture Sidebars",
        Key.CORE_ZONE               to "Central Trigger Area",
        Key.UPPER_FLANK             to "Upper Trigger Zone",
        Key.LOWER_FLANK             to "Lower Trigger Zone",

        Key.HORIZON_RAIL            to "Progress Rail",
        Key.ORBITAL_CAPSULE         to "Camera Cutout HUD",
        Key.SCREEN_ORIENTATION      to "Screen Orientation",
        Key.SYSTEM_ACTIONS          to "System Actions",
        Key.HARDWARE_CONTROLS       to "Hardware & System Controls",
        Key.GESTURE_SCRUBBERS       to "Sliders",
        Key.NAVIGATION              to "Navigation",
        Key.MEDIA_CONTROLS          to "Media Controls",
        Key.SHIP_MAINTENANCE        to "System Maintenance",

        Key.SUB_LIGHT_THRUSTERS     to "Volume Button Actions",
        Key.HULL_RESONATOR          to "Back-Tap Gestures",
        Key.IGNITION_OVERRIDE       to "Power Button Remapping",

        Key.REFUELING_BAY           to "Charging Dashboard",
        Key.SHIP_DATA_VAULT         to "Backup & Restore",
        Key.SHIZUKU_JETTISON        to "Elevated Task Dismissal (OEM Kill-Bypass)",
        Key.WATCHDOG_SENTINELS      to "Watchdog & Crash Guard",
        Key.PERIMETER_DEFENSE       to "Accessibility Service Manager (Shizuku)",
        Key.CORE_WATCHDOG           to "System Immunity & Watchdogs",
        Key.GRAVITY_ENGINE          to "Orientation Preferences",
        Key.SYSTEM_OVERRIDES        to "Developer Options",
        Key.BATTERY_EXEMPTION       to "Battery Exemption Control",
        Key.SONIC_DECK              to "Universal Audio Control",

        Key.CORE_COOLING            to "System Reboot",

        Key.MASTER_FLIGHT           to "Master Toggle",
        Key.FLIGHT_DECK             to "Quick Controls",
        Key.EXPERIMENTAL_LABS       to "Experimental Features",

        Key.GUIDEBOOK               to "Feature Dictionary",
        Key.TELEMETRY_AND_INDICATORS to "Telemetry",
        Key.TACTICAL_HARDWARE       to "Hardware Gestures",
        Key.POWER_GRAB_WARNING      to "This feature is experimental: with grab ON, the power button may not wake a screen that is off on some devices. If that happens, turn grab OFF or wake the phone with the fingerprint sensor or by plugging in a charger.",
        Key.FLIGHT_BLACKBOX         to "Diagnostics & Crash Logs",

        Key.CALL_DEFLECTOR_MODE_TITLE       to "Gesture Sidebars During Calls",
        Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE to "Keep sidebars active during calls",
        Key.CALL_DEFLECTOR_MODE_HIDE        to "Hide sidebars during calls",
    )

    // ─── Co-Pilot (Bilingual) Subtitle dictionary ────────────────────────────

    private val copilotSubtitleMap: Map<Key, String> = mapOf(
        Key.TELEMETRY_AND_INDICATORS to "Status bar telemetry",
        Key.REFUELING_BAY           to "Charging Dashboard",
        Key.POWER_GRAB_WARNING      to "Experimental feature warning: turn grab OFF or wake with fingerprint or charger if screen off wake fails.",
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

    /**
     * Returns the plain-English subtitle label for [key] in Co-Pilot mode.
     */
    fun copilotSubtitle(key: Key): String = copilotSubtitleMap[key] ?: clear(key)
}
