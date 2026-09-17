# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

## [1.4.1] - 2026-09-17

### ⚓ Nautical Mooring Rope & Deflector Dynamic Routing Engine
* **Per-Gesture Nautical Mooring Rope Toggle (`NauticalMooringRope.kt`, `GestureComponents.kt`)**:
  * High-performance custom Canvas component rendering a translucent Material 3 Expressive braided cord spanning across gesture rows in deflector configuration cards.
  * Interactive Knotting & Severing: Tapping the mooring line toggles between a unified knotted state and a decoupled dual-control state with spring animations and tactile haptic feedback.
  * **Unified (Tied)**: Consolidates the gesture into 1 unified row, mirroring actions between Upper and Lower deflector sectors across the whole flank.
  * **Dual Control (Severed)**: Splits the gesture into 2 independent rows (`Upper Sector` and `Lower Sector`), allowing separate action assignments for each half.
* **Corner-Anchored Flanking Mooring Cords (`SeveredMooringPairCard`)**:
  * Unlinked Upper & Lower sector row pairs are cradled in natural gravity-hanging cords anchored strictly at the top-left and top-right shoulders via tactical mooring eyelets and hitch knots.
  * Completely open top header with zero middle spanning, drooping slack cords along both flanks past Row 1 and Row 2 with frayed hemp fiber tips.
  * Mooring Confirmation Dialog (`MooringConfirmDialog`): Safeguards against accidental severance or re-tying.
* **Dynamic Accordion Routing & Auto-Scroll Engine**:
  * "Unified Gesture Matrix" holds only tied full-flank gestures; "Separate Deflector Controls" holds only unlinked dual-sector gestures.
  * Automatic accordion expansion and smooth animated scrolling shifts the viewport to newly populated rows upon state toggles.
* **Flank-Aware Inward Sweep & Hardware Maneuvers Typography Streamlining**:
  * Fixed Inward Sweep icon to flank-accurate inward sweep trajectories (`●──»──>` on Left Deflector, `<──«──●` on Right Deflector) with mid-track scrub chevrons.
  * Purged bulky monospace T-blocks across Deflectors and HUD Strip "Hull & Ship Maneuvers" volume keys, restoring dedicated `GestureTrailTracer` vector icons and hold halos.
* **Dynamic Deflector Scrubber & Macro Linking (`LightspeedPreferences`)**:
  * Added `KEY_UNIFIED_SCRUB_REGIONS_LINKED`, `KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT`, `KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT`, and `pref_gesture_unified_<flank>_<vectorKey>`.
  * Live overlay touch resolution across `LightspeedCruiseOverlayTouch`, `LightspeedCruiseOverlay`, and `LightspeedLeftWingOverlay` honors per-gesture unification states dynamically.

## [1.4.0] - 2026-09-16

### 🚀 Major Highlights & Commercial Polish
* **Android 14 & 15 Modernization (Target SDK 35)**:
  * Full support for Android 14/15 native zero-duration activity transitions via `Activity.overrideZeroTransition()`, eliminating legacy transition flicker across all 10 system and cockpit activities.
  * Modern `setShowWhenLocked()` and `setTurnScreenOn()` window APIs with strict backward-compatibility fallbacks.
  * Edge-to-edge system bar compliance via Material 3 dynamic theming.
* **Zero-Allocation Render Pipeline & Monolith Decomposition (<500 lines)**:
  * Replaced in-canvas path allocations with pre-allocated path objects (`chamferedPath.rewind()`, `circleClipPath.rewind()`, `diamondPath`, `gearPath`, `remember { Path() }`) across `LightspeedHudRenderer`, `DeepSpaceRenderer`, `LightspeedNotchOverlay`, and `RefuelingBatteryTelemetry`.
  * Preserved full floating-point fidelity in `LightspeedBackupEngine` via explicit type serialization and heuristic float parsing.
  * Decomposed all monolithic classes (>5,500 lines) into 12 targeted components with all files strictly under 500 lines: `ScrollableAppWidgetContainer`, `RefuelingWidgetControls`, `LightspeedCruiseOverlaySpatial`, `LightspeedCruiseOverlayActions`, `LightspeedKeySlots`, `LightspeedPowerKeyEngine`, `LightspeedKeyHudNav`, `CentralCommandModel`, `CentralCommandState`, `PerimeterServiceCard`, `PerimeterTelemetryBanner`, `PerimeterDialogHeader`, `LightspeedAccessibilityOverlays`, and `LightspeedAccessibilityReceiver`.
