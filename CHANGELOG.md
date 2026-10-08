# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.2] - 2026-10-09 — Accessibility-Only Mode, Call Deflector & Refueling Bay Upgrades

### Added
* **Accessibility Service Only Mode (`ShizukuGate`)**: New "Accessibility-only" switch in System Override lets Lightspeed run on the Accessibility Service alone. Every Shizuku permission request now goes through `ShizukuGate`, a single throttled entry point initialized at app start that shows a toast only when a request is actually issued. A one-time Shizuku choice dialog lets you pick how Lightspeed uses Shizuku. In this mode, Shizuku-only actions show a "needs Shizuku" message instead of failing, misleading Shizuku authorization toasts are suppressed, and the arm button can revive the accessibility service without issuing a Shizuku request. If you switch the mode off and deny Shizuku authorization, the switch reverts to on.
* **Call Deflector (`CallStateTracker`)**: New setting to hide the left and right gesture sidebars while a phone call is ringing or active (System Override > During phone calls, off by default). `CallStateTracker` detects the call state. The status bar and sensor area are not affected.
* **Refueling Bay Overlay Cover**: Bay sleep now covers Lightspeed's own overlays and other apps' overlays with black to prevent OLED burn-in (setting: Cover overlays while the Bay sleeps, on by default).
* **Refueling Bay "WHEN DISMISSED" Setting**: Choose whether the Bay stays dismissed until the device is unplugged or until the screen turns off.
* **Slider Shortcuts**: Tapping a slider opens Precision Control & Presets, and a long press resets it to its default (documented in the Guidebook).
* **Power Button Controls**: New grab warning and a tunable double-press window slider.
* **Close App Without Shizuku**: The close app action now falls back to opening Recents and shows a message explaining what to do, instead of doing nothing.

### Changed
* **Horizon Rail Text Safe Guard**: The status bar guard now applies in portrait as well as landscape, with customizable left and right text margins (0-200 dp, defaults 42/58).
* **Power Button Handling (`lsinputd`)**: Power presses are now forwarded natively while the screen is off, and the power grab daemon scores power input devices more accurately when selecting one.

### Fixed
* **Core Cooling Reboot**: Now shows a clear message when Shizuku is not running or not authorized, instead of doing nothing.
* **Refueling Bay Relaunch Loop**: The Bay no longer relaunches in a loop after it goes dark, and its dark guard no longer blocks launches while the screen is off.
* **Flight Blackbox Visibility**: The Flight Blackbox is now always visible instead of only appearing after a crash, and it keeps a saved log of actions that did not work as intended.
* **System Override Dialog State**: The dialog state now survives configuration changes.
* **Power Gesture Hardening (`LightspeedPowerGestureActivity`)**: Hardened the power gesture activity and the automation receiver.

## [1.1.1] - 2026-10-06 — Security Hardening, Backup Validation, Native Monotonic Clock & Countdown Guard

