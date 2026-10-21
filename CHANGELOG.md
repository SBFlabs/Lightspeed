# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.1] - 2026-10-21 — Samsung & TCL Freeform Hotfix and Watchdog Flood Patch

### Fixed
* **Samsung One UI & Modern Android Freeform Crash**: Removed invalid `--activity-reuse-task` flag in `PopupLauncher` that caused `am start --windowingMode 5` to fail on Samsung One UI and modern Android releases, restoring native floating pop-up window launching across Samsung Galaxy devices.
* **TCL NXTPAPER Freeform Auto-Arming**: Added automated verification in `PopupLauncher` to detect and arm `enable_freeform_support` and `force_resizable_activities` in `Settings.Global` via Shizuku if disabled by TCL OEM defaults.
* **Watchdog FlightLog Buffer Spam**: Fixed repeated `presumed user toggle-off` log entries in `LightspeedWatchdogEngine` that flooded the 50-entry FlightLog ring buffer every 30 seconds for intentionally disabled perimeter services.

## [1.2.0] - 2026-10-21 — Category Cruise Overhaul, 12-Theme Glass Engine & Ergonomics

### Added
* **Category Cruise Ground-Up Engine Overhaul (`LightspeedAppCategorizer`)**: Completely rebuilt categorization engine with deep app catalog definitions and heuristics across hundreds of popular and niche Android apps, covering productivity, media, dev tools, and utilities with instant indexing.
* **Category Cruise Threshold Swipe-to-Pin (`StickyPinTouchHandler`)**: Swiping past the screen threshold locks the active category into persistent focus mode, allowing rapid multi-app launching without dismissing the overlay.
* **Category Cruise App Long-Press Menu (`LightspeedCruiseOverlayAppMenu`)**: Long-press any app row in Category Cruise to trigger a tactical action deck: launch in Split Screen, freeform Pop-up window, pair on the fly with "Split with...", view App Info, or move the app to a different category.
* **Category Column Reordering & In-Place Management (`LightspeedCruiseOverlayEditMode`)**: Long-press empty space in Category Cruise to open the reorder hangar. Rearrange category column order with drag-and-drop, toggle category visibility, or reset to defaults.
* **Category Cruise "Split with..." Action**: Added a dedicated row in the Category Cruise long-press app menu (`Split with...`) that launches the selected app and immediately opens `CockpitGearPickerActivity` in split mode, allowing the user to select and pair a second app on the fly without needing background apps running.
* **Full-Width Form Dropdown Anchors (`DeckDropdownAnchorBox`)**: Introduced `DeckDropdownAnchorBox` in `DeckControls.kt` and wired `anchorWidth` into `DeckThemedDropdownMenu` across Back Tap Activation Scope and Lockscreen Deflector mode fields, ensuring full-width form bars expand their dropdown menus to 100% of the bar's width while keeping small sub-chip menus compact.
* **Tactical Hold Cue for Battery Exemption Pill**: Added `HOLD ↗` as the 3rd line on the Battery card in `WatchdogComponents.kt`, preserving the instant 1-tap Shizuku toggle for Lightspeed while matching the vertical rhythm of the Services pill and explicitly teaching the user that long-pressing opens the multi-app Battery Whitelist manager.