* **Modular Avionics Architecture (Phase 1 & 2)**:
  * Decomposed monolithic controllers into decoupled domain units: `LightspeedCruiseOverlayTouch` & `LightspeedCruiseOverlayDraw`, `HudStripTab` 4-deck breakdown, `CentralCommandDialogs`, `DeflectorStylingComponents`, `FlightControlDeckComponents`, `SliderCalibrationDialog`, and `CoreCoolingComponents`.
  * Reduced maximum file sizes across the settings suite by over 60%, drastically reducing re-composition overhead.
* **Master Flight Deck & Automation Ecosystem**:
  * Unified Master Flight Control 1-Tap hero pad with active system health telemetry.
  * Native Quick Settings Tiles (`Lightspeed` Master Toggle and `Deflectors` Flank Toggle).
  * Direct automation broadcast receiver (`LightspeedAutomationReceiver`) protected by signature permissions for MacroDroid, Tasker, and ADB control.
  * Persistent interactive flight notification manager with one-touch state controls.
* **Refueling Bay & Cryo Stasis Overhaul**:
  * Seamless Android AppWidget host integration with 3D cube smart stacks and carousel layout.
  * Migrated preference binding to `activity.defaultPrefs()`, restoring 100% backup and restore coverage under `LightspeedBackupEngine`.
  * Privileged launcher fallback for unexported third-party widget configuration activities.
* **Sensor Gravity & 360° Attitude Engine**:
  * Dedicated Sensor Portrait driver (0° and 180° inversion support) for inverted charging and mounted operation.
  * Fine-grained app attitude buckets with configurable override expiration policies.
* **Liquid Glass Optics & Custom Shaders**:
  * Real Liquid Glass engine with live runtime prototype switcher (Liquid Glass, Deep Frost, Tactical Obsidian).
  * Progressive frosted glass pill styling and dynamic specular rim flaring on flank deflectors.
* **Commercial Release Packaging & Zero-Telemetry Privacy**:
  * Hardened ProGuard/R8 shrinking and obfuscation rules with Material Icons extended tree-shaking.
  * Strict 100% offline invariant: zero internet permissions, zero analytics, zero external network dependencies.
* **HUD Strip Sensor Deck Notification Expansion & Freeze Patch**:
  * Added `KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS` toggle in Experimental Labs for pulling down Android notifications via status bar sensor swipe down.
  * Resolved SystemUI panel deadlock/freeze bug caused by continuous `GLOBAL_ACTION_NOTIFICATIONS` IPC spam and touch consumption when notification shade or Quick Settings was already open.
  * Implemented dynamic `isSystemUiActive` detection in `LightspeedAccessibilityOverlays` to cleanly hide the sensor touch window when SystemUI is in the foreground, restoring native touch pass-through for Quick Settings pulls.
* **Scrub Action Gating Rule & Sensor Deck Architecture Hardening**:
  * Enforced rule restricting continuous scrub actions (`system:volume`, `system:brightness`, `system:screen_timeout`) exclusively to Hold Modifiers (`_HOLD`) and deflector sweeps (`SCRUB`).
  * Dynamically filtered out scrub actions from picker lists for all momentary gestures (Tap, Double Tap, regular Swipes without Hold).
  * Removed the unpredictable "Sensor Deck Long Sweep (Scrubbing)" gesture and UI row, unifying status bar scrubbing under tactile Hold-to-Scrub (+ Hold Modifiers).

---
* **Massive Memory Optimization & App Payload Shrink (Nightly):**
  * Identified and destroyed a rogue pre-warming background loop in `LightspeedActionRegistry` that attempted to pre-cache all installed apps at zero-second startup.
  * Removed unbounded `ConcurrentHashMap<String, Drawable>` in `LightspeedDataBridge` that was secretly hoarding ~270 heavy `AdaptiveIconDrawable` instances, eliminating a massive 75MB+ RAM spike.
  * Reduced `LruCache` sizes for `Drawable` buffers from 350 to 50 across Icon and Shortcut Managers to aggressively purge heavy Android UI layouts, while maintaining the lightweight 144x144 rasterized `Bitmap` caches.
  * Hard-capped `OmniscientAudioDockManager` artwork processing to immediately compress high-res album arts to 144x144, preventing 36MB+ RAM allocations per song.
  * Implemented an ephemeral `temporaryPickerCache` that borrows RAM only when the Action Selection UI is active for buttery smooth scrolling, and instantly annihilates the cache inside `onDestroy()` when the UI closes.
  * **Build System Optimization**: Injected R8 Shrinker (`isMinifyEnabled = true`, `isShrinkResources = true`) directly into the Nightly `debug` Gradle build. This actively strips >9,900 unused Material Icons from Jetpack Compose, crushing the `.dex` mapping footprint from 60MB down to 13MB and drastically lowering the baseline memory tax of the Jetpack Compose architecture.

## [1.1.2] - 2026-08-23

