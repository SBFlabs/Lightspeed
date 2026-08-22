# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

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
