# ⚡ Lightspeed Changelog

All notable changes to the Lightspeed Gesture Launcher & Workspace are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.0] - 2026-10-27

### Added & Enhanced
* **Cybernetic Trackpad (`LightspeedBotTrackpadOverlay.kt`, `CyberneticTrackpadAccordion.kt`)**:
  * Precision floating cursor navigation with sub-pixel kinetic physics, customizable acceleration curves, dynamic haptic resistance, and gesture boundary awareness.
* **Cockpit Hangar Deck (`DeepSpaceHangarControlsDraw.kt`, `LightspeedCruiseOverlayAppMenu.kt`)**:
  * Unified quick-access cockpit avionics tray for instant switching between pinned gear, shortcuts, and deep system workflows.
* **Sonic Deck & Privileged Background Daemon Guard (`SonicDeckManager.kt`, `SonicVoiceIsolationEngine.kt`, `HudExperimentalLabsSection.kt`)**:
  * Per-app floating volume mixer and privileged Shizuku audio pipeline.
  * Automatic resource guard: terminating Sonic Deck immediately cleans up and unbinds the background daemon process, freeing ~100–150 MB of system RAM.
  * Prominent telemetry warnings and persistent confirmation modals to prevent unintended power or memory consumption.
* **Vista Deck & Display Sovereignty Simplification (`GestureActionSubChips.kt`, `CruiseScrubEngine.kt`)**:
  * Integrated brightness and optics control panel with customizable auto-reset triggers (Screen Off, App Switch, Recents Close).
  * Video Override architecture: transparent overlay activates strictly outside standard ranges (overdrive boost > 100% and sub-zero Extra Dim floor) to override stubborn media players, leaving native Android display controls 100% uninhibited in the 1%–100% range.
  * Fixed Extra Dim 90% clamping with haptic feedback to prevent cyclic resets.
* **Tactile & Haptics Engine (`HapticsSubSection.kt`, `HapticDomainSlider.kt`, `LightspeedHapticEngine.kt`, `GestureScrubHapticsSelector.kt`)**:
  * Per-domain haptic configuration across all Lightspeed surfaces: System baseline, Central Command UI, Deflector edge triggers, Continuous scrub, Cockpit rotary spinning, Gear set jumps, Category Cruise cylinder scroll, Spatial layer transitions, and Cursor/app focus — each with independent snap-stop precision sliders.
  * New `HapticDomainSlider.kt` component: horizontal snap-stop profiles (Off → Soft → Crisp → Heavy) with vertical fine-gearing for sub-step custom intensity tuning (`custom_XX`).
  * Dedicated sub-accordions for Cockpit Hangar & Gears and Category Cruise group all tactile controls in context.
  * ERM actuator compensation in `LightspeedHapticEngine.kt` for tablets: extended pulse durations overcome motor inertia that previously caused silent haptic misfires on ERM hardware (e.g. Lenovo TB305XU).
  * Global Haptic Pulse Throttle cooldown slider prevents motor saturation during rapid consecutive gestures.

### Fixed
* **Category Cruise App Menu Uninstall (`AndroidManifest.xml`, `LightspeedCruiseOverlayAppMenu.kt`)**:
  * Declared `REQUEST_DELETE_PACKAGES` permission and routed package removal through `Intent.ACTION_UNINSTALL_PACKAGE`, ensuring uninstalls fire reliably across Android 9 through Android 15+.
* **Left Wing Deflector Scrub Symmetry (`LightspeedLeftWingOverlay.kt`)**:
  * Symmetrized left and right deflector scrubbing with incremental accumulator tracking for flawless single-pixel sensitivity.
* **Master Stealth Cloak Quick Settings Tile (`MasterCutoffTileService.kt`, `LightspeedMasterCutoff.kt`, `LightspeedTileCloakActivity.kt`)**:
  * Single Quick Settings tile for the whole app. Tap = cold shutdown / 1-tap resurrection with haptic tick and Armed/Cloaked toast; long-press opens the Cloak Matrix to pick extra accessibility services (password managers, automation tools) to disable together with Lightspeed so banking and DRM apps launch cleanly.
  * Removed redundant tiles: the duplicate "Lightspeed" (`SidebarTileService`) tile, the Deflectors tile (`DeflectorTileService`), and the four Space Pod tiles (`SpacePodTileService`, `SpacePodTileService1..4`). Deflectors remain toggleable in-app.
* **Astrogation Airlock Dual Time (`LightspeedConsciousAirlockOverlay.kt`)**:
  * The 5-second mindful delay now shows both **App Time** and **Sector Time** (total screen time today across every app in the same category, honoring per-app category overrides via `LightspeedAppCategorizer`).

### Changed
* **Split Downloads (`app/build.gradle.kts`)**:
  * Per-ABI split APKs (`arm64-v8a`, `armeabi-v7a`) replace the universal APK; the "arm64 only" README note was removed.
* **About & Feedback (`AboutFeedbackSection.kt`)**: Added a **Share Lightspeed** button (system share sheet with a short pitch and the GitHub link) at the top, and moved **Buy me a coffee** and **Lightspeed Infinity** to the bottom. Removed the unfinished "Hall of the Universe" button while the combined credits/license/hall-of-fame screen is redesigned.

* **Video Takeover Exclamation Info Modal & Display Sovereignty Guidance (`DeckSubChip.kt`, `GestureActionSubChips.kt`)**:
  * **Exclamation Icon Inside Button**: Added `trailingIcon` and `onTrailingIconClick` support to `DeckSubChip` with a circular `!` (PriorityHigh) button nested within the Video Takeover toggle pill.
  * **Optical Glass 4-Tier Display Architecture Modal**: Tapping the `!` button opens a tactical `DeckDialog` detailing the display architecture:
    * **⚡ 101% – 120% • Video Takeover Boost**: Active accessibility overlay window only above 100% to overpower stubborn media players (Jellyfin, VLC, MX Player).
    * **☀️ 1% – 100% • Native Display Sovereignty**: Zero overlay interference; direct native Android brightness so in-app gestures, Quick Settings slider, and Lightspeed operate side-by-side with zero conflicts.
    * **🌙 Below 1% • Seamless Extra Dim**: Sub-zero attenuation (10% to 90%) below hardware panel minimum floor.
    * **↺ Floor Reset • Display Sovereignty Reset**: Clearing stuck video window overrides, restoring native auto-brightness.
* **Deflector Tab Card Flow Alignment (`DeflectorTabBody.kt`)**:
  * Moved "Unify Upper & Lower Actions" toggle card (`UnifyFlankActionsCard`) to be the **3rd card** instead of the 2nd card in Deflector tabs, placing "Deflector Landscape Geometry" as the 2nd card.
* **Sonic Deck Master Toggle Disarm, Native Reset & State Restoration (`SonicDeckManager.kt`, `HudExperimentalLabsSection.kt`, `LightspeedAccessibilityServiceInit.kt`)**:
  * **Master Toggle Off (Disarm & Reset to Native)**: Toggling Sonic Deck off from Experimental Labs stashes Cinema AI status, immediately stops `SonicVoiceIsolationEngine`, resets all per-app volume overrides to native unity (1.0f) via `SonicAppVolumeEngine.stopMonitoring()`, and clears privileged channel routing.
  * **Master Toggle On (Restore)**: Re-enabling restores Cinema AI dialogue isolation if previously active, re-enables volume monitoring, reapplies custom app volumes to active players, and restores privileged routes.
  * **Boot Protection**: Guarded startup monitoring in `LightspeedAccessibilityServiceInit` to keep audio systems completely native when Sonic Deck is disabled.

### Added & Enhanced
* **Sub-Zero Display Sovereignty Reset & Pure Native Range Scrub (`CruiseScrubEngine.kt`, `LightspeedDisplayWellnessResetEngine.kt`, `deploy-nightly.sh`)**:
  * **Sub-Zero (< 1% / 0%) Sovereignty Reset**: Dragging below 1% (or below 0% when Extra Dim reaches limit/off) triggers a full Display Sovereignty Reset. Immediately releases any active video overlay window, resets system display brightness mode to native (`Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC`), and executes privileged `cmd display reset-brightness-configuration` via Shizuku. Renders `↺ DISPLAY RESET: SOVEREIGNTY` on the HUD with haptic confirmation.
  * **Pure Native Standard Range (1%..100%)**: Overlay window is strictly cleared throughout standard brightness levels, ensuring neither Lightspeed nor video player overlays hijack the display. Android's quick settings slider, in-app video player gestures (Jellyfin, VLC), and Lightspeed scrub operate side-by-side with zero conflicts.
  * **Video Takeover Overdrive Zone (> 100%)**: Confined overlay window activation strictly to the boost range (101%..120%) where hardware display brightness must be overtaken.
  * **Multi-Device Wireless Deployment Hardening (`deploy-nightly.sh`)**: Deduplicated mDNS TLS endpoints to prevent concurrent install hangs on the same device, added 60-second install timeouts, and incorporated automated pre/post installation cleanup of stale `sonic_service` daemons.

### Fixed & Overhauled
* **Hardware System Brightness Sovereignty, Overlay Hijack Removal & Samsung 100% Fix (`LightspeedDisplayWellnessResetEngine.kt`, `CruiseScrubEngine.kt`, `CruiseMacroGestureHandler.kt`, `DeflectorHoldGestureDispatcher.kt`, `ExpandedBrightnessWellnessSection.kt`, `ActionDispatcherSonicDisplay.kt`)**:
  * **Eliminated Persistent Overlay Window Hijacking**: Cleared `videoOverrideView` during all standard brightness scrubbing (1..255), completely preventing the fullscreen `TYPE_ACCESSIBILITY_OVERLAY` from sitting permanently above the window stack. This instantly restores:
    1. In-app video player brightness gestures (VLC, MX Player, mpv, YouTube).
    2. Android's native quick settings notification shade brightness slider.
    3. Persistent brightness retention across app switching, closing, and updates (saved directly to standard `Settings.System.SCREEN_BRIGHTNESS` and `screen_brightness_float`).
  * **Samsung Starting at 100% Fix**:
    1. Added `getSystemBrightness()` checking `screen_brightness_float` (0.0..1.0) before `SCREEN_BRIGHTNESS` (0..255), accurately retrieving live ambient/adaptive display levels on Samsung One UI and Android 9+.
    2. Fixed Shizuku `cmd display set-brightness` passing float values (e.g. `0.450`), stopping Samsung's DisplayPowerController from interpreting raw integer inputs (e.g. `128`) as float percentages > 1.0 that previously clamped brightness to 100%.
  * **Scoped Video Boost Zone**: Confined `videoOverrideView` strictly to the manual boost zone (> 100%), leaving standard 0..100% display backlight operations 100% clean and transparent.
* **Category Cruise App Menu Uninstall Option, Mindful Airlock Clarification & Cybernetic Trackpad Geometry Calibration Overhaul (`LightspeedCruiseOverlayAppMenu.kt`, `LightspeedConsciousAirlockOverlay.kt`, `CentralCommandConfig.kt`, `HudExperimentalLabsSection.kt`, `CyberneticTrackpadCalibration.kt`)**:
  * **App Drawer Menu Uninstall Option (`LightspeedCruiseOverlayAppMenu.kt`)**: Replaced the bottom-most "Shortcuts & Pin" context action with "Uninstall" (`PinnedMenuItem.UNINSTALL`), cleanly invoking Android's `Intent.ACTION_DELETE` (`package:target`) to uninstall apps directly from Category Cruise.
  * **Conscious Airlock Mindful Delay Clarification (`LightspeedConsciousAirlockOverlay.kt`)**: Clarified "Sector Time Today" to "Sector Time (Screen Time Today)" to eliminate confusion with category hold times, and updated sci-fi copy to mindful prompts ("Take a conscious breath... Are you sure you want to open this app?" and "Mindful pause complete. Ready to engage.").
  * **Cybernetic Trackpad Deflector Jump Fix (`CentralCommandConfig.kt`, `HudExperimentalLabsSection.kt`)**: Fixed the non-functional navigation arrow in Experimental Labs Cybernetic Trackpad. Replaced the self-cancelling `LaunchedEffect(pendingJump)` with a dedicated `jumpChannel` event flow (`triggerJump(targetTab)`), and initialized `trackpadFlankLeft` from `KEY_BOT_TRACKPAD_LEFT_ENABLED` so clicking the arrow reliably expands the trackpad accordion and scrolls directly to it in either Deflector tab.
  * **Cybernetic Trackpad Geometry & Calibration Two-Card Modularization (`CyberneticTrackpadCalibration.kt`)**: Split the 8 plain sliders into two dedicated, collapsible sub-accordion cards:
    * **Edge Trigger Strip**: Hosts "Vertical Offset", "Height", "Width", and "Opacity" with clutter-free labels.
    * **Three-Button Arm & Dome**: Hosts "Radius", "Dome Opacity", "Extension Reach", and "Arms Opacity".
    * **Lightspeed Signature Dotted Sliders**: Converted all 8 geometry controls to `PrefDottedSliderRow` with precision dotted tracks, tactile clickable value pills opening full manual-entry/profile calibration dialogs, and automated `OnSharedPreferenceChangeListener` triggering live `LightspeedBotTrackpadOverlay` reloads.