### Fixed
* **Category Cruise Split & Pop-up Direct Execution**: Bypassed gesture-level Recents fallback constraints for Split Screen and Pop-up window actions in Category Cruise (`allowRecents = false`), enforcing direct `AUTO` execution via Shizuku/native windowing and eliminating "method not supported" toast errors when gestures were mapped to Recents click.
* **Clipped Screenshot Backdrop Blur for Popups & Sub-Dialogs**: Removed `if (!isFlyout)` bypass in `DeckGlassCardSurface`, allowing `ClippedFallbackBlurLayer` to render under `DeckDialog`, Slider Calibration, and Profile Naming flyouts on OEM devices lacking native SurfaceFlinger cross-window blur (such as Transsion Infinix XOS), eliminating cheap unblurred transparency.
* **Cockpit Gear Picker Scrollbar Clamping**: Fixed thumb pill overshoot in `CockpitGearPickerFastScroller` by properly clamping vertical travel within `[0f, sideBarHeight - thumbHeight]`, completely preventing the scroll pill from bleeding out at the top or bottom of the track.
* **Eye-Level Alphabet Fast-Scroll HUD**: Moved the fast-scroll letter badge overlay in `CockpitGearPickerScreenContent` and `CockpitGearPickerFastScroller` from vertical center to `Alignment.TopCenter` with 48dp top padding, placing the active alphabet indicator at natural eye level on tall displays without thumb obstruction.
* **Category Cruise Menu Hierarchy**: Swapped the order of Split actions to place `Split with...` above `Split screen` in `LightspeedCruiseOverlayAppMenu`.
* **Acrylic Theme Optical Purity**: Removed residual circular orange/coral radial shader (`0xFFFF7043`) positioned at middle-left (`0.25w, 0.35h`) in `DeckOpticalCacheBuilder`, restoring authentic neutral frosted acrylic glass with tactile micro-noise.
* **Adaptive Theme Dynamic Color Invariance**: Removed forced custom color tint overrides from Adaptive theme across `LightspeedTheme`, `DeckSurfaceModifier`, `DeckStylePickerComponents`, and `StyleLabRenderer`. Hid the color tint section in `DeckThemeCustomizationDialog` for Adaptive theme and preserved pure system Material You wallpaper palette (`colorScheme.primary` and `colorScheme.surfaceContainerHigh`), eliminating blank primary buttons, missing slider thumbs, and invisible action icons.
* **Glass Lava Transparent Text & Empty Action Chips**: Resolved invisible text and icons on active gesture sub-chips (`DeckSubChip`) and action labels by enforcing `alpha >= 0.88f` on readable tint colors, migrating "Colorless" tint from `0x00FFFFFF` to `0xFFFFFFFF`, and normalizing any legacy zero-alpha custom tints.
* **Glass Lava Backdrop Blur Chaining**: Chained hardware blur effect (`RenderEffect.createBlurEffect`) into `LiquidRefractionLayer` runtime shader, eliminating unblurred background widgets and bright home screen cards from showing through Central Command.
* **Screenshot Workflow & Git Invariance**: Ignored root-level ephemeral review screenshots in `.gitignore` and removed historical tracked screenshots, preventing screenshot multiplication and accidental commits during deployment/sync.

### Changed
* **Sub-Deck & Modal Flyout Width Hierarchy**: Proportioned floating secondary popup windows (`SliderCalibrationFlyoutDialog`, `ProfileNamingDialog`, and `DeckDialog` modal guards/confirmations) to a compact 78–80% width (`max = 330–350dp`), providing an authentic optical depth margin and visual layering over Central Command's 96% master deck.

