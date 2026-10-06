# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-10-06 — External Automation, Power Menu, Glass Colors & Reliability Hardening

### Added
* **External Automation Suite (Tasker, MacroDroid, ADB)**: Opt-in external broadcast receiver (off by default) supporting 9 system/flight actions, with a security confirmation dialog and multi-mode command builder tabs (ADB shell, Termux/root shell, Intent broadcast) with 1-tap copy.
* **Glass Chromatic Customization**: Choose Dynamic Material You colors or a Custom Palette, with a Tint Infusion Depth slider (5%–100%), 9 preset swatches, and a custom hex dialog with live preview. The tint now flows through the glass body, GPU caustics, rim dispersion and shimmer.
* **Power Menu Action & Tile**: New `system:power_menu` action (Ship Maintenance) and a Power Menu tile in the Tactical Flyout.
* **Experimental Power Button Engine (Shizuku, opt-in)**: Native `lsinputd` helper for power-button handling, automatic power-device discovery, double-press and hold passthrough, "Block Assistant on Power Hold" toggle, stuck-key safety release, and safety restore on app start.
* **Out-of-Process Core Guard & Griffin Recovery**: Shizuku shell guard monitors for crashed accessibility services and attempts recovery out of process, with 60 s per-package debounce and atomic settings cycling.
* **Watchdog Fast Triggers**: Perimeter checks now fire on accessibility-setting changes and screen/unlock events.
* **Battery Exemption Control**: 1-tap access to battery optimization settings from Ship Maintenance.
* **Sonic Deck / Universal Audio Control**: New vocabulary entries in Space Lore and Clear Comms, with backward compatibility.
* **Back-Tap Compatibility Notice**: Inline "Not supported on this device" notice when the sensor is missing.
* **Picker Test Launch**: Long-press a picker shortcut row to test-launch it.
* **Android 13+ Restricted Settings Hint**: Guidance toast when opening accessibility settings.
* **Compile SDK 36**.

### Changed
* **Synthetic Gravity** graduated to a permanent top-level accordion in the HUD Strip tab.
* **Swipe-Down to Notifications** moved from Experimental Labs into the Sensor Area.
* **Shizuku setup instructions** adapt to Android version (SDK 29 vs 30+).
* **Manual service disable is honored**: Lightspeed no longer revives services you turned off intentionally.
* **Guard helper** checks every 30 s (was 10 s) and exits on uninstall.
* **Root (`su`) support removed**; all privileged actions use Shizuku.

### Fixed
* **Null-safety crashes** in app icon handling (Sonic Deck) and backup URI decoding.
* **Perimeter watchdog race conditions** hardened.
* **Duplicate Central Command windows** from notification taps (`singleTop`).
* **Audio dock** volume memory restored; scrim now covers cutout and status bar.
* **Power gesture caller verification** so third-party apps cannot trigger it.
* **Toggle trampoline** no longer exported.

### Security & Privacy
* Still **100% offline**: no network permissions, no telemetry.
* External automation is opt-in with a confirmation dialog; automation and crash-diagnostic preferences are excluded from backups.
* Release builds refuse unsigned or debug-signed packaging.
* Dynamic receivers explicitly use `RECEIVER_NOT_EXPORTED`.

### Refactored & Optimized
* **Zero-allocation render loops** (no per-frame `RectF`) across deflectors, capsule, HUD gauges and deep-space canvas.
* **ProGuard tightened**, ~90 KB smaller release APK.
* **Modules extracted** from `ElevatedTaskCloser` (`TaskInspector`, `PopupLauncher`); timeout-safe privileged process execution (`PrivilegedWait`).
* **Structured exception logging** (`logSwallowed`) replaced silent empty catch blocks across the app.
* **Dead code removed**: legacy `CockpitSettingsActivity`, unused styling components, dormant permissions, old launcher drawables.
* Shizuku licensing attribution corrected to MIT.

### Known Quirks & Bugs
* **Split Screen Is Experimental**: Starting a split while a video is playing in a pop-up window or picture-in-picture can crash the system UI. Pressing split screen again while a split is already active may not exit it.
* **Returning to the Previous App in Split Screen**: Previous-app switching is unreliable while in split screen. Double-tap the Recents button instead.