* **TCL & Android 15 Audio Sovereignty Pipeline: Channel Routing, Per-App Volume & Cinema AI Isolation Fix (`SonicVoiceIsolationEngine.kt`, `SonicIsolationUserService.kt`, `SonicPrivilegedAudioPipeline.kt`, `SonicChannelRouter.kt`, `GtcrnProcessor.kt`, `LightspeedAccessibilityServiceInit.kt`, `ISonicIsolationUserService.aidl`, `deploy-nightly.sh`)**:
  * **Eliminated Daemon Suicide Race Condition (`SonicVoiceIsolationEngine.kt`)**: Removed premature asynchronous `pkill -9 -f sonic_service` from `ensureBound()`, which previously killed newly launched `sonic_service` instances before their IPC handshake completed and caused `isBinding` to deadlock in a permanent `true` state. Added a 5-second binding timeout guard and dynamically resolved `versionCode` for Shizuku args.
  * **Cinema Dialogue AI Isolation Hidden Reflection & Robust Loopback (`SonicPrivilegedAudioPipeline.kt`, `GtcrnProcessor.kt`)**: Added `isAccessible = true` across all hidden `AudioMixingRule.Builder` (`addRule`, `excludeMixRule`), `AudioMix.Builder`, and `AudioRecord.Builder` (`setInternalCapturePreset`, `addTag`, `setAudioAttributes`) reflections, eliminating silent `IllegalAccessException` crashes on Android 12–15. Added dual registration fallback between `registerAudioPolicyStatic` and `registerAudioPolicy`, and enhanced `GtcrnProcessor` to load `gtcrn.onnx` from application package context fallbacks.
  * **Acoustic Device Discovery & Static Unregistering (`SonicIsolationUserService.kt`)**: Replaced standard `getDevices` with `AudioManager.getDevicesStatic(GET_DEVICES_OUTPUTS)` inside `findTargetDevice` so shell daemons detect audio outputs (Speaker, Bluetooth A2DP/BLE, Wired/USB) without a WindowContext. Aligned unregistering with `unregisterAudioPolicyAsyncStatic`.
  * **Live Multi-Audio Focus In-Memory Activation (`SonicIsolationUserService.kt`, `SonicChannelRouter.kt`, `ISonicIsolationUserService.aidl`)**: Added `setMultiAudioFocus(enabled)` invoking `IAudioService.setMultiAudioFocusEnabled(boolean)` via system service binder, guaranteeing audio focus never ducks or pauses background playback on Android 15.
  * **Persistent Per-App Volume Playback Tracking (`SonicIsolationUserService.kt`)**: Registered an internal `AudioPlaybackCallback` in the privileged service to automatically reapply saved per-app volumes across player churn, song changes, or track pauses in real time.
  * **Dynamic Loopback Exclusion for Dedicated Output Routing (`SonicPrivilegedAudioPipeline.kt`, `SonicIsolationUserService.kt`)**: Implemented dynamic exclusion provider (`excludedUidsProvider`) in `SonicPrivilegedAudioPipeline`'s loopback mixing rules. Active routed app UIDs (`activeRoutedUids.keys`) are explicitly excluded via `excludeMixRule(RULE_MATCH_UID, uid)`, preventing Cinema AI Voice Isolation loopback (`ROUTE_FLAG_LOOP_BACK` on `USAGE_MEDIA`) from swallowing routed audio streams (e.g. Jellyfin -> Speaker) and redirecting them to Bluetooth.
  * **Service Death Link & Boot Context Synchronization (`SonicVoiceIsolationEngine.kt`, `SonicChannelRouter.kt`, `LightspeedAccessibilityServiceInit.kt`)**: Linked `IBinder.DeathRecipient` on Shizuku UserService to reset `isBound` and auto-recover on service death, and initialized `SonicChannelRouter.applyRoutingRules(this)` at startup to prevent null context drops. Prioritized `TYPE_BLUETOOTH_A2DP` over `TYPE_BLUETOOTH_SCO` in device discovery.
  * **Automated Sticky Shizuku Reconnection (`LightspeedAccessibilityServiceInit.kt`)**: Added `Shizuku.addBinderReceivedListenerSticky` to automatically bind and revive `SonicIsolationUserService` as soon as Shizuku starts or reconnects.
  * **Multi-Device Deployment Expansion (`deploy-nightly.sh`)**: Added `./deploy-nightly.sh all` flag deploying and verifying physical installation across all connected ADB targets (TCL `9469X`, Infinix, Samsung, Lenovo).
* **Master QS Tile Stealth Cloak Deck, Watchdog Immunity & Native Edge Back Recovery (`LightspeedTileCloakActivity.kt`, `LightspeedMasterCutoff.kt`, `SidebarTileService.kt`, `SpacePodLongClickActivity.kt`, `LightspeedPreferencesPerimeterAccessors.kt`, `LightspeedBackupEngine.kt`)**:
  * Unified the Master Flight QS Tile (`SidebarTileService.kt`) with `LightspeedMasterCutoff.kt`: toggling the tile triggers cold cutoff and multi-service cloaking to bypass banking app accessibility detection or safely switch between Nightly and Stable builds.
  * **Native Edge Back Inset Recovery**: Automatically reverts Android's native back gesture sensitivity to `1.0` (`back_gesture_inset_scale_left/right = 1.0`) upon cutoff/cloak via Shizuku/Settings.Secure so edge navigation is immediately restored when deflectors disappear; restores deflector suppression scale (`sys_override_edge_gesture`, e.g. `0.0`) on resurrection.
  * **Watchdog Immunity Invariant**: Explicitly marks `pref_service_intentionally_stopped = true` upon cutoff, guaranteeing that `LightspeedWatchdogEngine`, `LightspeedCrashSentinel`, and `LightspeedRevivalReceiver` remain dormant and never fight the user while cloaked.
  * **Display Wellness Decoupling**: Master Cutoff disarms Lightspeed's custom Eye Care overlay, Shizuku SurfaceFlinger color matrix, Smart Pixels mesh, and Extra Dim, while leaving Android's native Night Light completely untouched.
  * **Long-Press QS Tile Cloak Matrix (`LightspeedTileCloakActivity.kt`)**: Long-pressing the Master QS Tile launches the tactical Cloak Matrix modal, allowing the user to multi-select which third-party accessibility services (e.g. Bitwarden, Tasker) to automatically disable and re-enable alongside Lightspeed. Exported and imported seamlessly via `LightspeedBackupEngine`.
* **Video Brightness Gesture Override & Fullscreen Window Sovereignty Fix (`LightspeedDisplayWellnessResetEngine.kt`, `CruiseScrubEngine.kt`, `ExpandedBrightnessWellnessSection.kt`)**:
  * Fixed an issue where Video Brightness Takeover was non-functional: previously, `setVideoOverride` attempted to set `screenBrightness` on a 24px-wide idle deflector view, which Android WindowManager ignores because brightness overrides are restricted to fullscreen windows.
  * Rebuilt `LightspeedDisplayWellnessResetEngine` to create and manage a dedicated, transparent, touch-through fullscreen overlay window (`MATCH_PARENT x MATCH_PARENT`, `TYPE_ACCESSIBILITY_OVERLAY`), allowing `screenBrightness` to take direct z-order precedence over VLC, MX Player, mpv, and YouTube window locks.
  * Wired `KEY_BRIGHTNESS_VIDEO_TAKEOVER` directly into `CruiseScrubEngine.executeBrightnessScrub`, allowing gesture brightness scrubbing to seamlessly transition into the `⚡ VIDEO OVERRIDE` boost zone (101%..120%) and take over video player brightness.
* **Cybernetic Trackpad Gesture Engine, Cursor Dome & Drag & Move (`LightspeedBotTrackpadOverlay.kt`, `LightspeedBotTrackpadDraw.kt`, `LightspeedBotEngine.kt`)**:
  * Eliminated the "huge glitch" where double-tapping or long-pressing on the trackpad erroneously triggered outer macro button recording (`executeArmLongPressRecord`) or opened `BotMacroEditorDialog`: strictly separated the trackpad dome hit-testing from macro arms (`rInner = radiusPx + 2dp`) so touches within the dome never trigger macro actions.
  * Implemented **Cursor Long-Press**: holding down on the trackpad dome for > 450ms injects a native long-press gesture (`injectLongPressOrDispatch`) at `(cursorX, cursorY)` with heavy tactile feedback.
  * Implemented **Double-Tap**: quick double-tapping on the trackpad dome injects a native double click (`injectDoubleTapOrDispatch`).
  * Implemented **Drag & Hold and Drag & Move**: double-tapping and holding, or long-pressing and moving, smoothly transitions into **Drag Mode**; moving the thumb steers the cursor and releases a continuous gesture swipe from drag start to destination upon finger lift.
* **Full-Screen Cursor Overlay & Display Metrics Clipping Fix (`FullDisplayOverlayHelper.kt`, `LightspeedBotTrackpadOverlay.kt`, `LightspeedAccessibilityService.kt`)**:
  * Fixed a bug where the cursor disappeared below half the screen (especially on tall 2460x1080 Infinix displays or after orientation switches): updated `createFullDisplayOverlayParams` to use `MATCH_PARENT, MATCH_PARENT` instead of static bounds dimensions that previously truncated the window to 1080px.
  * Replaced display metrics clamping in `onTouchEvent` with real display bounds (`getScreenBounds` via `maximumWindowMetrics` / `getRealMetrics`), and ensured `LightspeedBotTrackpadOverlay.reload(this)` is invoked on configuration changes.
* **Cybernetic Trackpad Experimental Labs Accordion Fix (`HudExperimentalLabsSection.kt`, `CyberneticTrackpadAccordion.kt`, `CentralCommandConfig.kt`)**:
  * Fixed the non-functional Cybernetic Trackpad sub-accordion in Experimental Labs: embedded the complete `TrackpadGeometrySubSection` and `TrackpadPhysicsSubSection` directly beneath the enable toggle with a Left/Right Deflector flank selector.
  * Made Deflector tab `CyberneticTrackpadAccordion` react immediately to pref changes (`remember(prefs.getBoolean(...), flankKey)`), and updated the teleport jump arrow to expand the accordion and scroll directly to the trackpad in either Deflector flank.
* **Audio Routing Sovereignty & Device Discovery Fix (`SonicIsolationUserService.kt`, `SonicChannelRouter.kt`)**:
  * Removed the blocking `audioManager.mode != AudioManager.MODE_NORMAL` check in `SonicIsolationUserService` that caused audio routing to fail silently during active playback or communication modes.
  * Made reflective method resolution for `AudioMixingRule.Builder` (`addMixRule` / `addMixRuleInternal`) and `AudioMix.Builder` (`setDevice`) robust with `isAccessible = true` fallback across Android 10 through 15.
  * Expanded `findTargetDevice` to cover all output devices including BLE headsets/speakers, Bluetooth SCO, Hearing Aids, safe speaker, and USB audio accessories.
  * Added proactive initialization and route re-application in `SonicChannelRouter.applyRoutingRules`.
* **Sonic Service Memory Leak, Process Duplication & Zombie Suicide Shield (`SonicVoiceIsolationEngine.kt`, `SonicIsolationUserService.kt`, `ISonicIsolationUserService.aidl`, `LightspeedAccessibilityService.kt`, `LightspeedMasterCutoff.kt`)**:
  * Fixed a critical issue where repeated calls to `ensureBound` (triggered by volume scrubbing or routing sync) spawned multiple duplicate `app_process` daemon instances (`:sonic_service`), each consuming ~200MB of RAM and surviving after Lightspeed was force-stopped.
  * Added atomic synchronization lock `bindLock` and `isBinding` guard in `SonicVoiceIsolationEngine.kt`, strictly preventing concurrent or redundant Shizuku `bindUserService` calls.
  * Implemented **Client Binder Death Recipient (`registerClientToken`)**: `SonicIsolationUserService` now monitors the Lightspeed client process via `IBinder.linkToDeath`. The moment Lightspeed dies, crashes, or is force-stopped, the daemon immediately suicides via `Process.killProcess(Process.myPid())` and `System.exit(0)`, making zombie survival impossible.
  * Added `pkill -9 -f sonic_service` pre-flight purge in `ensureBound` and `teardown` to automatically terminate any lingering orphaned processes before binding or when services unbind.
  * Replaced `SonicVoiceIsolationEngine.stop()` with full `teardown()` across `LightspeedAccessibilityService.onDestroy()` and `LightspeedMasterCutoff.applyCutoff()`.
* **Infinity Golden Lock Alignment for Gear Sets, Trackpad Styles & Cursor Shapes (`CyberneticTrackpadSubSections.kt`, `CyberneticTrackpadCalibration.kt`, `DeepSpaceHangarControlsDraw.kt`)**:
  * Aligned all items locked behind Lightspeed Infinity to match Central Command's golden lock visual aesthetic:
    * `TrackpadPillButton`: added `isLocked` support rendering `Icons.Filled.Lock` (10dp) in `accentGold`, golden borders, and golden text.
    * Cybernetic Chassis Styles & Cursor Shapes: removed plain unicode `∞` text, passed clean labels with golden lock indicators, and triggered warning haptics + Infinity dialog on click.
    * Cockpit Hangar Deck: updated locked gear sets (bays >= 2) and sculpted styles (`ORBIT`, `DMND`, `CRESC`, `TECH`) to display golden `🔒` indicators, gold tinted backgrounds, and gold borders.

