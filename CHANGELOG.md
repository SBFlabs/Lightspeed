# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-09-25

### Fixed
* **Task Eviction & Graceful Close Fix**: Fixed an issue where closing an app gracefully (`system:close_app`) dispatched a synthetic Back event into underlying applications upon task eviction.
* **Hold Gesture Mutual Exclusion**: Hardened edge deflector touch tracking with explicit hold-fired state guarantees, ensuring simple swipe actions on finger release never collide or double-fire after hold actions.

## [1.0.0] - 2026-09-24 — Inaugural First Flight

### Added
* **Category Cruise**: Full-screen vertical app cruising deck to navigate entire application fleets with rapid one-thumb kinetic scrubbing and category switching. Pulling down past the bottom smoothly reveals the hidden Central Command deck.
* **Cockpit Hangar & Gears**: Orbital dual-ring app launcher wheels with rotating gimbal rings, tactile haptics, and classic 180° targeting flight reticle.
* **Deflectors (Port & Starboard)**: Bilateral fluid screen-edge wings with tactile gesture zones, liquid glass shaders, and precision continuous scrubbing for volume and brightness.
* **Sensor Area & Horizon Rail**: Top bezel tactile gesture capture zone for instant system actions, combined with top-edge status bar progress lines and telemetry flares.
* **Central Command**: Master configuration deck with dynamic accordion matrix controls, offline JSON backup & restore engine, and deep customization.
* **System Overrides via Shizuku**: Direct toggles for native back gesture neutralization, system animation scales, font scaling, and display DPI adjustment.
* **Dual Watchdog Sentinels**: Core Watchdog self-recovery mechanism and Perimeter Watchdog for reviving external accessibility services.
* **100% Sovereign & Offline Architecture**: Zero telemetry, zero analytics, zero external network permissions, and dynamic Material 3 Expressive theming.

### Known Quirks & Bugs
* **Large Screen Swipe Differentiation**: On 11.5-inch tablet screens, the Astrogation Core may struggle to cleanly differentiate between vertical and horizontal swipes.
* **Home Screen Launcher App Shortcuts Regression**: In the Action Selection Menu, Home Screen Launcher App Shortcuts currently fail to populate across all apps (leaving `CALL_PHONE` temporarily dormant).
* **App Categorization Overflow**: Static heuristic mapping adapted from LaunchTime may place uncataloged apps into "Other" or "Utilities".
