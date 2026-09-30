# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-09-29 — Lockscreen Controls, Synthetic Gravity & Power Optimization

### Added & Improved
* **Granular Lockscreen Visibility Controls**: Choose exactly what displays on your lockscreen (Deflectors, Horizon Rail, or Top Sensor Deck) so you can launch actions directly over keyguard.
* **Automatic Native Gesture Restoration**: Hiding Deflectors on the lockscreen automatically restores Android's native back gestures while locked, reverting to Lightspeed edge controls seamlessly upon unlocking.
* **Synthetic Gravity Subaccordion & Quick-Tuning Chips**: Overhauled the Synthetic Gravity subaccordion in the Action Selection Menu with inline gesture chips for instant orientation tuning (Strict Portrait, Sensor Portrait, Landscape, and 360° Gyro).
* **Power Management & Battery Optimization**: Background listeners and CPU wake locks are now released immediately when Power button shortcuts or Shizuku options are turned off.

### Fixed
* **Deflector Flank Isolation**: Resolved a bug where swipe gestures on the right deflector accidentally executed left deflector actions under symmetry settings.
* **Orbital Capsule Live Preview**: Cutout calibration and preview overlays no longer show up when the Orbital Capsule is turned off.
* **Perimeter Watchdog Recovery**: Fixed edge cases where external accessibility services failed to re-bind cleanly after system restarts or service interruptions.

### Refactored
* **StatusBarOverlayDraw Modularization**: Split the status bar rendering engine into 4 focused extension modules (`StreamCollector`, `RailDraw`, `TickerDraw`, `OverlayDraw`) for maximum stability and agent navigability.
* **Subtle Visual & Code Polish**: Restyled settings cards with cleaner Material 3 typography, improved badge alignment, and replaced raw string literals with type-safe preference constants.

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