### Added
* **Theme Unification Phase 1.5 Progress**: Migrated all raw `AlertDialog` instances outside `LiquidGlassDialogControls.kt` to `DeckDialog` across `CentralCommandDialogs`, `SidebarAttitudeSheet`, `FlightControlDeckComponents`, `WatchdogComponents`, `GestureHoldConfigComponents`, `GestureScrubSliderComponents`, `NauticalMooringRope`, `SidebarTabAccordion`, and `SystemOverrideDisplayMetrics`. Migrated standard cards to `DeckCard` with `tint` support for visual fill preservation across 11 settings sections. Tightened `check_theme.sh` CI drift guards to enforce strict `AlertDialog` and documented `Card` container isolation.
* **Theme Unification Phase 1.5 Architecture & Shared Controls**: Created `DeckControls.kt` providing standardized `DeckDialog`, `DeckCard`, and `DeckButton` components that react dynamically to `LocalDeckTheme.current`. Migrated initial raw `AlertDialog` and `Card` containers across `CentralCommandDialogs`, `HudSyntheticGravitySection`, `HudVaultExperimentalComponents`, and `HudHardwareRefuelingComponents`.
* **Stranded in Space Guidebook Screenshot Blur Workaround**: Upgraded `GuidebookBottomSheet.kt` with `ClippedFallbackBlurLayer` to use the physical 1:1 window hole blur workaround matching `SidebarAttitudeSheet`.
* **Universal Color Tokens (`LightspeedTokens.kt`)**: Centralized ~60 raw `Color(0x...)` literals across all settings sections, dialogs, widgets, and priority modals (`WatchdogComponents`, `TacticalWidgetPickerModal`, `RefuelingWidgetControls`, `RefuelingCustomizationModal`, `TacticalFlyoutActivity`, `SidebarAttitudeSheet`, and all `CockpitGearPicker*` rows) into typed, semantic constants with 100% hex equality.
* **Multi-Domain Theme Scaffolding (`ThemeDomains.kt`)**: Introduced `ThemeDomain` (`COMMAND`, `COCKPIT`, `CRUISE`) and `DomainPalette` architecture, wiring `LocalDomainPalette` providing `CockpitPalette` into `CockpitDialogActivity` and `CockpitGearPickerActivity`.
* **Automated Theme Drift Guard (`scripts/check_theme.sh`)**: Added CI/pre-flight verification script and checklist invariant in `REFACTORING_AUDIT_PROMPT.md` guaranteeing zero raw `Color(0x...)` regressions or unapproved dialog containers in `settings/`.
* **Translucent OEM Compatibility Shield Dialog (`CentralCommandDialogs`)**: Migrated `OemCompatibilityDialog` from an opaque `surfaceVariant` card to `lsColors.dialogSurface` with live hardware `FLAG_BLUR_BEHIND` and themed rounded corners, harmonizing its optical translucency and backdrop blur with the hold timeout trigger delay dialog.
* **Top Rim Specular Gleam Gradient (`LightspeedCruiseOverlayAppMenu`)**: Replaced the abrupt 55% rectangular glare capsule with a smooth, full-span top rim specular sweep using a `LinearGradient` that fades gracefully at both curved ends, completely eliminating the cutoff "half line" artifact across flyout menus and edit panels.
* **Full Theme Harmony Across Category Window & Grid (`LightspeedCruiseOverlayDraw` & `LightspeedCruiseOverlaySettingsButton`)**: Connected the category cruise screen backdrop, app tile cards, active docking ring, and settings gear button directly to `DeckThemeManager.getSnapshot(context)`. The entire category window now dynamically reflects the selected theme's panel tint, border strokes, and accent colors instead of hardcoded dark navy.
* **Themed Category Edit Mode Controls (`LightspeedCruiseOverlayEditMode`)**: Synchronized slot dividers, drag highlight card, drop spot indicators, reorder arrows, and bottom action buttons ("Save", "A-Z", "Most used", "Default") to the active theme's palette.
* **Pure Liquid Optics for Glass Lava & Liquid Jelly (`DeckThemeSnapshot` & `DeckSurfaceModifier`)**: Removed procedural noise grain (`noiseStrength = 0.0f`) from Glass Lava (`prism`) and Liquid Jelly (`jelly`) for 100% silky-smooth, crystal-clear liquid glass refraction, while preserving fine frosted grain exclusively for Frosted and Acrylic styles.
* **Inset Rounded Dropdown Selection Highlights (`DeckSurfaceModifier` & `GestureActionMethodControl`)**: Inset dropdown item rows with 4-6dp padding and 8dp corner radius clipping, completely preventing sharp 90-degree highlight corners from bleeding past the rounded outer perimeter of flyout windows.
* **Translucent Canvas Overlay Flyouts & Category Edit Mode (`LightspeedCruiseOverlayAppMenu` & `LightspeedCruiseOverlayEditMode`)**: Eliminated the interior-filling 63% black shadow layer and adjusted substrate opacity from 88-96% opaque to authentic theme translucency (38% for Glass Lava, 46% for Liquid Jelly), allowing underlying app icons and wallpaper to softly show through with diagonal caustics and specular gleams.
* **Physical 1:1 Screenshot Cropping (`CentralCommandBackdropBlur` & `LiquidRefractionLayer`)**: Replaced full-screen negative-offset image drawing with true physical sub-rectangle cropping (`srcOffset`, `srcSize`, `dstOffset.Zero`, `dstSize`) mapped directly from physical `displayMetrics`, completely eliminating squished backgrounds, clock displacement, and scaling distortion across decks and cards.
* **In-App Real Window Blur Behind Popups & Flyouts (`DeckSurfaceModifier` & `SliderCalibrationDialog`)**: Bypassed fallback wallpaper screenshot rendering for flyouts and modal dialogs (`isFlyout = true`), switching them to native hardware `FLAG_BLUR_BEHIND`. SurfaceFlinger blurs the live app content directly underneath in real-time, preventing home screen wallpaper bleeding into dialogs while rendering authentic translucent glass optics on top.
* **Seamless Diagonal Optical Caustics (`DeckOpticalCacheBuilder` & `LightspeedCruiseOverlayAppMenu`)**: Replaced localized circular radial shaders with smooth, full-span diagonal linear glass refraction gradients across Glass Lava (`prism`), Liquid Jelly (`jelly`), and Cyber Neon (`neon`), completely eliminating blotchy circular colored blobs on flyout menus and edit panels.
* **Flyout Dialog Backdrop Isolation (`DeckSurfaceModifier` & `LightspeedTheme`)**: Excluded `ClippedFallbackBlurLayer` from modal floating dialogs (`isFlyout = true`) and elevated dialog substrate opacity to 94%, preventing full-screen desktop wallpaper snapshots from being drawn misaligned or shrunken inside popup bounds and ensuring pristine text readability.
* **Hardware Window Blur Behind Dialogs & Dropdowns (`DeckSurfaceModifier`)**: Attached native `FLAG_BLUR_BEHIND` and `blurBehindRadius` to dialog windows (`DeckGlassCardSurface`) and popup windows (`DeckThemedDropdownMenu`) on Android 12+ (API 31+), ensuring underlying text and UI elements are physically blurred by the GPU rather than visually clashing.
* **Authentic Light Deflection for Glass Lava & Liquid Jelly (`DeckOpticalCacheBuilder`)**: Added authentic molten incandescent ember deflection caustics (`lavaPaint`) and cool spectral dispersion pockets (`secondaryPocketPaint`) for Glass Lava (`prism`), alongside the electric sky-blue to soft violet radial body and glossy specular highlight caps for Liquid Jelly (`jelly`).
* **High-Contrast Legible Flyout Substrates (`isFlyout` in `DeckOpticalCacheBuilder`)**: Implemented high-contrast buffered substrate floor (`0.76f - 0.88f`) and guaranteed specular highlight caps for dropdown menus, slider calibration dialogs, and mini popup windows, ensuring text and badges are crystal-clear and readable against underlying content.
* **Unified Canvas Flyout Optics (`drawDeckThemedFlyoutSurface` in `LightspeedCruiseOverlayAppMenu`)**: Unified overlay app long-press menus, category switcher flyouts, and edit mode panels with ambient elevation drop shadows, authentic themed light deflection, specular glare caps, and crisp themed borders over the live app grid.
* **Compact Slider Calibration Dialog Geometry (`DeckGlassCardSurface`)**: Fixed excessive window height by replacing `.fillMaxSize()` with `.matchParentSize()` on decorative optical layers, allowing slider flyouts to tightly wrap around content.
* **Unified 12-Theme Optical Dropdown Menus (`DeckThemedDropdownMenu` & `DeckSurfaceModifier`)**: Replaced all 15+ standard flat Material 3 dropdown menus across Central Command (telemetry routing, action modes, duration pickers, orientation policies, and tool selectors) with `DeckThemedDropdownMenu`, rendering every dropdown popup with the authentic optical glass stack of the selected theme (refractive borders, specular highlights, ambient glow, and translucent glass substrate).
* **Theme-Aware Overlay Flyout Menus & Edit Panels (`LightspeedCruiseOverlayAppMenu` & `LightspeedCruiseOverlayEditMode`)**: Connected the app long-press menu, category switcher, and edit mode panels to `DeckThemeManager.getSnapshot(context)` so they dynamically render in the active theme's translucent substrate, tint wash, corner geometry, specular glare cap, and refractive borders instead of hardcoded opaque slate.
* **Translucent Themed Dialogs & Flyouts (`SliderCalibrationDialog`, `ProfileNamingDialog`, `LightspeedTheme`)**: Wrapped slider calibration and profile naming flyouts in `DeckGlassCardSurface` and adjusted Material 3 surface containers in `LightspeedTheme` to inherit `snapshot.panelAlpha` and theme borders, replacing opaque boxes with authentic translucent glass.
* **Universal In-Place Theme Customizer (`DeckThemeCustomizationDialog`)**: Replaced legacy detached tuner with an authentic live in-place customization dialog where the dialog surface itself renders the theme being tuned and updates in real-time as sliders are adjusted. Includes Defaults restoration and unsaved changes discard warning.
* **Per-Theme Multi-Profile Engine (`ThemeProfileManager`)**: Full custom optical profile management (saving, naming, switching, renaming, deleting) for all 12 Deck themes stored persistently in `SharedPreferences`.
* **Per-Theme Customization Persistence (`ThemeCustomizationManager`)**: Real-time persisted adjustments for surface opacity, blur radius, corner radius, rim border stroke, ambient bloom/glow, specular gleam, and custom color tint infusion.
* **Authentic Jelly Optical Glass Engine (`DeckOpticalCacheBuilder` & `DeckThemeSnapshot`)**: Redesigned Jelly theme optics to match mock aesthetics with rich electric-blue to soft violet radial substrate, multi-stop glossy highlight cap, puffy bevel rim, and soft inner cushion.
* **Native AGSL Liquid Glass Lens Engine (`LiquidRefractionLayer`)**: Implemented high-performance AGSL chromatic refraction shader for Glass Lava (Android 13+ / API 33+), utilizing the accessibility display snapshot fallback to render authentic lens distortion, bevel refraction, and clean specular gleam with zero artificial blur or blue hue.
* **Unified 12-Theme Optical Glass Engine (`DeckSurfaceModifier` & `DeckOpticalCacheBuilder`)**: Unified all 12 Central Command themes across all floating UI decks, dialogs, and panels (`FloatingOverlayContainer`, `CentralCommandDeckDialog`, `BatteryExemptionDeckDialog`, `LiquidGlassCustomizationDialog`, `CockpitGearPickerActivity`, `GuidebookBottomSheet`) with zero-GC cached 3D depth sheets, dual-tone neon halo bloom, molten Glass Lava pockets, specular streaks, and refractive borders.
* **Miniature Live Optical Glass Theme Picker**: Upgraded `DeckStylePickerGrid` to render real miniature live glass preview cards instead of flat dot chips, showing the actual backdrop tint, specular sweep, and refractive glow for all 12 styles.
* **Interactive Style Lab**: Tapping any theme preview card in `StyleLabPreviewDialog` immediately applies and persists the theme, complete with live active badge tracking.
* **Card-Clipped Blur Lens (`CentralCommandBackdropBlur`)**: Replaced full-screen backdrop blur with card-clipped blur (`ClippedFallbackBlurLayer`), keeping the background sharp outside the floating panel bounds while blurring within the rounded glass card.
* **Scroll actions with method choices**: Scroll to top and Scroll to bottom actions with method choices (Automatic, Move to position, Page scroll, Step scroll, Swipe) and per-app method assignment (long-press a method).
* **Swipe scroll method**: The Swipe method for simulated finger scroll flings with boundary checks and front app verification.
* **Per-app method assignment for actions**: Per-app method assignment for Pop-up, Split and Close app actions.
* **Category reordering by drag**: Category reordering by drag on the Page tab.
* **Pop-up window in app menu**: Pop-up window row in the long-press app menu.
* **App menu action method synchronization**: App menu Pop-up and Split rows follow the Central Command method choices.
* **Edit panel Page and HUD tabs**: Edit panel Page/HUD tabs with theme-colored styling.
* **Pinned page gear button**: The gear button at the top right opens edit mode (it used to open Central Command). Setting "Gear button on the left" (System Override screen) moves the gear to the top left.
* **Edit mode panel**: Holding an empty spot of the app area or tapping the gear opens a panel with Page and HUD tabs. On the Page tab: reorder categories with up/down arrows, long-press and drag, A-Z, Most used (needs Usage access), Default. Changes are a draft; Save is the only thing that saves; tapping outside with unsaved changes asks Save / Discard / Keep editing.
* **Top strip hold for HUD tab**: Holding the empty top strip opens edit mode on the HUD tab (placeholder "HUD options coming soon"). It no longer opens Central Command. Central Command is reached by pulling past the bottom category in the Category Cruise.
* **App long-press menu**: Long-press an app on the pinned page opens a menu with Open, App info, Split screen, Pop-up window, Change category (remembered per app and overrides the automatic categorizer).