### 🚀 Highlights & Fixes
* **Relative Horizontal Pull Calculation**: Replaced absolute screen-edge distance checks with relative touch-down pull vectors (`touchDownRawX - rawX > 28dp`).
* **Category Scrubbing Stability**: Completely eliminated premature app grid flashes while scrolling up and down through categories on the central pill.
* **Smooth Reversion**: Pushing finger back towards the right edge smoothly transitions from App Grid back to Category selection.

---

## [1.1.1] - 2026-08-23

### 🚀 Highlights & Fixes
* **Gesture Decision Gate (Zero Flicker)**: Added a quiet neutral intent resolution gate to `LightspeedCruiseOverlay`. Direct lateral swipes to open the Cockpit / Gears Wheel now render 100% seamlessly without flashing the 3D category cylinder in the background.
* **Rock-Solid Gesture Separation**: Distinctly isolates fast lateral swipes (Cockpit/Gears) from deliberate vertical category scrubbing (Category App Grid).
* **Backup & Restore SAF Hardening**: Enforced `"wt"` stream mode and explicit JSON type coercions to ensure 100% reliable settings backup and restore across Android versions.

---

## [1.1.0] - 2026-08-23

### 🚀 Major Highlights

* **Status Bar Touch System Overhaul**: Resolved touch detection issues on the status bar gesture strip. Restored touch consumption lifecycle, fixed span geometry calculation, bound height to user preferences, and added Android 10+ system gesture exclusion boundaries.
* **Refined 2D Right Sidebar Engine**: Instant access to the dual-ring Favorites/Gears wheel via direct lateral swipe. Scrubbing categories is now zero-latency with in-memory metadata pre-warming and cached icon rendering.
* **Offline Backup & Restore Engine**: One-tap JSON export and import for all gesture mappings, sensitivity thresholds, coordinate dimensions, and custom gear sets via Storage Access Framework (SAF).
* **Progressive Liquid Glass AGSL Shader**: Re-engineered the depth blur shader with a 9-tap Poisson disk kernel, subtle chromatic edge refraction, and deep-space void tinting. Added RenderEffect fallback for Android 12+.
* **Side-by-Side Nightly Development Setup**: Completely isolated Nightly build (`com.sbf.lightspeed.nightly`) running side-by-side with Stable (`com.sbf.lightspeed`), featuring a distinct launcher icon with a crescent moon emblem and Nightly badge.

---

### 🛠️ Detailed Improvements

#### Gesture & Overlay Engine
* **Status Bar Overlay**:
  * Fixed window width calculation by clamping span DP-to-PX conversion to actual physical display bounds.
  * Added `FLAG_NOT_TOUCH_MODAL` so touch events outside the active status bar pass seamlessly to background applications.
  * Corrected `GestureDetector` listener `onDown` to return `true` to ensure full gesture streams are captured.
  * Bound layout height dynamically to `pref_statusbar_thickness` (default 80dp).
  * Added hardware haptic feedback ticks on gesture trigger.
* **Right Sidebar Cruise Overlay**:
  * Widened initial horizontal breakthrough threshold (`deltaX > 18dp`, `deltaY < 28dp`) for effortless entry into Gears/Favorites.
  * Eliminated background thread spawning during `ACTION_DOWN` for instantaneous first-frame 3D cylinder category rendering.
  * Removed artificial 150ms delay gate when transitioning from category scrubbing into the app grid.
  * Routed icon loading through in-memory cache to prevent main-thread stutter during fast swipes.

#### Backup & Data System
* Created `LightspeedBackupEngine` for offline JSON export/import of all user preferences.
* Added Backup & Restore controls in the floating settings interface.
* Added a protected Factory Reset confirmation dialog.

#### UI & Liquid Glass Aesthetics
* Upgraded AGSL progressive shader with chromatic prism dispersion on blur boundaries.
* Polished `FloatingOverlayContainer` with vertical glass gradient borders, radial lighting, and 32dp corner radii.
* Replaced legacy window insets with modern `enableEdgeToEdge()` across all activities.
* Registered `SidebarTileService` and `SidebarSettingsActivity` for Quick Settings access.

#### Architecture & Cleanups
* Removed runtime reflection (`context.resources.getIdentifier`) in settings preference resolution.
* Purged unused legacy files (`MacroHook.kt`, `OverlayPatch.kt`, `FrostedPickerActivity.kt`, `LightspeedGestureEngine.kt`).
* Verified clean compilation with `./gradlew assembleDebug` and `./gradlew assembleRelease`.

---

## [1.0.0] - 2026-08-22

* Initial release of Lightspeed Gesture Launcher.
* Mechanical dual-ring Cockpit & Gears wheel system.
* Floating Jetpack Compose settings interface.
* Elevated Shizuku/Shevery fast task termination without root.