## [1.0.3] - 2026-09-29 — Lockscreen Controls, Synthetic Gravity & Power Optimization

### Added & Improved
* **Granular Lockscreen Visibility Controls**: Choose exactly what displays on your lockscreen (Deflectors, Horizon Rail, or Top Sensor Deck) so you can launch actions directly over keyguard.
* **Automatic Native Gesture Restoration**: Hiding Deflectors on the lockscreen automatically restores Android's native back gestures while locked, reverting to Lightspeed edge controls seamlessly upon unlocking.
* **Synthetic Gravity Actions**: Improved orientation actions with deeper customization and added chips for fast tuning.
* **Power Management & Battery Optimization**: Background listeners and CPU wake locks are now released immediately when Power button shortcuts or Shizuku options are turned off.

### Fixed
* **Deflector Flank Isolation**: Resolved a bug where swipe gestures on the right deflector accidentally executed left deflector actions under symmetry settings.
* **Orbital Capsule Live Preview**: Cutout calibration and preview overlays no longer show up when the Orbital Capsule is turned off.
* **Perimeter Watchdog Recovery**: Fixed edge cases where external accessibility services failed to re-bind cleanly after system restarts or service interruptions.

### Refactored
* **StatusBarOverlayDraw Modularization**: Split the status bar rendering engine into 4 focused extension modules (`StreamCollector`, `RailDraw`, `TickerDraw`, `OverlayDraw`) for maximum stability and agent navigability.
* **Subtle Visual & Code Polish**: Restyled settings cards with cleaner Material 3 typography, improved badge alignment, and replaced raw string literals with type-safe preference constants.

## [1.0.2] - 2026-09-27 — Snapdragon Startup Blur Patch

### Fixed
* **Startup Screen Blur Patch**: Resolved an issue on Android 12+ (Snapdragon) where `FLAG_BLUR_BEHIND` blurred the screen immediately upon accessibility service binding. Blur is now deferred dynamically until gesture engagement.

## [1.0.1] - 2026-09-25 — Touch Collision & Task Eviction Stability

### Fixed
* **Task Eviction & Graceful Close**: Fixed an issue where closing an app gracefully (`system:close_app`) dispatched a synthetic Back event into underlying applications upon task eviction.
* **Hold Gesture Mutual Exclusion**: Hardened edge deflector touch tracking with explicit hold-fired state guarantees, ensuring simple swipe actions on finger release never collide or double-fire after hold actions.

## [1.0.0] - 2026-09-24 — Inaugural First Flight

### Added
* **Category Cruise**: Full-screen vertical app cruising deck to navigate entire application fleets with rapid one-thumb kinetic scrubbing and category switching. Pulling down past the bottom smoothly reveals the hidden Central Command deck.
* **Cockpit Hangar & Gears**: Orbital dual-ring app launcher wheels with rotating gimbal rings, tactile haptics, and classic 180° targeting flight reticle.
* **Deflectors (Port & Starboard)**: Bilateral fluid screen-edge wings with tactile gesture zones, glass shaders, and precision continuous scrubbing for volume and brightness.
* **Sensor Area & Horizon Rail**: Top bezel tactile gesture capture zone for instant system actions, combined with top-edge status bar progress lines and telemetry flares.
* **Central Command**: Master configuration deck with dynamic accordion matrix controls, offline JSON backup & restore engine, and deep customization.
* **System Overrides via Shizuku**: Direct toggles for native back gesture neutralization, system animation scales, font scaling, and display DPI adjustment.
* **Dual Watchdog Sentinels**: Core Watchdog self-recovery mechanism and Perimeter Watchdog for reviving external accessibility services.
* **100% Sovereign & Offline Architecture**: Zero telemetry, zero analytics, zero external network permissions, and dynamic Material 3 Expressive theming.

### Known Quirks & Bugs
* **Large Screen Swipe Differentiation**: On 11.5-inch tablet screens, the Astrogation Core may struggle to cleanly differentiate between vertical and horizontal swipes.
* **Home Screen Launcher App Shortcuts Regression**: In the Action Selection Menu, Home Screen Launcher App Shortcuts currently fail to populate across all apps (leaving `CALL_PHONE` temporarily dormant).
* **App Categorization Overflow**: Static heuristic mapping adapted from LaunchTime may place uncataloged apps into "Other" or "Utilities".