### Added & Hardened
* **Shell Command Validation (`ShellArgGuard`)**: Added `ShellArgGuard` to validate package names, component names, actions, flags, extra keys, and phone numbers before executing privileged commands. Updated `ShizukuShortcutLauncher` and `ElevatedTaskCloser` to use `execShizukuArgv` array arguments, eliminating shell string injection vectors.
* **Backup Import Validation (`LightspeedBackupEngine`)**: Excluded internal system state keys (`RESTRICTED_SYSTEM_STATE_KEYS`) from JSON backup restoration. Added strict action token validation (`isValidActionTokenValue`, `isValidSingleActionToken`) to sanitize package/class names, block command substitution syntax (`$()`, `` ` ``), and enforce 2000-character string bounds.
* **Display DPI & Font Scale Countdown Guard (`SystemOverrideComponents`)**: Integrated a 15-second interactive confirm-or-revert countdown dialog when changing display DPI density or system font scale. Spawned a background `nohup` shell fallback script (`ls_dpi_revert` / `ls_font_revert`) to automatically restore original display density if the application process is terminated or the screen becomes unreadable.

### Changed & Refactored
* **Native Monotonic Clock & Virtual Device Exclusion (`lsinputd.c`)**: Upgraded native input daemon `lsinputd` safety timers and deadline calculations from wall-clock time (`time(NULL)`) to `CLOCK_MONOTONIC`, eliminating freeze conditions caused by system time synchronization jumps. Added explicit check to reject auto-selecting its own `lsinputd` virtual uinput device node.
* **Out-of-Process Guard Helper Isolation (`LightspeedGuardHelper`)**: Updated `LightspeedGuardHelper` to generate package-isolated daemon paths (`/data/local/tmp/ls_guard_<pkg>.sh`) and validate process state via `/proc/<pid>/cmdline`, preventing process collisions across co-existing builds and ensuring automatic daemon cleanup upon package uninstallation.

### Fixed & Security
* **Caller Identity Verification (`LightspeedPowerGestureActivity`)**: Stripped fakeable `Intent.EXTRA_REFERRER` and `Intent.EXTRA_REFERRER_NAME` intent extras before checking caller package identity, preventing referrer spoofing by external applications.

## [1.1.0] - 2026-10-06 — External Automation, Power Menu, Glass Colors & Reliability Hardening

### Added
* **External Automation Suite (Tasker, MacroDroid, ADB)**: Opt-in external broadcast receiver (off by default) supporting 9 system/flight actions, with a security confirmation dialog and multi-mode command builder tabs (ADB shell, Termux/root shell, Intent broadcast) with 1-tap copy.
* **Glass Chromatic Customization**: Choose Dynamic Material You colors or a Custom Palette, with a Tint Infusion Depth slider (5%–100%), 9 preset swatches, and a custom hex dialog with live preview. The tint now flows through the glass body, GPU caustics (AGSL shader, Android 13+), rim dispersion and shimmer.
* **Power Menu Action, Tile & Experimental Power Button Engine (Shizuku, opt-in)**: New `system:power_menu` action (Ship Maintenance), a Power Menu tile in the Tactical Flyout, native `lsinputd` helper for power-button handling, automatic power-device discovery, double-press and hold passthrough, "Block Assistant on Power Hold" toggle, stuck-key safety release, and safety restore on app start (all in Experimental Labs).
* **Watchdogs**: A separate Shizuku-based guard process watches for Lightspeed's own accessibility service crashing and recovers it (60 s per-package debounce, atomic settings cycling; the guard checks every 30 s, was 10 s, and exits on uninstall). The Perimeter Watchdog covers other protected services and now also checks on accessibility-setting changes and on screen/unlock events. Services you turn off manually are no longer revived. Crash recovery needs Shizuku.
* **Battery Exemption Control**: 1-tap access to battery optimization settings from Ship Maintenance.
* **Back-Tap Compatibility Notice**: Inline "Not supported on this device" notice when the sensor is missing.
* **Picker Test Launch**: Long-press a picker shortcut row to test-launch it.
* **Android 13+ Restricted Settings Hint**: Guidance toast when opening accessibility settings.
* **Compile SDK 36**.

### Changed
* **Synthetic Gravity** graduated to a permanent top-level accordion in the HUD Strip tab.
* **Swipe-Down to Notifications** moved from Experimental Labs into the Sensor Area.
* **Shizuku setup instructions** adapt to Android version (SDK 29 vs 30+).
* **Root (`su`) support removed**; all privileged actions use Shizuku.

### Fixed
* **Null-safety crashes** (missing values) in app icon handling (Sonic Deck) and backup URI decoding.
* **Perimeter watchdog race conditions** (two checks running at once and clashing) hardened.
* **Duplicate Central Command windows** from notification taps (`singleTop`).
* **Power gesture caller verification** (checking which app is asking) so identifiable third-party apps cannot trigger it.
* **Toggle trampoline** no longer exported (an internal shortcut component can no longer be reached by other apps).
* **Sonic Deck (experimental)** volume memory restored; scrim now covers cutout and status bar.

### Security & Privacy
* Still **100% offline**: no network permissions, no telemetry.
* External automation is opt-in with a confirmation dialog; automation and crash-diagnostic preferences are excluded from backups.
* Release builds refuse unsigned or debug-signed packaging.
* Dynamic receivers explicitly use `RECEIVER_NOT_EXPORTED` (internal listeners cannot be reached by other apps).

### Refactored & Optimized
* **Zero-allocation render loops** (no per-frame `RectF`, so less memory churn) across deflectors, capsule, HUD gauges and deep-space canvas.
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