### Changed
* **Retirement of Legacy Theme Systems (`SettingsTheme`, `DeflectorTabBody`, `FlightControlDeckComponents`)**: Migrated all remaining callers from `rememberDeckBackdropVisuals` and `rememberDeckGlassVisuals` to `LocalDeckTheme.current`, eliminated the obsolete 4-button backdrop theme row, converted the System Overrides dialog to `DeckGlassCardSurface`, and purged 285 lines of legacy boilerplate (`DeckGlassTheme`, `DeckBackdropTheme`, `DeckGlassVisuals`, `DeckBackdropVisuals`).
* **Modular Cockpit Gear Picker Architecture (`CockpitGearPickerActivity`)**: Decoupled monolithic 1,117-line app/action gear picker into 7 focused modules strictly under 360 lines: `CockpitGearPickerActivity.kt` (212 lines), `CockpitGearPickerScreenContent.kt` (352 lines), `CockpitGearPickerHeaderSection.kt` (244 lines), `CockpitGearPickerListContent.kt` (316 lines), `CockpitGearPickerSelectedTray.kt` (156 lines), `CockpitGearPickerFastScroller.kt` (211 lines), and `CockpitGearPickerSubDialogs.kt` (119 lines).
* **Modular Theme Customization Dialog Architecture (`LiquidGlassCustomizationDialog`)**: Decoupled monolithic 831-line optical theme customizer into 4 focused modules strictly under 330 lines: `LiquidGlassCustomizationDialog.kt` (306 lines), `LiquidGlassDialogControls.kt` (323 lines), `LiquidGlassPresetColorSection.kt` (286 lines), and `LiquidGlassOpticsSlidersSection.kt` (149 lines).
* **Modular Preferences Architecture (`LightspeedPreferences`)**: Decoupled monolithic 1,140-line central preferences hub into 6 single-responsibility modules strictly under 400 lines: `LightspeedPreferences.kt` (393 lines), `LightspeedPreferencesAccessors.kt` (369 lines), `LightspeedPreferencesDefaults.kt` (225 lines), `LightspeedPreferencesPerimeterAccessors.kt` (121 lines), `LightspeedPreferencesContracts.kt` (109 lines), and `LightspeedPreferencesFlows.kt` (56 lines), maintaining 100% zero-regression backward compatibility via interface delegation.
* **Modular System Override Architecture (`SystemOverrideComponents`)**: Decoupled monolithic 1,176-line system override deck into 5 focused modules strictly under 400 lines: `OverrideSliderRow.kt` (155 lines), `SystemOverrideBackConflictCard.kt` (143 lines), `SystemOverrideLockscreenSection.kt` (377 lines), `SystemOverrideDisplayMetrics.kt` (316 lines), and `SystemOverrideComponents.kt` (325 lines).
* **Modular Gesture Architecture (`GestureComponents`)**: Decoupled monolithic 1,435-line gesture mapper into 7 single-responsibility modules strictly under 400 lines for enhanced AI navigability: `DeckSubChip.kt` (122 lines), `GestureHoldConfigComponents.kt` (221 lines), `GestureScrubSliderComponents.kt` (397 lines), `GestureHudAndMediaDropdowns.kt` (213 lines), `GestureActionMethodControl.kt` (188 lines), `GestureActionDropdowns.kt` (285 lines), and `GestureComponents.kt` (308 lines).
* **Refueling Bay Stardate Clock**: Upgraded the Refueling Bay mission clock format from `ORBITAL` (`D292`) to `STARDATE` using the standard Solar Calendar Stardate format (`SD YYYY.DDD · HH:mm`, e.g., `SD 2026.292 · 16:38`).
* **Central Command Theme #1 (`Glass Lava`) & Theme #2 (`Liquid Jelly`)**: Renamed Central Command glass preset display names to `Glass Lava` (`styleId = "prism"`) and `Liquid Jelly` (`styleId = "jelly"`).
* **App categorizer**: 10 languages (English, Arabic, Hindi, Spanish, Portuguese, French, German, Russian, Turkish, Indonesian) and about 180 well-known apps; call-recorder phrases.