### Fixed
* **Graceful Close & Recents Eviction Launcher Immunity Shield (`TopAppCloser.kt`, `TaskInspector.kt`, `RecentsFallbackCloser.kt`, `ElevatedTaskCloser.kt`)**:
  * Fixed a regression where triggering "Close App Gracefully & Remove from Recents" (`system:close_app`) while on the home screen evicted the default launcher (or evicted background apps behind the launcher).
  * Replaced the decoupled regex fallback in `TopAppCloser` and `TaskInspector` (`dumpsys activity top 2>&1 | grep -E 'TASK|ACTIVITY'`) with atomic `topResumedActivity` parsing (`ActivityRecord{... <pkg>/<act> t<id>}`), preventing task ID / package name mismatch.
  * Added home task recognition via `isLauncherTask` (`activityType == ACTIVITY_TYPE_HOME`, `CATEGORY_HOME` in `baseIntent`, and `LightspeedOrientationEngine.isLauncherPackage`), aborting immediately with `"Nothing to close here"` when on the home launcher.
  * Added a hard safety lock before `removeTask`, `am task remove`, or `am force-stop` that strictly refuses to close any detected launcher, system UI, or Lightspeed package.

### Added & Overhauled
* **Pre-Release Refactoring & 120Hz Zero-Allocation Audit Pass (`CockpitHangarTouchHandler.kt`, `CockpitHangarAccordionTouch.kt`, `LightspeedBotTrackpadDraw.kt`, `LightspeedStatusBarOverlayDraw.kt`, `LightspeedCruiseOverlayDraw.kt`, `LightspeedCruiseOverlayGearsDraw.kt`, `LightspeedPreferences.kt`, `DeepSpaceHangarControlsDraw.kt`, `CruiseFlankHandler.kt`, `CockpitGimbalConfigCache.kt`, `LightspeedCruiseOverlayMetrics.kt`)**:
  * Decomposed monolithic `CockpitHangarTouchHandler.kt` (from 765 lines down to 450 lines) by extracting accordion tab and sub-controls logic into `CockpitHangarAccordionTouch.kt` (353 lines), bringing all Kotlin files across the entire codebase strictly below 750 lines.
  * Hoisted `Path` allocations in `LightspeedBotTrackpadDraw.kt` (`cachedTrackpadPath`, `cachedWedgePath`, `cachedArrowPath`, `cachedDiamondPath`) and `RectF` allocations in `LightspeedStatusBarOverlayDraw.kt` (`tempSensorRect`), `LightspeedCruiseOverlayDraw.kt` (`cachedHudHeaderRect`), and `LightspeedCruiseOverlayGearsDraw.kt` (`cachedGearHeaderRect`) into reusable static/module properties for zero-allocation 120Hz gesture scrubbing.
  * Declared typed constant `KEY_GEAR_RETICLE_STYLE` in `LightspeedPreferences.kt` and eliminated raw string literals across `CockpitHangarTouchHandler.kt`, `LightspeedCruiseOverlayMetrics.kt`, `DeepSpaceHangarControlsDraw.kt`, and `CockpitGimbalConfigCache.kt`.
  * Replaced scattered raw `"pref_gear_physics_profile"` literals with `LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE`.
  * Relocated untracked research archives and mockup assets outside the repository root to ensure pristine workspace hygiene.
* **Cybernetic Trackpad Overhaul — 3-Button Macro Clicker, Bottom Default & Contoured Mockup Chassis (`LightspeedBotTrackpadOverlay.kt`, `LightspeedBotTrackpadDraw.kt`, `CyberneticTrackpadAccordion.kt`, `CyberneticTrackpadCalibration.kt`, `DeflectorTabBody.kt`, `DeflectorTabSectionCards.kt`, `LightspeedBotEngine.kt`, `LightspeedBotRecorderOverlay.kt`, `BotMacroEditorDialog.kt`)**:
  * Replaced the hardcoded 4-arm (`TAP/REPLAY`, `REC`, `EDIT`, `LOOP`) layout with a **Trackpad + 3 Independent Macro Buttons (`B1`, `B2`, `B3`)**:
    * **Trackpad Surface**: Dragging moves the 120Hz cursor; short-tapping on the trackpad dome clicks at the cursor position.
    * **Each of the 3 Buttons (`B1`, `B2`, `B3`)**:
      * **Short Tap (< 450ms)**: Executes that button's recorded macro sequence (taps, swipes, or Accessibility UI node matching).
      * **Long Press (>= 450ms)**: Starts macro recording directly into that specific button (`REC` indicator).
      * **Tap + Long Press (tap then hold within 420ms)**: Opens `BotMacroEditorDialog` for that specific button (with Play, Record, Loop, and per-step delay/coordinate editing).
  * Set default vertical position to **78% (bottom flank)** instead of 50% center, enabled **instant 0ms deployment** on edge touch (`ACTION_DOWN`) with `deployedOnThisTouch` separation so deploying never triggers unintended taps, and fixed **Tap + Hold / Double-Tap** on `B1..B3` so `pendingTapExecuteRunnable` is cancelled immediately on the second press to reliably open `BotMacroEditorDialog`.
  * Fixed the recursive touch-injection feedback loop in `LightspeedBotRecorderOverlay.kt`: always sets `FLAG_NOT_TOUCHABLE` and waits 55ms for `InputDispatcher` before injecting any tap/swipe, enforces a 240ms debounce and 40-step cap, adds a `✕ CANCEL` button next to `⏹ SAVE`, and auto-purges corrupted macros (`> 40` steps) in `LightspeedBotEngine.kt`.
  * Wired the Cybernetic Trackpad sensor strip and overlay to obey **Landscape Deflector Geometry** (`_landscape` calibration keys and scaling) and **Orientation Overlay Policy** (`Adaptive` vs `Portrait Only`), and moved `ORIENTATION OVERLAY POLICY` from Synthetic Gravity directly underneath `Landscape Deflector Geometry` in `DeflectorComponents.kt`.
  * Integrated `Cybernetic Trackpad` (`left_trackpad` / `trackpad`) into the **Deflector Tab Blueprint** reorder and pin manager.
  * Upgraded `LightspeedBotTrackpadDraw.kt` from stick-figure arm lines to **3 contoured dark-glass radial wedge segments** (and 3-segment beveled edge dock) with glowing neon bevel seams and crisp glyphs (`↶ B1`, `⌂ B2`, `❐ B3`).
  * Added Accessibility `dispatchGesture` fallback (`injectTapOrDispatch` / `injectSwipeOrDispatch`) and recorder pass-through (`passGestureAndRecordStep`) so recording and playback work both with and without Shizuku.
  * Simplified accordion title from `"CYBERNETIC TRACKPAD // LEFT"` / `"// RIGHT"` to `"Cybernetic Trackpad"`, removed the redundant `"Arms & Bot Macros"` sub-accordion, and made the **Live Trigger Zone Preview** 100% automatic whenever `"Geometry & Calibration"` is expanded.
  * Added **9 Chassis Styles** (`Classic Arc`, `Angular`, `Floating`, `Gear Tooth`, plus Infinity-gated `Soft Wing`, `Liquid Glass`, `Delta`, `Shield`, `Minimal`), **2 Button Orientations** (`Radial Arc` and `Edge Strip`), and **5 Tactical Cursor Shapes** (`⊕ Cross`, `➤ Arrow`, plus Infinity-gated `◎ Ring`, `❖ Dmnd`, `● Dot`), and fixed `LightspeedBotTrackpadOverlay.reload(context)` so switching trackpad styles or cursor shapes updates the views in-place without hiding the active trackpad or cursor.
  * Added **Experimental Labs Toggle + Teleport Arrow (`→`)** (`HudExperimentalLabsSection.kt`, `LightspeedBotContract.kt`, `CentralCommandTabNavigator`): toggling OFF hides the Cybernetic Trackpad accordion and disables the trackpad overlays; toggling ON reveals an instant `→` teleport button that jumps directly to the Right Deflector tab (`page = 2`) with the Cybernetic Trackpad accordion expanded.
  * Added **Per-Window & Per-App Scoped Recorded Actions** (`LightspeedBotEngine.kt`, `LightspeedBotScreenScanner.kt`, `LightspeedBotRecorderOverlay.kt`, `BotMacroEditorDialog.kt`): each application and each window within an app stores and executes its own independent macro sequences for `B1`, `B2`, and `B3` (`pref_bot_macro_<id>_win_<pkg>_<window>` -> `pref_bot_macro_<id>_app_<pkg>` -> global fallback).
  * Added **Smart Screen Element Detection (`LightspeedBotScreenScanner.kt`)**: automatically scans the active screen for Burger Menus / Navigation Drawers, Android 3-Dot Overflow Menus, Search buttons, Toolbar controls, and all clickable UI elements, drawing interactive tactical highlight boxes (`⌖ Burger Menu`, `⌖ Android Menu`, etc.) during recording and providing 1-tap Smart Element triggers inside `BotMacroEditorDialog`.
* **Cockpit Hangar Deck — 8 Sculpted Mockup Styles, True 12-Theme Canvas Palette & `◈ TRIGGER ZONES` Accordion (`DeepSpaceRenderer.kt`, `DeepSpaceHangarControlsDraw.kt`, `DeepSpaceHangarGimbalDraw.kt`, `LightspeedCruiseOverlayGearsDraw.kt`, `CockpitHangarTouchHandler.kt`, `DeepSpaceHangarRingsDraw.kt`, `DeepSpaceGearGeometry.kt`, `DeckThemeSnapshot.kt`)**:
  * Synced Cockpit Gear Rings, Hangar Deck, Category Cruise Starfield (`LightspeedCosmicBackdropRenderer.kt`), and Cybernetic Trackpad (+ 3 Buttons) directly to all **12 Central Command Themes** via `DeckStylePresets` in `DeckThemeSnapshot.resolveCanvasPalette(context)` (`Prism`, `Jelly`, `Frosted`, `Blur`, `Progressive`, `Glassmorphism`, `Neon`, `Adaptive`, `Acrylic`, `Gaussian`, `Depth`, `Dark`), and replaced all colored UI emojis with crisp geometric symbols (`◈`, `⌖`, `⬡`, `❖`, `◎`, `◐`, `▣`, `✦`, `✕`) and Material vector icons.
  * Implemented **8 Sculpted Mockup Styles** from `Cockpit mockups.png` (`⚙ GEAR` #1 Gear Wheel, `⬡ HEX` #2 Hex Ring, `❀ PETAL` #3 Petal Menu, `⚡ BLADE` #9 Floating Blades, plus Infinity-gated Row 2: `◎ ORBIT` #4 Orbital Rings, `❖ DMND` #6 Diamond, `◐ CRESC` #7 Crescent, and `▣ TECH` #14 Tech Disc) with individual dark-glass radial segments aligned to each ring's app icons, rendered the **Concentric Center Hub (`COCKPIT` / `HANGAR` + rotating reticle)** across all 8 styles, and completely removed the 6-point hexagram star geometry so all shapes are 100% secular and inclusive.
  * Reorganized the live in-flight **Cockpit Hangar Deck** (`DeepSpaceHangarControlsDraw.kt` & `CockpitHangarTouchHandler.kt`) from 10 stacked rows into a clean top bar (`Port/Starboard` + `Docking Bays Rail`) plus **3 Collapsible Accordion Tabs** (`◈ TRIGGER ZONES ▾`, `⌖ RETICLE ▾`, `⬡ SHAPE ▾`), fixed Portrait layout so expanded accordions render in an elevated dark-glass card above the auto-fitted lower Gimbal preview without overlap, and added a **side-by-side Landscape & Tablet split layout**.
* **Conscious Airlock — Full Cutout Coverage, Landscape Split Layout & Category Cruise Starfield (`LightspeedConsciousAirlockOverlay.kt`)**:
  * Updated `WindowManager.LayoutParams` with `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, `FLAG_LAYOUT_IN_SCREEN`, and `FLAG_LAYOUT_NO_LIMITS` so the airlock covers the entire physical display including the status bar and camera punch-hole cutout.
  * Added a responsive **Landscape side-by-side split layout** so the `Abort` and `Proceed` buttons are never clipped in landscape games or apps, and replaced the breathing circle with an animated **Category Cruise Starfield (`Canvas`)** with twinkling stars and nebula glow.
* **Vista Deck Smart Pixels — Dual Grid Mode (Density % + Block Size `1x..4x`) & Screen Recording Protection (`LightspeedSmartPixelsOverlay.kt`, `DisplayWellnessSection.kt`, `ExpandedBrightnessWellnessSection.kt`, `LightspeedPreferences.kt`)**:
  * Added both **Grid Density (`5%..85%`)** and **Pixel Block Size (`1x Fine`, `2x Medium`, `3x Coarse`, `4x Macro`)** controls in Vista Deck and Central Command.
  * Integrated MIT reference architecture from `Essentials` (by Sameera Sandakelum): added `DisplayManager.DisplayListener`, Android 15 (API 35) `WindowManager.addScreenRecordingCallback`, and a smooth 200ms `ObjectAnimator` fade-in so Smart Pixels never corrupts screenshots, screen recordings, or wireless casts.
* **Sonic Deck — Instant 0ms Launch, 5 Cinema Presets, All 5 System Streams & `0–100%` Per-App Mixer (`SonicDeckManager.kt`, `VistaDeckManager.kt`, `SonicDeckSections.kt`, `SonicAppMixerSection.kt`, `SonicAppVolumeEngine.kt`, `SonicVoiceIsolationEngine.kt`, `SonicIsolationUserService.kt`, `ShellAttributionContext.kt`)**:
  * Eliminated opening latency in `SonicDeckManager.kt` and `VistaDeckManager.kt` (removed initial `isVisible = false` frame delay and synchronous `dumpsys activity top` shell spawn on the UI thread).
  * Added **5 Cinema Dialogue & AI Isolation Presets** (`Cinema`, `Podcast`, `Gaming`, `Max`, `Custom`) with default `Cinema` tuning (`Vocal Boost +5dB`, `Background Muffle 65%`), plus `ContextImpl.mAttributionSource` shell patching and `@Keep constructor(Context)` in `SonicIsolationUserService` so audio loopback capture succeeds on Android 12–16 (`TCL`, `Infinix`, `Lenovo`).
  * Expanded **Master & System Streams** to all 5 Android audio streams: **Media**, **Ringtone**, **Notifications**, **Alarms**, and **Call / Assistant**.
  * Upgraded **Per-App Audio Split & Detection** and normalized the per-app volume slider to `0.0f..1.0f` (`0%..100%`) so `100%` sits at the full right end of the track: detects apps playing in **Picture-in-Picture (PiP)**, **Pop-Up / Freeform windows** (`LightspeedAccessibilityService.instance?.windows`), active `MediaSession`s, and privileged UID 2000 `AudioPlaybackConfiguration` (`getActiveAudioPackages()` / `setPackageVolume()` via `ISonicIsolationUserService.aidl`).
  * Added **Pin to Top** (`Icons.Default.PushPin`, `pref_sonic_pinned_apps`) and **Ignore App** (`Icons.Default.Block`, `pref_sonic_ignored_apps` — sorts to bottom, dims row, and disables controls) in `SonicAppMixerSection.kt`, and fixed the **No-Ducking Lock (`Icons.Default.Lock`)** in `SonicChannelRouter.kt` (`TAKE_AUDIO_FOCUS ignore` vs `allow`).
* **Config Vault, Uncategorized 2-Column Hall of Fame, 16 KB Page Alignment & SDK 26–36 Hardening (`LightspeedBackupEngine.kt`, `NebulaHallOfFameDialog.kt`, `CREDITS.md`, `PowerGrabHelper.kt`, `app/build.gradle.kts`)**:
  * Verified complete backup/restore coverage and instant live hot-reload for all `pref_bot_*`, `pref_sonic_*`, `pref_per_app_volume_*`, and `pref_smart_pixels_*` keys in `LightspeedBackupEngine.kt`.
  * Redesigned `NebulaHallOfFameDialog.kt` into an uncategorized 2-column grid starting with `MiXplorer` and `VLC & mpv`, followed by `Essentials` and `Key Mapper`, with zero proprietary trademark mentions.
  * Resolved the Android 15/16 **16 KB ELF page-size compatibility warning** by upgrading `onnxruntime-android` to `1.22.0`, setting `jniLibs.useLegacyPackaging = false`, and updating `PowerGrabHelper.kt` to auto-extract `liblsinputd.so` from the APK when needed; configured `compileSdk = 36` / `targetSdk = 36` (Android 16), `minSdk = 26` (Android 8.0 Oreo), and dual `arm64-v8a` + `armeabi-v7a` (32-bit Android Go) ABI support.
  * Completed all 17 Tier-1 Critical Smoke Detector splits in `PLAN.md` Objective 2 so zero Kotlin files in the repository exceed 750 lines.

### Fixed & Polished
* **Deflector Gesture Haptics Slider & Live Runtime Linkage (`HapticDomainSlider.kt`, `LightspeedHapticEngine.kt`, `LightspeedLeftWingOverlay.kt`, `CruiseFlankHandler.kt`)**:
  * Fixed tactile slider dragging bug in `HapticDomainSlider.kt`: decoupled `sliderVal` from `remember(selectedKey)` and added `keyToSliderVal` helper, preventing dragging from snapping back to 0 or resetting mid-gesture.
  * Moved preference persistence and refresh dispatch to `onValueChangeFinished` while keeping live tactile tick previews on `onValueChange` without recomposition thrashing.
  * Updated `LightspeedHapticEngine.kt` so `custom_XX` profiles command direct hardware one-shot vibration (`vibrateDirect`) honoring custom amplitudes rather than falling back to predefined generic system ticks.
  * Wired `LightspeedLeftWingOverlay.kt` and `CruiseFlankHandler.kt` to `KEY_DEFLECTOR_HAPTICS` via `LightspeedHapticEngine.deflectorClick(context)` and `deflectorTick(context)`.
* **Sonic Deck Window Focus Stability & Crash Prevention (`SonicDeckManager.kt`, `VistaDeckManager.kt`, `SonicDeckSections.kt`, `SonicAppMixerSection.kt`, `TaskInspector.kt`)**:
  * Resolved crash when launching Sonic Deck while Central Command is active: attached `OverlayLifecycleOwner` to `ComposeView` before `setContent` to guarantee a valid `ViewTreeLifecycleOwner` in all contexts.
  * Enforced main-thread execution for `show()` and `dismiss()` via `Handler(Looper.getMainLooper())`.
  * Added `FLAG_NOT_FOCUSABLE` to modal overlay window params in both `SonicDeckManager` and `VistaDeckManager`, preventing focus stealing and focus contention collisions with `CentralCommandActivity`.
  * Guarded `AudioManager.getStreamVolume` / `setStreamVolume` in `SonicDeckSections.kt` with try-catch blocks to prevent `SecurityException` when Do Not Disturb (ZenMode) or OEM ringer restrictions are active.
  * Hardened `SonicAppMixerSection.kt` and `TaskInspector.kt` against accessibility node inspection exceptions and IPC failures during window transitions.
* **Cybernetics Opacity Controls in Geometry & Arms Sub-Accordions (`LightspeedBotContract.kt`, `CyberneticTrackpadCalibration.kt`, `CyberneticTrackpadSubSections.kt`, `LightspeedBotTrackpadOverlay.kt`, `LightspeedBotTrackpadDraw.kt`)**:
  * Added Sensor Sidebar Gesture Area Opacity slider (`0%..100%`, default 50%) in `TrackpadGeometrySubSection` (`KEY_BOT_TRACKPAD_TRIGGER_OPACITY_LEFT/RIGHT`).
  * Added Trackpad Half-Circle Opacity slider (`10%..100%`, default 100%) in `TrackpadGeometrySubSection` (`KEY_BOT_TRACKPAD_DPAD_OPACITY_LEFT/RIGHT`).
  * Added Robot Arms Opacity slider (`10%..100%`, default 100%) in both `TrackpadArmsMacroSubSection` and `TrackpadGeometrySubSection` (`KEY_BOT_TRACKPAD_ARMS_OPACITY_LEFT/RIGHT`).
  * Connected opacity sliders directly to aerospace canvas rendering in `LightspeedBotTrackpadDraw.kt` and `LightspeedBotTrackpadOverlay.kt`.
  * Added `isAttachedToWindow` and try-catch guards to `updateViewLayout` in `deploy()` and `retract()` within `LightspeedBotTrackpadOverlay.kt` to eliminate `View not attached to window manager` crashes.

### Polished & Reorganized
* **Haptics UI De-duplication & Central Command Sub-Accordion Polish (`HapticsSubSection.kt`, `DeepSpaceHangarControlsDraw.kt`, `CockpitHangarTouchHandler.kt`, `LightspeedCruiseOverlayEditMode.kt`, `LightspeedCruiseOverlayEditTouch.kt`)**:
  * Tidied up Cockpit Hangar deck by completely removing the legacy 4-pill haptics row (`pref_gear_haptic_strength`), giving the gimbal ring more breathing room and centralizing all cockpit tactile controls inside Central Command.
  * Tidied up the Category Cruise overlay editor's HUD tab by removing redundant cruise haptics pills, keeping the tab cleanly dedicated to Settings Gear positioning.
  * Renamed the first sub-accordion in the Tactile & Haptics Engine from "Deflector & Command Haptics" simply to "Central Command" (grouping Central Command UI Haptics, Deflector Gesture Haptics, and Continuous Scrub Haptics).
* **Tactile & Haptics Engine Hierarchy Polish (`HapticsSubSection.kt`)**:
  * Restructured "Tactile & Haptics Engine" sub-accordion inside Hull and Ship Maneuvers:
    1. System Haptics (`KEY_HAPTIC_PROFILE`) placed at the top as the master baseline.
    2. Haptic Pulse Throttle cooldown card (`KEY_HAPTIC_COOLDOWN_MS`) directly follows.
    3. "Central Command" sub-accordion groups Central Command UI Haptics (`KEY_CENTRAL_COMMAND_HAPTICS`), Deflector Gesture Haptics (`KEY_DEFLECTOR_HAPTICS`), and Continuous Scrub Haptics (`KEY_GESTURE_SCRUB_HAPTICS`).
    4. "Cockpit Hangar & Gears" sub-accordion houses Rotary Circle Spinning and Gear Set Jump Haptics (`KEY_COCKPIT_GEAR_JUMP_HAPTICS`).
    5. "Category Cruise Tactile Engine" sub-accordion focused exclusively on cruising mechanics: Category Cruise (`KEY_CRUISE_CATEGORY_HAPTICS`), Spatial Layer Transits (`KEY_CRUISE_LAYER_JUMP_HAPTICS`), and Cursor Focus & App Scrub (`KEY_CRUISE_APP_FOCUS_HAPTICS`).

### Added
* **Dedicated Category Cruise & Cockpit Tactile Accordions (`HapticsSubSection.kt`, `HapticDomainSlider.kt`, `LightspeedCruiseOverlaySpatial.kt`, `LightspeedHapticEngine.kt`, `LightspeedPreferences.kt`)**:
  * Extracted Category Cruise into its own dedicated sub-accordion with 3 granular sliders: Category Cylinder Scrolling (`KEY_CRUISE_CATEGORY_HAPTICS`), Spatial Layer Transits across Category -> Apps -> Sticky Pin (`KEY_CRUISE_LAYER_JUMP_HAPTICS`), and Cursor Focus / App Scrub (`KEY_CRUISE_APP_FOCUS_HAPTICS`), alongside Deflector and Scrub sliders.
  * Modularized Cockpit Hangar & Gears into a dedicated sub-accordion featuring Rotary Circle Spinning (`KEY_COCKPIT_HAPTICS`) and Gear Set Jump (0, 1, 2) transitions (`KEY_COCKPIT_GEAR_JUMP_HAPTICS`).
  * Placed System Haptics (`KEY_HAPTIC_PROFILE`) and Central Command UI Haptics (`KEY_CENTRAL_COMMAND_HAPTICS`) as standalone isolated cards directly beneath the Haptic Pulse Throttle card.
  * Added vertical finger precision gearing (`precisionStep = 0.05f`) in `HapticDomainSliderRow` for fine 5% sub-step custom tuning (`custom_XX`).
  * Extracted reusable slider component into `HapticDomainSlider.kt` to maintain strict AI navigability with `HapticsSubSection.kt` well under 250 lines.

### Fixed & Restored
* **Tablet ERM Haptic Inertia Compensation & OS Touch Suppression Bypass (`LightspeedHapticEngine.kt`)**:
  * Resolved missing tactile haptics on tablet hardware (e.g. Lenovo TB305XU) caused by system `TOUCH=OFF` OS settings filters by switching usage to `VibrationAttributes.USAGE_HARDWARE_FEEDBACK`.
  * Added hardware amplitude detection (`vibrator.hasAmplitudeControl()`): on devices lacking amplitude control (ERM counterweight motors), one-shot pulses enforce a 30ms minimum duration or utilize native prebaked `EFFECT_CLICK` / `EFFECT_HEAVY_CLICK` so eccentric motors overcome mechanical inertia and deliver distinct tactile pulses.


### Fixed & Restored
* **Haptics Sub-Chip & Tactical Feedback Isolation (`GestureScrubHapticsSelector.kt`, `LightspeedHapticEngine.kt`, `LightspeedCruiseOverlay.kt`, `CruiseScrubEngine.kt`)**:
  * Decoupled deflector continuous parameter scrub haptics (`KEY_GESTURE_SCRUB_HAPTICS`) from Category Cruise and general system/deflector feedback.
  * Reverted `triggerHardwareHaptic` in `LightspeedCruiseOverlay` to direct hardware vibration (`LightspeedHapticEngine.vibrate`), restoring independent control to Category Cruise HUD haptics (`KEY_CRUISE_HAPTICS`).
  * Routed continuous parameter scrub ticks (volume, brightness, timeout, scroll scrub) through `LightspeedHapticEngine.scrubTick`.
* **Category Cruise Spatial Baseline Reset (`LightspeedCruiseOverlaySpatial.kt`, `CruiseFlankHandler.kt`, `CruiseTouchHandler.kt`, `LightspeedCruiseOverlay.kt`)**:
  * Reset Category Cruise mechanics and spatial cylinder math back to the verified baseline, eliminating anchor offsets and gesture conflicts.
  * Preserved full tactile cruise haptics customization options in the HUD tab.
* **Settings Gear Relocation (`LightspeedCruiseOverlayEditMode.kt`, `LightspeedCruiseOverlayEditTouch.kt`, `SystemOverrideLockscreenSection.kt`)**:
  * Moved the pinned-page settings gear alignment toggle (`KEY_PINNED_GEAR_LEFT`) from Central Command's System Override accordion into the HUD tab of the Category Cruise edit mode flyout deck.
  * Supported dynamic "TOP RIGHT" vs "TOP LEFT" pill buttons with immediate overlay persistence.
* **Critical Thermal Fix & Dumpsys Throttle (`LightspeedWatchdogEngine.kt`, `GriffinRecovery.kt`, `SonicVoiceIsolationEngine.kt`)**:
  * Eliminated high-frequency `dumpsys accessibility` shell process spawns every 30 seconds.
  * Added `getCrashedAccessibilityPackages()` to batch and cache crashed package inspection at most once per pulse.
  * Presumed user toggle-offs (`wasEnabled == false` or `count > 2`) skip dumpsys inspection completely and log only once to `FlightLog`.
  * Prevented privileged user service binding in `SonicVoiceIsolationEngine` when disabled.
* **Category Cruise "Finger-in-Space" Spatial Math (`LightspeedCruiseOverlay.kt`, `LightspeedCruiseOverlaySpatial.kt`, `CruiseFlankHandler.kt`)**:
  * Decoupled category cylinder and app grid vertical anchors via dedicated `catAnchorRawY`, `catAnchorIndex`, and `gridEntryRawY` fields, completely eliminating baseline anchor ratcheting when switching between categories and apps.
  * Grid entry dynamically maps `gridEntryRawY` to `hF * 0.32f`, ensuring Row 0 (`hF * 0.22f`) is always accessible ~35dp above the finger's inward swipe entry point regardless of vertical position.
* **Full-Display Status Bar Coverage on Android 11–16 / API 36 (`FullDisplayOverlayHelper.kt`, `LightspeedEyeRestOverlay.kt`, `SonicDeckManager.kt`, `VistaDeckManager.kt`)**:
  * Applied `FLAG_LAYOUT_NO_LIMITS`, `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, and `setFitInsetsTypes(0)` / `setFitInsetsSides(0)` / `setFitInsetsIgnoringVisibility(true)` to `SonicDeckManager` and `VistaDeckManager`, guaranteeing the 65% black scrim covers 100% of the screen all the way up through the status bar and camera cutout.
* **Haptics Sub-Chip & Tactical Feedback Engine (`GestureScrubHapticsSelector.kt`, `LightspeedHapticEngine.kt`, `LightspeedCruiseOverlay.kt`, `CruiseScrubEngine.kt`, `CruiseTouchHandler.kt`)**:
  * Renamed UI labels to clean hardware-agnostic profiles: `Off`, `Transitions Only`, `Soft (Low-Freq)`, `Crisp (Standard)`, `Heavy (Mechanical)`.
  * Routed all gesture and scrubbing haptics through `LightspeedHapticEngine.performScrubOrGestureHaptic` with 90ms cooldown for Soft, 25ms for Crisp, and full suppression of scrub ticks under `Transitions Only`.
* **Vista Deck & Gated Apps In-Card View Switch (`VistaDeckManager.kt`, `ExpandedBrightnessWellnessSection.kt`, `ConsciousAirlockAppPickerDialog.kt`)**:
  * Replaced external activity launch with an in-card view switch (`showAirlockPicker`) directly inside `DeckGlassCardSurface`, eliminating ghosting and transparency overlaps.
  * Added `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to installed app titles and package names in `ConsciousAirlockAppPickerDialog.kt`.
* **Category Cruise Spatial Baseline Rollback (`LightspeedCruiseOverlaySpatial.kt`)**:
  * Completely restored category cruise geometry, row heights, and finger jump navigation to baseline `82325b1a`, resolving accessibility regression and preserving smooth transitions between categories and app columns.
* **Brand Sanitization & Copyright Compliance**:
  * Completely purged all occurrences of "UbikiTouch" / "Ubiki" across all token resolvers, action registries, touch handlers, bot engines, and settings accordion UI.
* **Conscious Airlock Token Crash Fix (`ConsciousAirlockConfigActivity.kt`, `ConsciousAirlockAppPickerDialog.kt`, `ExpandedBrightnessWellnessSection.kt`)**:
  * Fixed `WindowManager$BadTokenException` crash when tapping "Gated Apps (X) ⚙" from Vista Deck by launching a dedicated transparent `ConsciousAirlockConfigActivity` with `FLAG_ACTIVITY_NEW_TASK`.
* **Solid Scrub HUDs & Tactical Theme Decks (`LightspeedHudThemeHelper.kt`, `LightspeedHudRenderer.kt`, `SonicDeckManager.kt`, `VistaDeckManager.kt`)**:
  * Fixed transparent HUDs when Theme Engine toggle is enabled by rendering an opaque base fill and enforcing minimum panel alpha of 88%..95%.
  * Deepened Sonic Deck and Vista Deck backgrounds with 65% black scrim and full opacity for vibrant 12-theme optical presentation over wallpapers.
* **Foreground App Routing & Audio Mixer (`SonicAppVolumeEngine.kt`, `SonicAppMixerSection.kt`, `SonicDeckSettingsSheet.kt`)**:
  * Automatically resolve the active foreground app via `TaskInspector.getTopForegroundTaskInfo` and place it at index 0 in per-app routing and volume mixer lists.
* **Bot Recorder Overlay Lifecycle Crash Fix (`LightspeedBotRecorderOverlay.kt`)**:
  * Replaced `ComposeView` inside overlay window with 100% native Android views (`FrameLayout`, `TextView`, `GradientDrawable`), preventing `ViewTreeLifecycleOwner` crashes when tapping the record button.
* **Cybernetic Trackpad UI Alignment & Aerospace Overhaul (`DeckControls.kt`, `CyberneticTrackpadAccordion.kt`, `LightspeedBotTrackpadDraw.kt`)**:
  * Upgraded `DeckButton` to provide themed `LocalContentColor`, eliminating black text on buttons.
  * Standardized button padding and single-line layout for Robot Arms (1..4) and Active Macros (1..5) in `CyberneticTrackpadAccordion.kt`.
  * Redesigned `LightspeedBotTrackpadDraw.kt` from crude sticks and circles to high-precision aerospace collimator reticles, holographic graduation ticks, dual-rail conduit beams, and chamfered glowing satellite pods.
* **Stack Widget Preview & Theme Sync (`LightspeedStackWidgetProvider.kt`)**:
  * Loaded real child widget icons and app titles directly onto the home screen stack widget card bitmap.
  * Added `ACTION_REFRESH_THEME` receiver to dynamically update stack widget cards upon Central Command theme changes.

### Added
* **Interactive Tactical Stack Widget & Contact Token Resolution (`LightspeedStackWidgetProvider.kt`, `widget_stack_layout.xml`, `LightspeedStackTileHelper.kt`, `LightspeedShortcutManager.kt`, `LightspeedIconManager.kt`, `ActionDispatcher.kt`)**:
  * Replaced static gray box with a rich multi-page interactive widget operating directly on the home screen over 12-theme wallpaper-blurred glass:
    * **Page 0 (Tactical App & Shortcut Grid)**: Renders 4 configured apps/shortcuts from active gear with crisp resolved icons, human-readable labels, and 1-tap direct launching.
    * **Page 1 (Quick System Deck)**: 4 interactive toggle tiles for Vocal Boost, Smart Pixels, Extra Dim, and Vista Deck with live active/inactive status highlights and instant execution.
    * **Page 2 (Cockpit Telemetry)**: Live gear set name and module counter, real-time battery % and immunity status, 1-tap gear cycle button (`ACTION_CYCLE_GEAR`), and 1-tap refueling bay deck launcher.
    * **Page 3+**: External hosted Android widget previews with 1-tap expansion into `LightspeedHomeStackOverlayActivity`.
    * **Header Navigation**: Interactive `◀` (prev page), card title, `▶` (next page), and `⚙` (opens widget config dialog).
  * Fixed `caller;label=...` and `;b64uri=` token parsing across `LightspeedShortcutManager.kt`, `LightspeedIconManager.kt`, and `ActionDispatcher.kt`, properly resolving contacts ("Dr Mom", "saved messages") with clean labels, monogram/contact icons, and 1-tap calling/messaging in `LightspeedFolderWidgetProvider` and `LightspeedFolderModalActivity`.

* **Cybernetic Trackpad Sub-Accordions & Tactical Calibration (`CyberneticTrackpadAccordion.kt`, `CyberneticTrackpadSubSections.kt`, `CyberneticTrackpadCalibration.kt`, `LightspeedBotTrackpadOverlay.kt`)**:
  * Reorganized Trackpad settings into 3 collapsible sub-accordions matching Unified Deflectors: `Arms & Bot Macros`, `Geometry & Calibration`, and `Cursor Physics & Timing`.
  * Created `TrackpadPillButton` with 36dp fixed height, single-line text (`softWrap = false`), and high-contrast Material 3 theme styling, eliminating wrapping and unreadable black text.
  * Added Live Trigger Zone Preview toggle with illuminated accent and border indicator for instant visual calibration.
  * Added continuous sliders for Edge Trigger Touch Width (8..32 dp), Half-Circle D-Pad Radius (50..110 dp), Robot Arm Extension Reach (36..80 dp), and Cursor Sensitivity (0.5x..2.5x).
  * Clamped deployed D-Pad and robot arm center coordinates (`safeDpadCenterY = triggerCenterY.coerceIn(totalRadiusPx, screenHeight - totalRadiusPx)`), guaranteeing that the trackpad and satellite pods are never clipped by screen top/bottom boundaries even at 10% or 90% vertical trigger offsets.

* **Half-Circle Cybernetic Trackpad with Articulated Robot Arms (`LightspeedBotTrackpadOverlay.kt`, `LightspeedBotTrackpadDraw.kt`, `CyberneticTrackpadAccordion.kt`, `LightspeedBotContract.kt`)**:
  * Fixed trackpad movement crash: eliminated all rapid `WindowManager.updateViewLayout` calls during touch movement; cursor is rendered on a fullscreen non-touchable Canvas overlay (`FullDisplayOverlayHelper.createFullDisplayOverlayParams`) updating cursor coordinates and invalidating at locked 120Hz.
  * Built Half-Circle D-Pad with 1..4 mechanical robot arms and articulated elbow struts themed with Central Command 12-theme backplane, outer glow, and specular highlights (`LightspeedBotTrackpadDraw.kt`).
  * Deployed on-demand from Left or Right bezel edge trigger strips via inward swipe (> 14dp) or rest hold (>= 120ms).
  * Single-swipe continuous glide cursor navigation with inertia/acceleration curve.
  * Finger-lift linger window (800ms..3500ms, default 1800ms) leaving arms interactive for tap replay, hold record, bot macro editor, and infinite loop.
  * Mounted dedicated `CyberneticTrackpadAccordion` in both Left and Right Deflector tabs.

* **Modularization of Deflector Tabs (`DeflectorTabBody.kt`, `DeflectorTabBlueprint.kt`, `DeflectorTabNotices.kt`, `DeflectorTabSectionCards.kt`)**:
  * Decomposed 677-line `DeflectorTabBody.kt` into 4 focused modules strictly under 350 lines each.
  * Verified 100% theme drift guard compliance (`bash scripts/check_theme.sh`).

### Fixed
* **Category Cruise Flank Distance Baseline Drift & Row Restoration (`LightspeedCruiseOverlaySpatial.kt`)**:
  * Fixed Column 0 reachability bug where the column closest to the trigger flank became unreachable after repeatedly entering and exiting categories.
  * Replaced drifting `horizontalPull = (rawX - touchDownRawX)` with constant physical flank distance: `flankDistance = if (isOpenedFromLeftFlank) localX else (wF - localX)`.
  * Removed `touchDownRawX = rawX` coordinate reset on Category re-entry, preventing horizontal baseline drift.
  * Restored intended rows geometry (`hF * 0.16f`, `verticalComfortScope = hF * 0.40f`, `verticalStartAnchor = hF * 0.30f`).

### Added
* **Native Widget Configuration & Home Screen Shortcut Studio (`LightspeedWidgetConfigActivity.kt`, `space_pod_widget_info.xml`, `lightspeed_folder_widget_info.xml`, `lightspeed_stack_widget_info.xml`)**:
  * Removed `SpacePodSettingsSection` from Central Command in favor of native Android widget configuration and on-demand setup.
  * Implemented `LightspeedWidgetConfigActivity` (< 230 lines) registered with `android.appwidget.action.APPWIDGET_CONFIGURE` and `reconfigurable|configuration_optional`.
  * Added first-launch check: tapping unconfigured Space Pod Widgets, Home Shortcuts, or QS Tile Pods automatically opens `LightspeedWidgetConfigActivity`.
  * Removed long-press interception from Home Screen Widgets and Shortcuts to preserve native Android launcher move/resize/delete operations; home shortcuts and 1x1 Pod widgets exclusively evaluate Single Click and Double Click (QS Tiles retain all 4 gestures).
  * Added Custom Icon (Gallery photo picker & Icon Pack browser) and custom title editor for Home Screen Shortcuts and Pod Widgets before pinning via `ShortcutManagerCompat.requestPinShortcut`.

* **Bulletproof 12-Theme Stackable Widget & Refueling-Bay-Style Stack Overlay (`LightspeedStackWidgetProvider.kt`, `widget_stack_layout.xml`, `WallpaperBlurHelper.kt`, `LightspeedHomeStackOverlayActivity.kt`)**:
  * Replaced crash-prone `StackView` and `RemoteViewsService` with a pre-rendered `ImageView` card canvas and discrete navigation overlay zones (`btn_stack_prev`, `btn_stack_center`, `btn_stack_next`, `btn_stack_edit`).
  * Implemented `WallpaperBlurHelper` (< 160 lines) capturing live wallpaper (with gradient fallback), fast box-stack blur matching `windowBlurRadiusPx`, and compositing 12-theme glass panel tint, specular highlight, and border.
  * Created `LightspeedHomeStackOverlayActivity` hosting an interactive Refueling-Bay-style `HorizontalPager` widget stack floating over blurred 12-theme glass with `MultiWidgetContainer` and `TacticalWidgetPickerModal`.

* **Tactical Folder Real Label & Icon Resolution (`LightspeedFolderModalActivity.kt`, `LightspeedFolderWidgetProvider.kt`)**:
  * Resolved all app and deep shortcut tokens to human-readable labels via `LightspeedShortcutManager.resolveLabel`.
  * Loaded real icon bitmaps asynchronously via `LightspeedIconManager.getIconBitmap` / `resolveIconBitmap`, eliminating raw Base64 token strings and generic rocket icons.
  * Dispatched app launches directly through `ActionDispatcher.executeDirect`.

* **Modularized HUD Strip Tab (`HudStripTab.kt`, `HudStripTabOrderHelper.kt`, `HudStripAdvancedSections.kt`)**:
  * Split accordion section ordering and migration into `HudStripTabOrderHelper.kt`.
  * Extracted secondary and advanced sections into `HudStripAdvancedSections.kt`, bringing `HudStripTab.kt` to 332 lines (all files strictly under 350 lines).

* **True Edge-to-Edge Status Bar & Cutout Coverage (`FullDisplayOverlayHelper.kt`, `LightspeedEyeCareEngine.kt`, `LightspeedSmartPixelsOverlay.kt`)**:
  * Implemented `createFullDisplayOverlayParams(context)` calculating exact physical window metrics, status bar height, and navigation bar inset.
  * Applied `FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`, `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`, and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` across both Eye Care / Extra Dim overlays and Smart Pixels overlays to guarantee 100% coverage over status bar, camera cutouts, and navigation areas on Android 11-16 (especially Transsion XOS).

* **Continuous Smart Pixels 8x8 Bayer Ordered Dithering Matrix (`LightspeedSmartPixelsOverlay.kt`, `ExpandedBrightnessWellnessSection.kt`, `DisplayWellnessSection.kt`)**:
  * Upgraded OLED Smart Pixels from 4 rigid button presets to a smooth continuous density slider (`5%`..`85%`).
  * Replaced manual patterns with an 8x8 Bayer Ordered Dithering matrix rendered at 1:1 physical screen pixels with `Bitmap.DENSITY_NONE`, zero bilinear filtering, and automated 60-second spatial phase shifting.

* **Real-Time SurfaceFlinger 1015 HAL Color Matrix & Native Blue Light Engine (`LightspeedEyeCareEngine.kt`)**:
  * Implemented debounced (80ms on `Dispatchers.IO`) hardware 4x4 RGB color matrix scaling via Shizuku (`service call SurfaceFlinger 1015 i32 1 f <r> ...`) to control color temperature (1800K..5500K) and Extra Dim (0%..90%) at the hardware display HAL level.
  * Automatic zero-lag fallback to full-display edge-to-edge `TYPE_ACCESSIBILITY_OVERLAY` when Shizuku is not running.
  * Modularized 20-20-20 rule timer into `LightspeedEyeRestMonitor.kt`.

### Added
* **Central Command 12-Theme Engine for Scrub HUDs (`LightspeedHudThemeHelper.kt`, `LightspeedHudReticleRenderer.kt`, `LightspeedHudBladeRenderer.kt`, `LightspeedHudRenderer.kt`, `LightspeedHudHorizonRenderer.kt`, `LightspeedHudDialRenderer.kt`)**:
  * Added `KEY_HUD_USE_CENTRAL_THEME` preference toggle (default `true`) allowing all 5 Scrub HUD styles (Canopy Drop-Pod, Holographic Cockpit Reticle, Edge Blade, Quantum Horizon, Tachyon Dial) to dynamically render with the active Central Command theme palette (`panelColor`, `panelAlpha`, `tintColor`, `borderColor`, `glowColor`, `glowStrength`, and `highlightStrength`).
  * Added inline gesture row toggle chip ("Theme: Deck 12-Style" vs "Theme: Classic Dark") in `GestureActionDropdowns.kt`.
  * Modularized HUD renderers into single-responsibility modules strictly under 350 lines.

### Changed
* **Overhauled Sonic Deck (`SonicDeckManager.kt`, `SonicDeckSections.kt`, `SonicAppMixerSection.kt`)**:
  * Removed outer dim backdrop (`FLAG_DIM_BEHIND`, `dimAmount = 0f`) and made container transparent to prevent status bar seams.
  * Bound height to `wrapContentHeight().heightIn(max = 540.dp)` to hug content tightly with zero dead bottom space.
  * Wrapped content in full Central Command 12-theme optical surface (`DeckGlassCardSurface`).
  * Rebuilt Per-App Sovereign Mixer into sleek 44.dp single-row controls with pure Kotlin vector icons (`SyncAlt`, `Speaker`, `Headphones`, `Lock`, `LockOpen`), completely eliminating emojis.
  * Pulling outward (> 80dp) while scrubbing Volume launches Sonic Deck immediately.

* **Overhauled Vista Deck (`VistaDeckManager.kt`, `ExpandedBrightnessWellnessSection.kt`)**:
  * Removed outer dim backdrop (`FLAG_DIM_BEHIND`, `dimAmount = 0f`) and made outer frame transparent.
  * Wrapped card tightly using `wrapContentHeight().heightIn(max = 540.dp)` with active 12-theme glass card styling.
  * Directly embedded Conscious 5s App Launch (Mindful Airlock) toggle and Gated Apps picker button inside `ExpandedBrightnessWellnessSection.kt`.
  * Pulling outward (> 80dp) while scrubbing Brightness launches Vista Deck immediately.

* **Central Command Gesture Row Layout & Sub-Chip Flow (`DeflectorGestureCards.kt`, `GestureComponents.kt`, `GestureScrubHapticsSelector.kt`, `GestureBrightnessScrubControls.kt`, `GestureVolumeScrubControls.kt`)**:
  * Replaced horizontally clipping single-line rows with `@OptIn(ExperimentalLayoutApi::class) FlowRow` spaced by 6.dp.
  * Converted Scrub Haptics into a compact 28.dp `DeckSubChip` dropdown, eliminating the ~120dp blank vertical gap in gesture row cards.

### Removed
* **Failed Mini Volume / Brightness Scrub Overlays**:
  * Removed `LightspeedUnifiedScrubHudOverlay`, `LightspeedUnifiedScrubHudContent`, `LightspeedUnifiedScrubHudTracks`, `LightspeedVolumeHudOverlay`, `LightspeedVolumeHudContent`, and `LightspeedVolumeHudDrawer`.
  * Normal volume and brightness scrubbing now exclusively utilizes high-performance tactical HUDs (`LightspeedStatusBarOverlay.showActionHud`) without intrusive secondary vertical sliders or mini-columns.

### Added
* **Vista Deck (Visual Optics & Display Wellness Modal — `VistaDeckManager.kt`, `ExpandedBrightnessWellnessSection.kt`)**:
  * Mirrors the exact glass modal shape, animations, and triggers of the Sonic Command Deck, dedicated to Display Wellness and Visual Optics.
  * **Triggers**:
    * Pull outward horizontally (>80dp) from Deflectors while scrubbing Brightness.
    * Pull downward vertically (>80dp) from the top Sensor Deck progress rail while scrubbing Brightness.
    * Direct Action Token: `system:vista_deck` / `system:display_dock` dispatchable via gestures, Space Pods, or hardware buttons.
    * Central Command inline sub-chip in `GestureBrightnessSubChips.kt` ("Vista Deck ⚙").
  * **Integrated Controls**:
    * Master Brightness & ⚡ Video Takeover (0%..120% overlay boost).
    * Auto-Brightness Reset triggers (Screen Off, App Switch, Recents Close).
    * OLED Smart Pixels (12%, 25%, 50%, 75% uniform subpixel matrices).
    * Eye Care Blue Light filter (Native, Overlay, SurfaceFlinger 1015 modes) & Kelvin temperature slider.
    * Extra Dim intensity slider (-10%..-90%).
    * 20-20-20 Eye Rest reminder suite & quick preview.

* **Clean-Room Lightspeed Bots & Trackpad Sensor Area (`LightspeedBotEngine.kt`, `LightspeedBotRecorderOverlay.kt`, `BotMacroEditorDialog.kt`, `LightspeedBotTrackpadOverlay.kt`, `BotTrackpadSettingsSection.kt`, `BotStep.kt`, `LightspeedBotContract.kt`)**:
  * **100% Clean-Room Architecture**: Built strictly using Android `AccessibilityService` APIs and Shizuku `IInputManager` without any GPL dependencies.
  * **UbikiTouch Gesture Record & Replay**:
    * Swipe & Hold on any Deflector/Sensor Zone bound to a Bot macro (`bot:macro_1` .. `bot:macro_10`) triggers Bot Recording Mode with a translucent recording border and floating stop pill.
    * Screen-Reading on every step: reads `viewIdResourceName`, `text`, `contentDescription`, `className`, `packageName`, and relative coordinates.
    * Screen-Aware Playback: inspects `rootInActiveWindow` to dynamically locate matching view nodes even if the screen scrolled or shifted, with physical coordinate fallback via Shizuku < 2ms injection.
  * **Bot Macro & Screen Condition Editor**:
    * `BotMacroEditorDialog` allows users to test run, inspect, and delete individual steps and review recorded accessibility identifiers.
  * **Dedicated Bot Trackpad Sensor Zone & Arms**:
    * Toggleable floating trackpad with edge-snapping and customizable size/alpha (`LightspeedBotTrackpadOverlay.kt`).
    * High-precision crosshair cursor reticle across display.
    * 4 Tactical Action Arms:
      1. Primary Arm (Single Tap): Fires instant tap at cursor reticle.
      2. Secondary Arm (Hold): Starts recording a new Bot macro.
      3. Edit Arm (Tap then Hold within 500ms): Opens `BotMacroEditorDialog`.
      4. Loop Arm (Double Tap, 🔒 Infinity): Initiates continuous auto-looping with live iteration count badge on the cursor reticle.

* **True Multi-Gesture Space Pods Suite (`SpacePodGestureDispatcher.kt`, `SpacePodTileService.kt`, `SpacePodLongClickActivity.kt`, `SpacePodShortcutActivity.kt`, `SpacePodWidgetProvider.kt`, `LightspeedFolderWidgetProvider.kt`, `LightspeedFolderModalActivity.kt`)**:
  * Removed legacy floating bubble overlay in favor of system-level Quick Settings Tile Pods (Pods 1 to 4), Home Screen Shortcut Pods, 1x1 Widget Pods, Stackable Widgets, and Resizable Home Screen Folders.
  * **4-Gesture State Machine**: Dispatches `CLICK` (320ms single tap window), `DOUBLE_CLICK` (within 320ms), `HOLD` (long-press intercepted via `QS_TILE_PREFERENCES` and shortcut catcher), and `CLICK_THEN_HOLD` (tap followed by hold within 650ms).
  * **12-Theme 1x1 Widget Pods**: Render dynamic optical glass surface matching active theme (`DeckThemeManager`) and forward taps to multi-gesture state machine.
  * **12-Theme Resizable Folders**: Supports dynamic cell resizing (1x1 compact -> 2x2 grid -> expanded views) via `onAppWidgetOptionsChanged`, with a 12-theme modal dialog (`LightspeedFolderModalActivity`) to add and remove pinned apps.
  * **Central Command Integration**: Configured cleanly in `HudStripTab` and `SpacePodSettingsSection` with per-pod 4-gesture action assignment via `CockpitGearPickerActivity` and 1-tap home screen shortcut pinning.

* **Unified Aesthetic Scrub HUD & Auto-Pinning Interactive Surface (`LightspeedUnifiedScrubHudOverlay.kt`, `LightspeedUnifiedScrubHudContent.kt`, `LightspeedUnifiedScrubHudTracks.kt`, `ExpandedSonicDeckSection.kt`, `ExpandedBrightnessWellnessSection.kt`, `ExpandedTimeoutAndScrollSections.kt`)**:
  * Unified volume, brightness, screen timeout, and scroll scrubbing into an interactive HUD with real glass blur, specular rim, and 12-theme dynamic color palette.
  * **State A (Collapsed Active Scrubbing)**: Master slider with Per-App Volume mini-columns (auto-collapsed), Brightness with Video Override Zone (101%..120%) and Extra Dim Zone (-10%..-90%), Timeout step gauge (15s..30m) with "⚡ Always On Until Screen Off" pill, and Scroll Scrub indicator.
  * **State B (Auto-Pinned Expanded Deck)**: Pinned surface with 6-second inactivity auto-dismiss that resets on touch and dismisses on outside tap.
    * Integrated Sonic Deck: Per-app volume sliders (0%..200%), system streams, AI Cinema Vocal Boost & Background Muffle, and output channel routing / always-on audio focus toggles.
    * Integrated Display Wellness: Master brightness + Video Takeover (0%..120% overlay override), Auto-Brightness Reset chips (`screen_off`, `app_switch`, `recents_close`), Extra Dim & Eye Care Kelvin sliders, and OLED Smart Pixels / 20-20-20 Eye Rest controls.
    * Screen Timeout: "⚡ Always On Until Screen Off" mode backed by `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` auto-released on `ACTION_SCREEN_OFF`.
* **New Scrub Action: Scroll Scrubbing (`system:scroll_scrub`, `LightspeedScrollScrubEngine.kt`)**:
  * Added `system:scroll_scrub` action supporting continuous vertical and horizontal list/page scrolling on deflectors and status bar using Shizuku `IInputManager` `MotionEvent.ACTION_SCROLL` (`AXIS_VSCROLL`) with accessibility step scroll fallback.
* **Gesture Row Sub-Chips for Scrub Actions (`GestureActionSubChips.kt`, `GestureComponents.kt`)**:
  * Added inline configuration sub-chips in gesture settings rows for Volume ("Per-App Columns: Auto" vs "Expanded Only", plus Sonic Deck launcher) and Brightness (Video Brightness Takeover and Auto-Brightness Reset mode selectors).
* **Settings Architecture Cleanup (`HudSystemOverridesSection.kt`, `LightspeedPreferencesScrubKeysContract.kt`)**:
  * Decoupled `DisplayWellnessSection`, `SonicDeckSettingsSheet`, and `SpacePodSettingsSection` from `System Overrides` into their respective Scrub HUD surfaces while keeping the Sonic Deck master experimental toggle in `Experimental Labs`.

* **Privileged Shizuku Real-Time Voice Isolation Pipeline (`ISonicIsolationUserService.aidl`, `SonicIsolationUserService.kt`, `SonicPrivilegedAudioPipeline.kt`, `ShellAttributionContext.kt`, `IsolationIntensityMixer.kt`, `SonicVoiceIsolationEngine.kt`)**:
  * Bound privileged `SonicIsolationUserService` via `Shizuku.bindUserService(...)` executing under UID 2000 (`com.android.shell`) with `CAPTURE_AUDIO_OUTPUT` and `MODIFY_AUDIO_ROUTING`.
  * Built real-time loopback capture pipeline in `SonicPrivilegedAudioPipeline`: captures 16kHz PCM audio via `AudioRecord` sink using `AudioPolicy` loopback (excluding output UID to eliminate feedback), processes streaming 256-sample hops through `StftProcessor` -> `GtcrnProcessor` (`gtcrn.onnx`) -> `IsolationIntensityMixer`, and renders isolated speech via `AudioTrack`.
  * Implemented `IsolationIntensityMixer` supporting real-time independent parameters: `vocalBoostLevel` (0.0f..1.0f, up to +8dB speech gain with tanh soft limiter) and `backgroundMuffleLevel` (0.0f..1.0f, background residual attenuation).
  * Removed fake Equalizer stub and dummy warm-loops from `SonicVoiceIsolationEngine`.
* **Privileged Shizuku Per-App Channel Router (`SonicChannelRouter.kt`, `SonicIsolationUserService.kt`)**:
  * Delegated per-app audio output device routing (`ROUTE_SPEAKER`, `ROUTE_BLUETOOTH`, `ROUTE_WIRED`, `ROUTE_DEFAULT`) directly to the Shizuku UserService executing `AudioManager.setUidDeviceAffinity` under UID 2000 (`shell`), eliminating non-privileged `SecurityException`s.
  * Synchronized per-app volume (0%..200%), Always-On / Non-Ducking Audio Focus (`cmd appops set <pkg> TAKE_AUDIO_FOCUS allow` + `IAudioService.setMultiAudioFocusEnabled(true)`), and device routing in `defaultPrefs()` with unified persistence, hot-reload, and automatic reset on teardown.
* **12-Theme Sonic Deck Rebuild & Volume HUD Overhaul (`SonicDeckManager.kt`, `SonicDeckSections.kt`, `LightspeedVolumeHudOverlay.kt`, `LightspeedVolumeHudContent.kt`, `LightspeedVolumeHudDrawer.kt`, `LightspeedAccessibilityReceiver.kt`)**:
  * Rebuilt `SonicDeckManager` modal using `DeckGlassCardSurface` optical glass surface (< 300 lines) with three dedicated sections:
    1. **Cinema Dialogue & AI Isolation**: Master neural toggle + live sliders for Vocal Boost and Film Background Muffle.
    2. **Master & System Streams**: Media, Ring, Alarm, and Call sliders + working `MUTE ALL`, `DND TOGGLE` (via NotificationManager / Shizuku zen_mode), and `RESET APPS`.
    3. **Per-App Mixer, Output Channel & Sovereignty**: Interactive app rows showing App Label + Volume Slider (`0%..200%` with boost indicator) + Channel Pill (`🔊 SPK` / `🎧 BT` / `🔌 WIRE` / `🔄 DEF`) + Always-On Sovereignty Lock (`🔒 ALWAYS-ON`).
  * Upgraded `LightspeedVolumeHudOverlay` & `LightspeedVolumeHudContent`:
    * Added Bluetooth headset volume change trigger via `android.media.VOLUME_CHANGED_ACTION` (`STREAM_MUSIC`) in `LightspeedAccessibilityReceiver`.
    * Collapsed HUD view displays master volume alongside slim vertical columns strictly for apps actively playing audio or configured with custom volume (!= 100%).
    * Expanded drawer provides quick controls for Vocal Boost, Background Muffle, Per-App Channel routing, Per-App Volume sliders, and one-tap "OPEN FULL SONIC DECK" button.
* **Live In-Flight Cockpit Gear Shapes & DPI-Severed Geometry (`LightspeedCruiseOverlayGearsDraw.kt`, `LightspeedCruiseOverlayPrimitives.kt`, `CruiseFlankHandler.kt`, `CruiseTouchHandler.kt`)**:
  * Wired live in-flight Cockpit Gears layer (`drawFavoritesGearsLayer`) directly to `CockpitGimbalConfigCache` and `DeepSpaceGearGeometry` (`computeGearUnitScale`, `buildGearRingPath`, `pointOnGearPerimeter`).
  * Live in-flight gears now authentically render user-configured parametric ring shapes (Circle, Square, Squircle, and Star) with physical DPI-independent scale and perimeter-locked flight reticles.
  * Synchronized gesture tracking in `CruiseFlankHandler` and `CruiseTouchHandler` with `pointOnGearPerimeter` targeting.

### Fixed & Hardened
* **Category Cruise Vertical Scrubbing & Visual Updates (`LightspeedCruiseOverlaySpatial.kt`)**:
  * Restored continuous vertical `categoryVisualOffset` computation and 3D cylinder progress mapping during finger drags in `CruiseLayer.CATEGORY`. Fixed visual category cylinder freezing in place when moving finger vertically.
* **Category Cruise Reachability & Multi-Exit Recalibration (`LightspeedCruiseOverlaySpatial.kt`, `LightspeedCruiseOverlayDraw.kt`, `LightspeedCruiseOverlayMetrics.kt`)**:
  * Fixed Row 0 becoming unreachable when entering and exiting categories: recalculated `categoryVisualOffset = activeCatCenterY - touchDownRawY` upon Grid-to-Category layer exit so the active category aligns with touch focus without drift.
  * Clamped `gridOriginY = gestureStartY.coerceIn(hF * 0.26f, hF * 0.46f)` and amplified vertical grid motion (`amplifiedY`) to ensure Row 0 (`hF * 0.22f`) and bottom rows remain reachable across 10+ consecutive category navigations.
  * Decomposed spatial and AGSL rendering pipeline into `LightspeedCruiseOverlayMetrics.kt` to ensure clean maintainability under 350 lines.
* **Deflector Origin Disambiguation (`LightspeedCruiseOverlay.kt`, `LightspeedAccessibilityService.kt`, `CruiseTouchHandler.kt`)**:
  * Resolved state leakage where Right Deflector swipes executed Left Deflector actions: introduced `@Volatile var isDispatchingFromLeftFlankView` guarded by `try { ... } finally { ... }` in `LightspeedAccessibilityService`.
  * Tied `isOpenedFromLeftFlank` strictly to `isDispatchingFromLeftFlankView` during `MotionEvent.ACTION_DOWN` in `CruiseTouchHandler`.
* **Vulkan (`skiavk`) Back Gesture Reliability & Native uinput Driver (`lsinputd.c`, `PowerGrabHelper.kt`, `ShizukuExecHelper.kt`, `ActionDispatcher.kt`, `ActionMethods.kt`)**:
  * Implemented native `KEY_BACK` emission on kernel `/dev/uinput` device in `lsinputd.c`, controlled via `"BACK"` command over stdin in `PowerGrabHelper.sendBack`.
  * Added direct `IInputManager.injectInputEvent` with virtual keyboard source in `ShizukuExecHelper.injectBack`.
  * Added `system:back` action token method resolution (`AUTO`, `A11Y_ACTION`, `SHIZUKU`) in `ActionMethods.kt` and `ActionDispatcher.kt`.
* **Synthetic Gravity Battery, Thermal & Package Switch Hardening (`LightspeedOrientationManager.kt`, `LightspeedOrientationEngine.kt`, `LightspeedAccessibilityServiceInit.kt`)**:
  * Added deduplication in `onForegroundPackageChanged` (`targetPkg == lastEvaluatedPkg && isLocked == lastEvaluatedLocked`) to eliminate unnecessary computations on intra-app window updates.
  * Added 160ms looper debounce on package transitions to prevent mid-animation double-rotations during system UI swipe gestures.
  * Auto-halted sensor portrait listeners and cascade evaluations when screen is off (`PowerManager.isInteractive == false`).
  * Extended internal write debounce window to 2500ms and added `isRecentInternalWrite` guard in rotation `ContentObserver` to prevent feedback loops.
* **Decomposition & Strict < 350 Lines Compliance**:
  * Extracted `CruiseMacroGestureHandler.kt`, `LightspeedCruiseOverlayPrimitives.kt`, `LightspeedCruiseOverlayMetrics.kt`, and `LightspeedCruiseOverlayGearsDraw.kt` to keep all codebase files strictly modular and AI-navigable.
* **12-Theme Stackable Home Widgets, Nebula Credits Hub ("Hall of the Universe") & Render Loop Speedups (`LightspeedStackWidgetProvider`, `LightspeedStackWidgetService`, `NebulaHallOfFameDialog`, `CockpitGimbalConfigCache`, `LightspeedDeflectorPillDraw`, `DeepSpaceHangarFooterDraw`)**:
  * 12-Theme Stackable Home Screen Widget (`LightspeedStackWidgetProvider` & `LightspeedStackWidgetService`): Multi-card stackable widget rendered with active theme tokens and optical glass surfaces: Card 1 (Tactical App & Deep Shortcut Grid), Card 2 (Direct Hardware Toggles: Vocal Boost, Smart Pixels, Extra Dim, Master Cutoff), Card 3 (Cockpit Telemetry, Gear Set Quick-Switcher, and Central Command launcher).
  * Nebula Credits Hub ("Hall of the Universe" - `NebulaHallOfFameDialog`): Interactive cosmic registry in Central Command honoring open-source giants across App Store Nebulae (Shizuku, Obtainium, Orion, Shizuku Stores), Media Supergiants (MiXplorer, VLC, mpv), Control Constellations (Essentials, KeyMapper), Atmospheric Horizons (Atmos Wallpaper), and Sonic Deck MIT pioneers (SplitAudio, android-realtime-voice-isolation).
  * Render Loop & Hot-Path Allocations Speedups: Hoisted `Paint`, `Path`, `RectF`, and corner radii float array allocations out of 120Hz render paths in `DeepSpaceRenderer`, `DeepSpaceHangarGimbalDraw`, and `LightspeedDeflectorRenderer`.
  * In-Memory Gimbal Config Cache (`CockpitGimbalConfigCache`): Replaced per-frame `defaultPrefs()` string queries with an in-memory preference listener cache for reticle styles, shapes, unit scale, and custom diameter scales.
  * Startup App Icon Prewarming (`LightspeedIconManager`): Added asynchronous background icon prewarming on `Dispatchers.Default` upon service connection to eliminate main thread drawable decoding delays.
* **Space Pods Floating Deck, High-Cadence Auto-Clicker & Deep App Shortcuts Pinning (`LightspeedSpacePodOverlay`, `SpacePodTileService`, `SpacePodSettingsSection`, `LightspeedDeepShortcutHelper`, `DeepShortcutsPinDialogContent`)**:
  * Floating, draggable, edge-snapping Space Pods overlay rendering up to 4 tactical pods with 4-way gesture bindings (Single Tap, Double Tap, Long Press, Flick) for actions, apps, and deep shortcuts.
  * Privileged Auto-Clicker Mode: Crosshair reticle view repeatedly dispatching automated taps at user-configurable high frequencies (25ms to 1000ms cadence) via direct Shizuku input injection.
  * Quick Settings Space Pods tile (`SpacePodTileService`) for instant overlay toggling.
  * Deep App Shortcut querying via `LauncherApps.getShortcuts()` (with Shizuku dumpsys fallback) and integrated long-press menu options in Category Cruise to launch deep shortcuts or pin apps directly to Cockpit Gears, Space Pods, or Home Screen.
  * Central Command Space Pods configuration suite (`SpacePodSettingsSection`) to customize pod count, diameter, opacity, shape, gesture bindings, and auto-clicker cadence.
* **Master Reactor Cutoff QS Tile & Cold Shutdown Orchestration (`LightspeedMasterCutoff`, `MasterCutoffTileService`, `DisplayWellnessSection`, `SonicDeckSettingsSheet`)**:
  * Unified `LightspeedMasterCutoff` providing 1-tap Cold Shutdown (releasing synthetic gravity, resetting Eye Care/SurfaceFlinger color matrices, clearing Smart Pixels, resetting per-app volumes, unregistering audio routing policies, stopping GTCRN neural isolation, and disabling accessibility services via `Settings.Secure` and Shizuku) and 1-tap Resurrection directly from a Quick Settings tile or the Central Command cockpit hero card.
  * Central Command Display Wellness suite (`DisplayWellnessSection`) with controls for Extra Dim, Eye Care blue light temperature, 20-20-20 screen rest reminders, OLED Smart Pixels density, and Mindful Airlock app gating.
  * Central Command Sonic Deck settings sheet (`SonicDeckSettingsSheet`) and one-tap Central Command shortcut in the Custom Volume HUD drawer.
  * Added action tokens in `LightspeedActionRegistry` and `ActionDispatcher` for `system:smart_pixels_toggle`, `system:eye_care_toggle`, `system:extra_dim_toggle`, `system:vocal_isolation_toggle`, and `system:volume_hud_show`.
* **Brightness Scrub Suite, Eye Care & OLED Smart Pixels (`LightspeedEyeCareEngine`, `LightspeedSmartPixelsOverlay`, `LightspeedConsciousAirlockOverlay`, `CruiseScrubEngine`)**:
  * 3-tier Eye Care & Extra Dim suite: Native Android `reduce_bright_colors` / `night_display` via `WRITE_SECURE_SETTINGS` / Shizuku, Shizuku `SurfaceFlinger 1015` hardware compositor 4×4 color matrix, and accessibility overlay sub-zero dimming.
  * Seamless Sub-Zero Scrubbing in `CruiseScrubEngine`: Scrubbing below 0% brightness dynamically transitions into Extra Dim (10% to 90%) with interactive tactile HUD feedback.
  * OLED Smart Pixels mesh with 8×8 `BitmapShader` (12%, 25%, 50%, 75% subpixel shutoff), 60-second spatial burn-in shift, and automated suppression when Package Installer / Permission Controller windows appear.
  * 20-20-20 Rule continuous screen-on wellness monitoring with timed telemetry alerts.
  * Mindful Airlock: 5-second breathing gate for addictive apps with daily usage statistics, abort/proceed choices, and a 10-minute session grace period.
* **Ultra-Fast < 2ms Vulkan Back Gesture (`ShizukuExecHelper`, `ElevatedTaskCloser`, `ActionDispatcher`)**:
  * Direct IPC key event injection via `android.hardware.input.IInputManager` through Shizuku binder, reducing Vulkan / SkiaVK back fallback latency from ~100ms shell execution down to under 2ms.
* **Clean-Room Sonic Deck Overhaul, Custom Volume HUD & Neural Voice Isolation (`SonicAppVolumeEngine`, `SonicChannelRouter`, `SonicVoiceIsolationEngine`, `LightspeedVolumeHudOverlay`, `LightspeedVolumeHudContent`)**:
  * Clean-room per-app volume and loudness boost engine with Shizuku/AOSP `AudioPlaybackConfiguration` client mapping, `setVolume(0f..1f)` player proxy attenuation, `LoudnessEnhancer` boost up to 200%, preference persistence, lossless backup restore, and auto-reset to unity on service teardown.
  * Multi-channel routing engine (MIT SplitAudio port) enabling per-app audio output pinning (Speaker vs. Bluetooth / Headphones) and persistent Always-On Audio Focus (`TAKE_AUDIO_FOCUS ignore`).
  * Neural Cinema Voice Isolation engine (MIT android-realtime-voice-isolation port) featuring onnxruntime-backed 512-point STFT/iSTFT DSP pipeline with `gtcrn.onnx` neural model, providing independent slider controls for Dialogue Vocal Boost and Background Noise/Music Muffling.
  * Custom Lightspeed Volume HUD replacement overlay intercepting hardware volume keys and scrub actions with `flags = 0` (hiding stock Android volume dialogs), featuring collapsed and expanded drawer modes styled with Lightspeed optical theme tokens.
* **Cockpit Gear Shapes Archetypes & DPI-Severed Sizing (`DeepSpaceGearGeometry`, `DeepSpaceHangarGimbalDraw`, `DeepSpaceHangarControlsDraw`, `CockpitHangarTouchHandler`)**:
  * Introduced 4 parametric gimbal ring archetypes: Circle, Square, Squircle (n=4 superellipse), and 6-point Star with real-time perimeter tracking for app pods, collimator reticles, and magnetic cogs.
  * Implemented physical DPI-severed unit scaling via hardware `xdpi`/`ydpi` display metrics, ensuring gimbal rings maintain exact physical millimeter dimensions regardless of software Android display scaling or smallest width overrides.
  * Added interactive tactile Hangar Deck controls to cycle shapes (`CIRC / SQR / SQRC / STAR`), adjust diameter scale (50% to 200%), and toggle DPI independence.

### Fixed & Hardened
* **Phase 2 Canonical Audit & Runtime Hardening (`REFACTORING_AUDIT_PROMPT.md`)**:
  * **Ultra-Fast < 2ms Space Pod Auto-Clicker**: Upgraded Space Pod auto-clicker from shell `input tap` to direct binder IPC via Shizuku `IInputManager.injectInputEvent` (zero process spawns) with seamless fallback to `AccessibilityService.dispatchGesture`.
  * **Android 12-16 Widget Hardening (`LightspeedStackWidgetProvider`, `LightspeedStackWidgetService`)**: Added explicit `FLAG_UPDATE_CURRENT or FLAG_MUTABLE` to widget click pending intent templates and individual AppWidgetId iteration for `notifyAppWidgetViewDataChanged`.
  * **SurfaceFlinger 1015 Safety & Native Fallback (`LightspeedEyeCareEngine`, `DisplayWellnessSection`)**: Clamped SurfaceFlinger 1015 RGB multipliers to `0.15f..1.0f` (never 0f) to prevent display blackouts, defaulting to native Night Light / accessibility overlay until explicitly enabled.
  * **Real-Time Sonic DSP & Audio Affinity (`SonicVoiceIsolationEngine`, `SonicChannelRouter`)**: Added hardware session-0 acoustic presence equalization (800Hz-4kHz boost, <250Hz cut) and warm-loop background coroutine to maintain real-time GTCRN latency; wired reflection-based `AudioManager.setUidDeviceAffinity` and Shizuku `TAKE_AUDIO_FOCUS ignore`.
  * **Modular Architectural Decomposition (< 350 Lines Invariance)**:
    * Decomposed `HudVaultExperimentalComponents.kt` into `HudConfigVaultSection.kt`, `HudSystemOverridesSection.kt`, `OrbitalCapsuleLabSubSection.kt`, `PowerButtonLabSubSection.kt`, and `HudExperimentalLabsSection.kt`.
    * Modularized `ActionDispatcher.kt` by extracting `ActionDispatcherSonicDisplay.kt` and `ActionDispatcherSystemHelpers.kt`.
    * Split `LightspeedActionRegistry.kt` by extracting `ActionTokenResolver.kt`.
    * Decomposed `LightspeedKeyEngine.kt` by extracting `LightspeedVolumeKeyEngine.kt`.
    * Modularized `LightspeedAccessibilityService.kt` by extracting `LightspeedAccessibilityServiceInit.kt` and `LightspeedForegroundTracker.kt`.
    * Split `LightspeedVolumeHudContent.kt` by extracting `LightspeedVolumeHudDrawer.kt`.
* **Category Cruise Top-Row Y-Ratchet Fix (`LightspeedCruiseOverlaySpatial`)**: Eliminated downward drift of Category Cruise when alternating between Category column and App Grid without lifting thumb; re-anchored gesture start position to the active category's true visual coordinate and reset viewport scroll offset to 0 on transitions.
* **Left/Right Deflector Mirroring Guard (`CruiseFlankHandler`, `CruiseTouchHandler`)**: Explicitly tracked entry flank origin on `ACTION_DOWN` so right-side deflectors and center cruise never inherit left-flank mirroring flags.
* **Resilient Hardware/Vulkan Back-Gesture Fallback (`ActionDispatcher`)**: Added Shizuku `input keyevent 4` fallback when accessibility service returns false or is unresponsive, ensuring native back navigation succeeds under SkiaVK / hardware renderer transitions.
* **Synthetic Gravity Thermal & Orientation Glitch Guard (`LightspeedOrientationEngine`, `LightspeedOrientationManager`)**: Added cached state verification to bypass duplicate ContentResolver and Shizuku rotation writes when active orientation matches. Added proper `stopActiveSensorPortraitDriver()` invocation when leaving sensor-driven buckets and ignored transient SystemUI recents transitions during app-switching.
* **Custom App Icon Persistence (`LightspeedShortcutManager`)**: Preserved manually overridden `app:*` icons in `purgeCorruptedIcons()` so custom icons on standard apps survive app and service restarts.
* **App Shortcut Creation Wizard (`CockpitGearPickerScreenContent`)**: Wired `ACTION_CREATE_SHORTCUT` result handling to `extractShortcutToken()`, restoring third-party app shortcut creation in the Action Selection Menu.
* **Watchdog & Backup Security Hardening (`LightspeedWatchdogEngine`, `GriffinRecovery`, `LightspeedBackupEngine`)**: Fixed shell `case` brace syntax in service revival, enforced `ShellArgGuard` validation on all watchdog and battery-whitelist package/component arguments, and refreshed Category Cruise caches immediately upon backup import or factory reset.
* **Central Command & Guidebook Navigation (`CentralCommandConfig`, `CentralCommandState`)**: Added `System Overrides` to Expand/Collapse All and Solo modes on the HUD Strip tab, fixed Guidebook jump targets (`volumekeys`, `backtap`, `refueling_bay`, `orbital_capsule`), and fixed External Automation `-p <pkg>` command formatting.
* **UI Encoding & Offline Licensing (`TacticalWidgetPickerModal`, `AboutFeedbackSection`)**: Fixed corrupted UTF-8 symbols in widget grid dimensions and OEM diagnostics, and added an in-app offline Open-Source Notices dialog (MIT / Apache 2.0).

### Performance & Refactoring
* **Orientation Cascade 120ms Debounce & UI Sensor Frequency (`LightspeedOrientationManager`)**: Added a 120ms coroutine debounce before evaluating gravity cascades to absorb rapid foreground package switches during app transitions, and changed sensor portrait driver listener frequency from `SENSOR_DELAY_NORMAL` to `SENSOR_DELAY_UI` to eliminate battery and heat spikes.
* **Modularized Subsystem Architectures (File Splits #6 - #9)**:
  * Extracted `SidebarAttitudeTargetScanner.kt` from `SidebarAttitudeSheet.kt`.
  * Modularized `DeepSpaceRenderer.kt` into `DeepSpaceHangarControlsDraw.kt` and `DeepSpaceHangarGimbalDraw.kt`.
  * Split `WatchdogComponents.kt` to extract `WatchdogBlackboxSection.kt`.
  * Decomposed `HudSyntheticGravitySection.kt` into `HudGravityBucketsSection.kt` and `HudGravityPoliciesSection.kt`.
* **120Hz Zero-Allocation Status Bar & Overlay Renderers (`LightspeedStatusBarTickerDraw`, `LightspeedStatusBarRailDraw`, `LightspeedCruiseOverlayEditMode`)**: Hoisted `Paint` and `Path` instances and cached custom `Typeface` instances out of `onDraw()`.
* **Debounced Native Back-Gesture IPC & Window Blur (`LightspeedAccessibilityOverlays`, `MainActivity`, `CentralCommandActivity`, `CockpitGearPickerScreenContent`)**: Eliminated redundant Shizuku shell spawns on window state changes and migrated `SideEffect` window blur calls to `LaunchedEffect`.

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
* **App Categorization Overflow**: Early static heuristic mapping may place uncataloged apps into "Other" or "Utilities".