### Fixed
* **Cockpit Gear Set Switching Stutter (`CockpitGearRepository`, `DeepSpaceRenderer`, `CruiseFlankHandler`)**: Eliminated frame drops when switching cockpit profiles and gear sets by (1) introducing asynchronous background pre-warming (`prewarmGearCache`) that resolves ring packages, set labels, and icons before interaction, (2) caching custom profile set names in an in-memory concurrent map to eliminate synchronous `prefs.getString()` calls inside `onDraw()`, and (3) debouncing profile persistence to gesture release (`ACTION_UP`) rather than executing synchronous SharedPreferences disk writes on every intermediate tick of the 3D cube rotation carousel.
* **Central Command Tab Swipe & Tap Stutter (`CentralCommandConfig`)**: Eliminated frame drops during tab swiping and tab clicking by (1) increasing pager viewport precomposition (`beyondViewportPageCount = 2`) so all 3 tabs (Left Deflectors, HUD Strip, Right Deflectors) remain composed in memory rather than being destroyed and recreated mid-swipe, (2) debouncing preview beacon synchronization to only execute when scroll animations have fully settled (`!isScrollInProgress`), (3) removing redundant duplicate preview sync effect blocks, and (4) eliminating unsolicited main-thread `safeReloadPreferences()` calls during tab page transitions.
* **Central Command backdrop blur on XOS / OEM firmware (`CentralCommandBackdropBlur`)**: Added an Accessibility screenshot + hardware GPU `RenderEffect` blur fallback when the device firmware disables `SurfaceFlinger` cross-window blur (`ro.surface_flinger.supports_background_blur` unset, such as Transsion XOS 16 on Infinix Note 50s 5G+), and fixed `FloatingOverlayContainer` overriding selected Central Command themes with a double-stacked opaque grey gradient.
* **MacroDroid force-stop**: Lightspeed no longer force-stops MacroDroid when it starts, which switched off its accessibility services.
* **Edit mode touch release**: Edit mode no longer closes when the finger lifts after the hold.

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
* **Glass Chromatic Customization**: Choose Adaptive Color (your system's dynamic colors) or a Custom Palette, with a Tint Infusion Depth slider (5%–100%), 9 preset swatches, and a custom hex dialog with live preview. The tint now flows through the glass body, GPU caustics (AGSL shader, Android 13+), rim dispersion and shimmer.
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
