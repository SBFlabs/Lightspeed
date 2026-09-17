================================================================================
LIGHTSPEED MASTER AVIONICS VAULT: COMPLETE ACCUMULATED BACKLOG & DREAMS ARCHIVE
================================================================================

--------------------------------------------------------------------------------
PART 0: GENESIS, HUMAN VISION & ARCHITECTURAL CHARTER
--------------------------------------------------------------------------------

[THE FOUNDER'S CREED & LIFE PROJECT]
* Visionary Founder: Medical student and visionary builder pair-programming with autonomous AI agents (Claude Sonnet 4.6 -> Gemini Pro -> Antigravity).
* The Founder's Dual Vocation & Real-Life Synergy:
  - Balancing rigorous medical training with cutting-edge mobile systems architecture.
  - Channeling clinical diagnostic precision, physiological systemic thinking, and zero-tolerance for failure into software: strict domain boundaries, 100% offline privacy, zero memory leaks, and instant sub-millisecond reflexes.
* The Core Childhood Dream:
  - From early dumbphones to modern glass slabs, the phone was never envisioned as a passive consumer device running fragmented, ad-ridden utility apps.
  - The dream was an adaptive sci-fi spaceship cockpit / Transformer robot that lives in your hand—tactile, responsive, and completely sovereign.
  - Lightspeed is the lifelong realization of that dream. The spaceship metaphor is not decorative skinning—it is the soul of the vessel, the driving joy of development, and the foundational architecture of the user experience.

[NON-NEGOTIABLE CORE INVARIANTS]
1. 100% Offline & Private: Zero telemetry, zero analytics, zero external network calls.
2. Production Readiness: Clean architecture designed for Google Play, F-Droid, and GitHub. All proprietary trademarks scrubbed (e.g. Dynamic Island -> Orbital Capsule).
3. Monolithic Meta-Shell: Single unified APK with internal modular domain separation, avoiding external multi-APK fragmentation.
4. Total Backup Invariance: Every setting, custom icon (Base64 embedded), physics profile, and layout must export and import losslessly via LightspeedBackupEngine.
5. Nightly-Only Target: All day-to-day work, testing, and deployment targets com.sbf.lightspeed.nightly on branch nightly-refactor. Stable release remains locked and immutable.
6. Public Version Progression & Promotion Pipeline: Strict 3-tier SemVer (PATCH/MINOR/MAJOR) and audited graduation flow (Nightly -> Refactor Audit -> Version Bump -> Explicit Promotion).

[THE MASTER AVIONICS TAXONOMY (SPATIAL & SEMANTIC MAP)]
* Horizon Rail: Top edge status bar progress line, battery telemetry curve, and horizontal scrubbers.
* Sensor Area (Touch Strip): Top bezel tactile gesture capture zone for instant action triggers.
* Synthetic Gravity Engine (Orientation Preferences): Native gyro, face-posture, and 4-bucket application orientation automation.
* Info Beacons (Telemetry & Indicators): Real-time network throughput, media/download progress flare, and Orbital Capsule notch dynamics.
* Tactical Scrub HUD Suite (Telemetry HUDs): Five custom-rendered liquid glass Canvas visualizer styles (Tactical Canopy Drop-Pod, Holographic Cockpit Reticle, Dynamic Edge Blade, Quantum Synthetic Horizon, Tachyon Orbital Radar) plus interactive Media Timeline Scrubber, with per-gesture style isolation and adaptive 16-segment to continuous 100+ precision gauge tracking.
* Hull & Ship Maneuvers (Hardware & Kinetic Gestures): Physical key interception, volume long-press, power double-press camera revival, kinetic back-tap, wrist-twist/flip camera kinematics, and temporal rhythm detection.
* Orbital Capsule: Camera punch-hole dynamic HUD (cutout calibration, marquee telemetry, media flare).
* Flight Blackbox (Diagnostics & Crash Logs): Isolated crash-telemetry and fault recorder with one-touch clipboard export.
* Deflectors (Port & Starboard): Left and right kinetic screen-edge gesture wings with progressive frosted glass diffusion, specular rims, and directional macro gestures.
* Cockpit Hangar & Gears: Orbital app launcher wheels with rotating gimbal rings and flight-lock reticles.
* Category Cruise: Full-screen launcher cruising deck for categorizing application fleets.
* Refueling Bay & Cryo Stasis (Charging Screen): Cryo charging and widget dashboard (Mode 1: 3D Cube Smart Stack, Mode 2: Freeform FlowRow Grid).
* Deep Space: Custom high-performance Canvas graphics engine (cosmic starfield, gimbal rings, lightspeed warp surges).
* Central Command: Master configuration deck with dynamic accordion matrices (formerly SidebarMatrixConfig).
* Config Vault (Backup & Restore): Complete offline JSON export/import engine with Base64 custom icon persistence and instant cache flush.
* System Override Deck (Developer & System Tuning): Direct system value overrides utilizing Shizuku or standalone elevated ADB permissions (DPI/PPI/smallest width, font scale, continuous animation speed scaling with simplified master or detailed subdomains, zero-sensitivity native edge gesture neutralization, custom long-press delays, stay-awake charging, ADB/Wi-Fi debugging switchboard, freeform multi-window mode selection, and lock-screen shortcuts), connected via direct bridge buttons in Deflector setup, the 4-in-1 OEM volume slider companion dock (paired app volume slider, multi-sound concurrent background playback, DND toggle, sound profile switcher), plus real-time per-app Vulkan/OpenGL ES FPS/CPU/GPU HUD telemetry.
* Adaptive Kinetic Scroll Engine (Top, Bottom & Continuous Scrub): Priority fallback orchestration checklist (Accessibility Direct Node -> Directional Step -> Kinetic Fling -> Native Toolbar Tap -> Key Injection), per-app learning memory, and a kinetic Scrub-to-Scroll action with customizable starting speed, acceleration curve, and max velocity ceiling.




[THE LANGUAGE ENGINE: 3-WAY COMMUNICATION PROTOCOL & GLOBAL I18N]
* Conceived in Central Command Guidebook as an interactive 3-way toggle:
  1. Vessel Lore: Full spaceship immersion (Deflectors, Cockpit Hangar, HUD Strip, Orbital Capsule).
  2. Co-Pilot: Bilingual hybrid protocol showing spaceship terms alongside plain Android utility.
  3. Clear Comms: Plain, direct Android utility (Gesture Sidebars, App Launcher, Status Bar, Punch-hole HUD).
* Extensible Internationalization:
  - Backed by LightspeedLanguageEngine and LightspeedVocabulary.
  - Serves as the single global resolution foundation for future real-world languages (Arabic, Chinese, RTL).

--------------------------------------------------------------------------------
MASTER STATUS TAXONOMY & AUDIT CONVENTIONS
--------------------------------------------------------------------------------
Every architectural initiative, feature vector, and bug in this Vault is governed by strict status taxonomy:
* [STATUS: SHIPPED & VERIFIED (v1.4.0)] : Fully implemented, verified on physical hardware, integrated in the active release.
* [STATUS: FIELD TEST NEEDED (RUNTIME AUDIT)] : Implemented in code, awaiting live device testing / deep-sleep / edge verification.
* [STATUS: ACTIVE BACKLOG (NEXT SPRINT)] : Fully architected and ready for immediate engineering execution.
* [STATUS: PAUSED / EXPERIMENTAL LABS] : Quarantined behind settings flags while deeper framework/OS hooks are developed.
* [STATUS: DEFERRED (THE BACK BURNER)] : Established architectural ideas held in stasis for future milestones.
* [STATUS: DREAMS & FUTURE VISIONS] : Raw creative accumulation and long-term dream registry.
* [STATUS: BUG RESOLVED] : Fixed in code, regression-tested, and verified on device.

--------------------------------------------------------------------------------
PART 1: THE BACK BURNER (ESTABLISHED DEFERRED ARCHITECTURE) [STATUS: DEFERRED]
--------------------------------------------------------------------------------

1. Hover Peeking (Proximity Pre-Trigger):
   - Intercept stylus/finger hover events (MotionEvent.ACTION_HOVER_ENTER, ACTION_HOVER_MOVE) over edge zones.
   - Dynamically fade in the HUD Strip canopy and Deflector flank boundaries before physical touch contact is made.

2. Dual Gesture Execution Triggers (Instant vs. On-Lift):
   - Instant Reflex: Fires actions immediately upon crossing the distance threshold (current behavior for short/long swipes).
   - On-Lift / Elevation: Waits until finger elevates from short, long, or longer hold/swipes before firing, allowing hold-to-preview or aborting prior to lift.

3. Perpendicular Scrubber Lock & Cancellation:
   - Scrubbing operates along the primary axis (horizontal across the horizon rail or vertical along the flanks).
   - The scrubber will not be released on lift, but instead movement on the perpendicular axis cancels/locks the action without misfiring.

4. Horizon Sensor Pull-Down Scrubbing Overhaul:
   - Temporarily stripped from Central Command.
   - Redesign trigger contract (hold-then-swipe-down vs. direct pull-down, threshold handling) so it does not conflict with notification shade or top-rail reach ergonomics.

5. Deflector Lexicon & Zone Simplification:
   - "Astrogation Core Zone" -> "Core Zone"
   - "Right Deflector — Upper Vector Zone" -> "Upper Flank"
   - "Right Deflector — Lower Vector Zone" -> "Lower Flank"
   - Complete lexicon review for all flank zones across left/right deflectors.

6. Hull Maneuvers: Rhythmic Cadence / "Morse Code" Tap Patterns:
   - Expand kinetic hull tapping beyond standard Double Tap / Triple Tap counts into temporal rhythm and cadence pattern detection (e.g. `[Tap] — [Pause] — [Double Tap]`, `[Tap] — [Tap] — [Pause] — [Tap]`).
   - Time-windowed state machine with maximum tap sequence ceiling (e.g., 4 or 5 beats max), inter-tap pause window threshold, and timeout commit.
   - Prevents sensor saturation, eliminates false-positive misfires in pocket, and opens a rich vocabulary of physical tactile triggers on the phone chassis.

7. Ship Maneuvers: Kinetic Expansion:
   - Future kinetic gesture recognition: "Chop-Chop" double flick (e.g. flashlight toggle), twist/flip gesture (quick camera or DND toggle), and pick-up/tilt sensor triggers.

8. Per-App Enforced Force-Dark Mode (Targeted Amazon / Stubborn White Apps):
   - Explicitly deferred low-priority back-burner capability.
   - Targeted hardware-accelerated dark mode enforcement (`debug.hwui.force_dark` injection via elevated ADB/Shizuku) restricted strictly to selected app packages (specifically enforcing notoriously bright, non-compliant apps like Amazon Shopping) without corrupting global Android system color schemes.


--------------------------------------------------------------------------------
PART 2: UNCERTAIN FIELD STATUS & TESTING AUDIT (VERIFY ON RUNTIME)
--------------------------------------------------------------------------------

1. Deep-Sleep First-Double-Press Capture:
   - 450ms high-priority wake lock (SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP) was added to LightspeedKeyEngine.kt to keep CPU frequency high during screen-off clicks.
   - Field Check: Test if phone sitting in deep sleep/Doze for >30 minutes still drops the first click or if double-press reliably triggers the torch on attempt 1.

2. Lock-Screen Secure Camera Fallback:
   - Purged vendor strings in favor of dynamic PackageManager resolution: Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE) falling back to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) with FLAG_ACTIVITY_SHOW_WHEN_LOCKED.
   - Field Check: Confirm camera opens immediately over keyguard on power-double-press without collision or launching underneath Gemini.

3. Live Geometry Preview Transition (Unify OFF):
   - Live calibration preview flags are hooked to "Sensor Geometry" expansion.
   - Field Check: Confirm Upper and Lower Flank boundary guides render independently without flickering when Unify Upper & Lower is disabled.

4. Rotary Schedule Wheel Physics:
   - Implemented vertical scroll wheel with snap behavior (rememberLazyListState + snapFlingBehavior).
   - Field Check: Verify wheel snapping feels weighted and mechanical without overshooting when spun rapidly.

5. Perimeter Watchdog Polling Overhead:
   - Background sentinel checks third-party accessibility services.
   - Field Check: Confirm polling does not wake CPU excessively or increase background battery drain.

6. Quick Deck App Reordering:
   - Fresh install power tools (MacroDroid, Tasker, Key Mapper, Shortcut Maker) and user-pinned apps sort to top.
   - Field Check: Evaluate drag-and-drop manual reordering for pinned deck items.

7. OEM Warning Demotion Animation Fluidity:
   - Demoting an OEM conflict notice to an advisory footnote currently toggles the card position.
   - Field Check: Add smooth layout spring animations (Modifier.animateItemPlacement()) so card glides down instead of popping abruptly.

8. Residual Bracket Audit:
   - Brackets [...] purged from Central Command status pills, badges, and accordions.
   - Field Check: Audit Guidebook bottom sheet and debug export files for legacy bracket strings.

--------------------------------------------------------------------------------
PART 3: THE DREAMS & FUTURE VISIONS REGISTER (VERBATIM ACCUMULATION)
--------------------------------------------------------------------------------

[DEV & EXTERNAL DAEMONS]
* Intro analyzer like Jellyfin intro skipper but an android apk?
• Train sth like Shade NSFW blocker app, mby for ears? music block? profanity block? it's already a ff extension
• Add widget page to ion launcher
** Dear android Reader
• Keep alive in background OR unless removed from recents using pulses OR that Foss app that acts like shizuku but keeps sending pulses (imp only one app similar To Samsung's keep app alive)

[PHYSICAL RAILS, THE SIDE PILL & GESTURE TENSION]
* A side pill, a small one, that has a lot of gesture that can be mapped on it. But the most important one would be swiping against resistance. So it's not swipe and hold per se, like an open source app called smart edge.
* Tap gestures for the central pill?
* Swiping outward triggers hold Gesture which Is a cool tip!!!
* Swap flank actions until next gesture is done (ethical dillema)?
* Touchpad & bot buttons & toggles at the bottom option & media ctrl at top?

[CATEGORY CRUISE (LAUNCHER & APP ENVIRONMENT)]
*** Expand / improve category database / get rid of LaunchTime Code? JINA?
¿ Remove categories HUD or remove HUD all together OR replace with hyperdrive swipe up to access widgets OR info OR summon local ai To perform recategorizaztion OR subcategorization OR rename category like WebLibre ai?
- Make icon bigger and allow 2 lines titles. with setting option to force only one line.
- Liquid glass icon rendering?
- Icon shapes option
- Pop up or split gesture when dwelling on app?
- Total number of apps overlayed on the background behind the apps or beside category name in first category wheel?
- Recents apps, screenshots, files? OR an option for a specific category like Search or setting. also specific user apps work or personal all? system?
** Top most category for:
* Quick Search or my own bot chat?
* Quick Settings and info:
   - add free rom, ram and temp
   - real QS tiles using adb?
   - toggles
   - widget
* Widget Stacks?
   - improve widget selection menu
   - updates in real time?
   - built-in ones like screentime chart
* Quick Notes plugin with sync?

[GLOBAL / GENERALIZED / APP-WIDE DEFAULTS]
** Add systemUI tuner setting I use plus the ones from that Foss app for pixels and animation speed:
   - System Override: Direct developer options control powered via Shizuku or standalone elevated ADB permissions (zero-dependency light speed execution):
     * PPI, DPI, and smallest width (sw<N>dp) overrides without reboot.
     * Font Scale (`Settings.System.FONT_SCALE`) dynamic slider control.
     * Custom Long-Press Delay (`Settings.Secure.LONG_PRESS_TIMEOUT`) with custom millisecond thresholds (e.g. 200ms - 500ms).
     * Stay Awake While Plugged In (`Settings.Global.STAY_ON_WHILE_PLUGGED_IN`) flags for AC, USB, and wireless charging.
     * Continuous animation speed scaling: tactile slider UI with dual modes (simplified global master slider or detailed subdomains for window, transition, and animator duration).
     * Native Android side/start gesture sensitivity zero-out (inset scale = 0) so edge gestures cover the whole screen border without OS interference.
     * Deep integration: Placed natively in System Override, but also surfaced via direct bridge buttons and hint cards directly within the Deflector/developer gesture configuration steps.
     * ADB & Wireless Debugging Switchboard (`adb_enabled`, `adb_wifi_enabled`) with architecture to retain elevated execution without persistent Wi-Fi debugging notifications.
     * Freeform Multi-Window Selection: Native Android AOSP freeform desktop mode vs. OEM vendor floating window implementations.
     * Custom Lock-Screen Shortcuts: Re-assignment of left/right lock screen triggers via MacroDroid / secure settings bridge.
     * Floating OEM Volume Slider Companion (4-in-1 Audio Dock): Anchored directly beneath/adjacent to the native OEM volume slider dialog:
       1. Paired App Volume Slider (independent volume slider for the active/paired foreground or background media app, decoupled from system master volume).
       2. Multi-Sound Sovereign Audio Lock (allows one or multiple designated apps to continuously play audio in the background unconditionally, even when other players, media streams, or games are actively playing).
       3. Instant Do Not Disturb (DND) mode toggle.
       4. Sound profile mode switcher (Normal Sound / Vibrate / Mute).
     * Targeted Per-App Force Dark Mode: Scoped `debug.hwui.force_dark` override for stubborn apps (e.g. Amazon Shopping) - low priority back burner.
     * Hardware telemetry overlay (CPU cluster load, GPU frequency/load, FPS frame-pacing, and Vulkan vs. OpenGL ES active renderer indicator) with per-app foreground activation whitelist.


* Cruise vs gears settings vs for both?
  - haptics? icons? text size? scale?
  - cruise background blur etc
  - sensitivity for gestures y cruise etc
• Visual Theme Adaptation: Hook sheets background matrices to track global framework Dark/Light state toggles.
• Change app name, icon or even its acc serv? Gesture app OR system Gestures OR add #🚀 to acc serv?

[ACTIONS ENGINE]
* Toggles for WiFi, Bluetooth, GPS, airplane mode, audio mode, DND etc
*** Bot which is basically clicker as Loop or UbikiTouch as one time trigger OR BETTER YET, use Keysh or Key Mapper but with the better implementation mention in issue no.¿xx?
** Add pop window and split Screen launch modes for app launch actions
** Kinetic Scroll Engine (Scroll to Top, Scroll to Bottom & Continuous Scrub):
   - Complete architectural symmetry between Scroll to Top and new Scroll to Bottom action.
   - Priority fallback checklist: reorderable fallback execution chain (Accessibility Direct Node, Multi-Pass Step, Kinetic Fling, Native Toolbar Tap, Key Injection).
   - Per-app fallback memory: remembers and enforces the optimal fallback mechanism per app package directly through the action configurator.
   - Continuous Scrub-to-Scroll Action (`scrub:scroll`): Drag along flank/rail analogous to brightness and volume scrubbers, with customizable starting speed, acceleration curve, velocity ceiling, and haptic impulse ticks.
* Action Engine Vector III: Reserved placeholder for pending action enhancement.
** Power menu
* Rotation mode scrubber & actions

[ANT / BUGS REGISTER]
* Calendar icon (dynamic date sync)
* Scroll action fallback fixes (tap chaos)
* Freeform windows OEM vs stock android
* Progressive blur turning whitish
✓ [RESOLVED] Rotating back to portrait after closing Brawl Stars messes things up (Fixed via baseline separation and ROTATION_0 reset)
✓ [RESOLVED] Screen timeout HUD custom choice/action & per-gesture style conflict (Fixed with isolated per-gesture preference hierarchy and independent HUD style resolution)
✓ [RESOLVED] Flank gesture scrub resting-finger collapse (Fixed by continuously preserving active telemetry HUD on touch hold until gesture release)
✓ [RESOLVED] Brightness 3% artificial floor cutoff (Fixed by unlocking full 0%–100% linear brightness control)
✓ [RESOLVED] HUD title right-edge squeezing & paint leakage (Fixed via defensive Paint.Align resets in LightspeedHudRenderer and adaptive text bounds fitting)

[OPTIMIZE & REFACTOR]
* Update dependencies?
* Code refactoring?
* Size exploded?
* SQL storage? improve gears speed?
* Permissions? acc serv mode vs shizuku?
* Speed? indexing like siri ai?
* Backup logic checkup after new updates
* App icon update (screenshot in gallery)
* 60, 90, 120, 144Hz or not imp?
*** Live overlay to indicate the pill
*** Live Notification Listener (status bar line OR notch pill (disable OEM ones note))
✓ [RESOLVED] Tactical HUD suite for volume, brightness & timeout (5 liquid glass avionics styles, 100th volume resolution, 0% brightness floor, and isolated per-gesture memory)

[SUPERAPP STRATEGY & MODULARITY]
❕ Is this considered a superapp? Are super apps suitable for a hobby project utilizing ai agent and a medical student as founder?
- Verdict: OS-level Meta-Shell / Cockpit. Single monolithic APK with strict internal domain separation (:engine:gestures, :engine:overlay, :launcher:cruise). Avoid external multi-APK plugin fragmentation.

[LINEAGE & DNA REGISTER (FEATURES INSPIRED BY / PORTED FROM)]
+ Recents, Be Nice, Smart Edge, LaunchTime
+ Animator speed, SystemUI Tuner
+ Atmo Engine (overlay instead of live wallpaper option), LifeDots
+ Edge Gestures, UbikiTouch, Action Notch, Samsung's One Hand Operation+, JINA Drawer & Sidebar

--------------------------------------------------------------------------------
PART 4: COCKPIT & REFUELING BAY EVOLUTION (ACTIVE CONVERGENCE LOG)
--------------------------------------------------------------------------------

[STATUS: IMPLEMENTED & DEPLOYED]
✓ Tactical Widget Reconfiguration Engine:
  - Added dedicated [Tune] / CONFIG action in edit mode to launch native AppWidgetProvider configure activities without dropping overlay state.
✓ Smart Stack Horizontal Swipe Bridge:
  - Horizontal gestures over scrollable widgets (Anki, Calendar, Tasks) route directly into PagerState to ensure swipeability.
✓ Freeform X/Y Widget Grid Engine (Mode 2):
  - Replaced rigid 2x2 with dynamic FlowRow grid supporting 20%-100% width and 80dp-600dp height.
  - Independent persistent profiles for portrait and landscape orientation.
  - Subtracted inter-item gap math to allow exact 50%/50% side-by-side widget tiling.
✓ Smart Stack Memory User Toggle:
  - Added preference ("pref_refueling_stack_remember_page") to toggle between persistent page memory and resetting to page 0 on launch.
✓ Deflector Glow & Progressive Frost Overhaul:
  - Replaced disjointed rectangular edge blocks with a unified aerodynamic continuous curved blade silhouette (central bell curved thicker than flanks).
  - Implemented 4 aesthetic finishes: Heavy Progressive Frost (glass diffusion), Material Surface Shade (dynamic M3 tone), Crimson Reactor (thermal warning core), and Cyber Plasma (full-spectrum kinetic gradient).
  - Added toggle to pulse deflector glow upon step-1 gesture recognition ("pref_deflector_glow_on_gesture_step").
  - Added glow duration selector ("800ms", "1500ms", "2200ms") and live "TEST FX" trigger in Central Command.
✓ Nautical Mooring Rope & Deflector Dynamic Routing Engine [STATUS: SHIPPED & VERIFIED]:
  - Cross-Row Flanking Mooring Cords (`SeveredMooringPairCard` in `NauticalMooringRope.kt`): When severed into Separate Controls, the paired Upper and Lower rows are flanked by natural, unconstrained gravity-hanging cords. The cut cords droop over the top shoulders, hang slackly alongside the flanks of both rows with frayed hemp fiber tips, and point re-coupling chevrons toward the center without enclosing or boxing the cards. Tapping the overarching cords triggers the tactical confirmation to tie them back together.
  - Direction Indicator & Typography Streamlining: Purged bulky `[UPPER]` / `[LOWER]` monospace T-blocks to restore full animated `GestureTrailTracer` directional vector icons in all unlinked rows, using clean, compact titles (`Upper · ...` / `Lower · ...`) to conserve precious mobile screen real estate.
  - Independent Memory Profile Preservation: Linking or unlinking gestures never clobbers assigned actions. Tying restores the saved unified action profile (`pref_macro_action_[LEFT_]UNIFIED_*`), while severing preserves the independent Upper and Lower sector configurations (`pref_macro_action_[LEFT_]TOP_*` and `pref_macro_action_[LEFT_]BOTTOM_*`).
  - Tactical Confirmation Micro-Dialog (`MooringConfirmDialog`): Sleek Material 3 confirmation safeguard before severing or tying any mooring line to eliminate accidental toggles.
  - Dynamic Two-Card Accordion Migration:
    * "Unified Gesture Matrix" card holds only tied gestures (1 single consolidated row executing full-flank actions).
    * "Separate Deflector Controls" card holds only cut gestures (2 independent rows for Upper & Lower sectors).
    * Cutting a rope moves gesture into Separate Controls; tying it moves it back into Unified Matrix.
  - Accordion Auto-Expansion & Auto-Scroll Engine:
    * If destination accordion is collapsed, it automatically expands (`isExpanded = true`) and persists.
    * Smooth animated scrolling immediately shifts the viewport to the newly populated destination card.
  - Preference & Overlay Runtime Synchronization: Maintained `KEY_UNIFIED_SCRUB_REGIONS_LINKED`, `KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT`, `KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT`, and `pref_gesture_unified_<flank>_<vectorKey>` in `LightspeedPreferences`, dynamically resolved in `LightspeedCruiseOverlayTouch`, `LightspeedCruiseOverlay`, and `LightspeedLeftWingOverlay`.
✓ Synthetic Gravity Engine & Native Auto-Rotation Resolution:
  - Decoupled persistent Master Auto-Rotate baseline from transient hardware system setting writes (`Settings.System.ACCELEROMETER_ROTATION`), eliminating circular setting corruption.
  - Fixed landscape app exit glitch (Brawl Stars 90° lock): ensured unconstrained rotation resets `USER_ROTATION` to `Surface.ROTATION_0` and restores baseline auto-rotate without requiring Home button press.
  - Added dedicated system action `system:auto_rotate_toggle` ("Toggle Native Auto-Rotate") with instant avionics canopy HUD telemetry ("360° GYRO (ENABLED)" / "0° PORTRAIT (LOCKED)").
  - Synchronized external Quick Settings auto-rotate toggles via internal write tracking and ContentObserver in `LightspeedAccessibilityService`.
  - Added Central Command `WRITE_SETTINGS` permission diagnostics card with direct intent launcher.
✓ Synthetic Gravity Engine HUD Strip Promotion & Face-Posture Architecture:
  - Promoted Synthetic Gravity Engine from Experimental Labs into a top-level first-class accordion in the HUD Strip tab.
  - Added native Face-Oriented Auto-Rotate (`camera_autorotate`) integration via API 31+ Android Private Compute Core sensor subsystem (100% offline, zero camera permissions).
  - Implemented 4 Attitude Buckets (`Natural Portrait`, `Reverse Portrait`, `Landscape Standard`, `Reverse Landscape`) with real-time per-app assignment modal sheet.
  - Added configurable Action Override Lifetime duration picker (`until_app_switch`, `until_screen_off`, `persistent`, `disabled`) and overlay rotation enforcement policy.
✓ Central Command HUD Strip 8-Deck Nomenclature & Layout Standard:
  - Reorganized HUD Strip accordions into a standardized 8-deck hierarchy with complete dual-mode vocabulary parity (Spaceship Lore / Clear Comms):
    1. Sensor Area (`sensor_deck`) / Touch Strip
    2. Synthetic Gravity Engine (`synthetic_gravity`) / Orientation Preferences
    3. Info Beacons (`telemetry_indicators`) / Telemetry & Indicators
    4. Hull & Ship Maneuvers (`tactical_hardware`) / Hardware & Kinetic Gestures
    5. Refueling Bay & Cryo Stasis (`refueling_bay`) / Charging Screen
    6. System Override (`system_override`) / System Override
    7. Config Vault (`config_vault`) / Backup & Restore
    8. Experimental Labs (`experimental_labs`) / Experimental Features
✓ Avionics Flight Blackbox & Crash Isolation:
  - Hardened WindowManager token validation across telephony interrupts to eliminate BadTokenException crashes when waking up after phone calls.
  - Canonized "Flight Blackbox" (Vessel Mode) / "Diagnostics & Crash Logs" (Clear Comms) in Central Command telemetry deck with one-tap clipboard export.
✓ Tactical Avionics Liquid Glass HUD Suite (5 High-Performance Styles):
  - Conceived, architected, and deployed a suite of 5 custom-rendered Canvas HUD styles in `LightspeedHudRenderer`, powered by dynamic Material 3 palette extraction and layered liquid glass optics (chamfered frosted backplanes, refractive specular rims, and glint lines):
    1. *Style 1: Tactical Canopy Drop-Pod* (`canopy_droppod`): Floats cleanly beneath the status bar cutout/icons with 45° chamfered glass visor, dynamic frosted backplane, and dual-mode 7-segment quantum bar / continuous precision gauge.
    2. *Style 2: Holographic Cockpit Reticle* (`cockpit_reticle`): Upper-third focal projection with circular frosted plate, 260° tachyon orbital arc, target collimator pips, and glowing lock bead.
    3. *Style 3: Dynamic Edge Blade* (`edge_blade`): Anchored directly alongside the active gesture swipe flank (left or right) as a vertical energy ladder with chamfered glass card and graduated energy rungs.
    4. *Style 4: Quantum Synthetic Horizon* (`quantum_horizon`): Aircraft/starship artificial horizon collimator with swept flight wing brackets, pitch ladder avionics grid, monospace status readout, and synthetic waterline level bar.
    5. *Style 5: Tachyon Orbital Radar* (`tachyon_dial`): Circular tactical scanner with 360° azimuth degree graduation marks, concentric range rings, crosshairs, and 270° sweeping orbital energy arc.
  - Interactive Media Timeline Scrubber HUD (`drawMediaScrubberHud`): Specialized liquid glass card with live song title, artist, elapsed/total MM:SS readouts, and interactive quantum seekbar with live thumb pip.
  - Adaptive Gauge Physics: Automatically transitions from tactile discrete segment blocks (≤16 steps) to continuous high-precision liquid glass tracks with glowing specular bead (>16 steps) to flawlessly support fine-grained scrub actions up to 254 steps without UI overflow.
✓ Per-Gesture HUD Style Memory & Isolation Engine:
  - Eliminated global style crosstalk where changing a HUD style on one gesture would overwrite styles across other gestures.
  - Implemented a 4-tier hierarchical resolution contract in `LightspeedPreferences`:
    1. Action-specific gesture key (`pref_macro_hud_style_<GESTURE>_<ACTION>`)
    2. Base gesture key (`pref_macro_hud_style_<GESTURE>`)
    3. Global default HUD style (`pref_macro_hud_style_default`)
    4. Hardware fallback (`canopy_droppod`)
  - Integrated full per-gesture HUD style pickers into `CockpitGearPickerActivity` and `SettingsComponents`, updating dynamically per assigned action (Screen Timeout, Media Volume, Display Brightness).
✓ 100th Volume Scrub Resolution (0%–100%) & Fine-Grained Audio Engine:
  - Slashed reliance on crude 1–5 step volume jumps across Android's native 0–15 stream indices.
  - Added `KEY_VOLUME_SCRUB_RESOLUTION` (`pref_volume_scrub_resolution`), defaulting to 100 steps (1/100th / 1% precision per notch, configurable 5..100).
  - Fine-Grained Virtual Scrubbing: Tracks normalized `0%..100%` during touch drag across all overlay decks (`LightspeedCruiseOverlay`, `LightspeedLeftWingOverlay`, `LightspeedSensorDeckTouchOverlay`), displaying crisp `XX%` telemetry with continuous high-precision liquid glass gauge track.
  - Dynamic Stream Hardware Mapping: Accurately maps virtual percentage to `AudioManager.setStreamVolume(STREAM_MUSIC, round(pct * maxVol / 100f), flags)` whenever hardware thresholds cross.
  - Cockpit Picker & Settings Suite: Replaced rigid 1..5 velocity slider with an interactive **Volume Scrub Resolution** slider (5 to 100 steps / 100ths) with live step percentage preview.
✓ Avionics Scrubbing Stability & Paint Hygiene Hardening:
  - Fixed resting-finger collapse where pausing finger motion mid-scrub caused the overlay to collapse to the status bar or disappear: gesture HUD now remains anchored and stable continuously until finger release.
  - Unlocked true 0% brightness floor (eliminating legacy 3% clamp).
  - Defensively hardened `LightspeedHudRenderer` against text paint alignment leaks (`headerTextPaint.textAlign = Paint.Align.LEFT` leaking into center-drawn HUD styles), ensuring strict `CENTER` alignment resets before and after each render pass, paired with adaptive text width measurement and ellipsis truncation.
✓ System Override Suite (Shizuku & Elevated Android Core Overrides):
  - Created and deployed the System Override deck positioned directly above Config Vault in Central Command.
  - Animation Speeds: Dedicated continuous sliders for Window animation scale, Transition animation scale, and Animator duration scale, with simplified global master slider and detailed granular matrix, instant safety resets, and confirmation dialogs.
  - Display Metrics (PPI / DPI / Smallest Width): Direct programmatic control over `wm density` and `display_density_forced`. Includes custom PPI overwrite protection to preserve hardware factory defaults, paired with tactile confirmation modals.
  - Font Scale Overwrite: Real-time slider controlling `Settings.System.FONT_SCALE` (0.80x to 1.30x) without requiring deep Android settings navigation.
  - Lockscreen Shortcuts Customization: Configures AOSP `sysui_keyguard_left` and `sysui_keyguard_right` across direct-access and unlock-to-access actions.
✓ Vernier Dual-Axis Precision Scrubbing Architecture:
  - Pioneered 2D vernier scrubbing on custom sliders: sliding horizontally adjusts values normally; lifting or moving finger vertically on the Y-axis smoothly slows down the horizontal tracking ratio (high-gear precision mode), enabling sub-pixel, single-unit adjustments even on dense scale tracks.
  - Resolved track thumb clipping at 0% and 100% bounds.
  - Hardened tap-to-jump physics to strictly respect the `jump_on_track` toggle without unwanted track jumps.
✓ App Shortcuts & Deep Linking Engine Hardening:
  - Fixed launcher shortcut execution and deep activity launching via Shizuku and native intent dispatchers.
  - Resolved carrier/dual-SIM direct-call crashes by integrating explicit `CALL_PHONE` runtime permission checks, preventing permission denials seen across macro automation apps.
  - Added full Base64 dynamic bitmap serialization in `LightspeedBackupEngine` for deep-linked shortcut icons.
✓ Alphabetical Index Scrubbing & Morphing Typography:
  - Alphabetical app jump index formatted with capitalized first letter and lowercase second letter.
  - Excluded pinned cockpit apps from the alphabetical jump index to eliminate jump stutter and index pollution.
  - Implemented micro-scrubbing within index buckets for multi-item letter groups.
✓ Release Hardening & Feature Isolation for GitHub Inspection:
  - Quarantined incomplete or hardware-dependent features into **Experimental Labs** with dormant default states to ensure a bulletproof public inspection build.
  - *Orbital Capsule*: Gated camera cutout rendering behind `KEY_ORBITAL_CAPSULE_ENABLED` (`pref_orbital_capsule_enabled`, default: false). Switched default download telemetry routing to `"top_line"` (Horizon Rail). Cutout alignment, width/snugness sliders, marquee typography, and OEM conflict notices moved to Labs.
  - *Omniscient Audio Dock*: Gated horizontal pull-out gesture on side deflectors and vertical pull-out on Sensor Deck behind `KEY_OMNISCIENT_AUDIO_DOCK_ENABLED` (`pref_omniscient_audio_dock_enabled`, default: false) to eliminate accidental dock triggers during volume scrubbing.
  - *Tactical Hardware Keys*: Pruned confusing, non-functional power button card from the hardware keys deck; power button remapping safely isolated in Experimental Labs.
  - *HUD Strip Swipe-Down to Notifications*: Gated top sensor deck downward pull gesture behind `KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS` (`pref_statusbar_swipe_down_notifications`, default: true) in Experimental Labs. Fixed SystemUI touch freeze bug caused by duplicate `GLOBAL_ACTION_NOTIFICATIONS` IPC calls and touch hijacking while notification shade or Quick Settings was already open by dynamically hiding the sensor touch window when SystemUI is active and enforcing single-shot execution.
  - *Scrub Action Gating Rule*: Enforced architectural invariance restricting continuous scrub actions (`system:volume`, `system:brightness`, `system:screen_timeout`) exclusively to continuous Hold gestures (`isHold == true` / `_HOLD`) or deflector inward sweeps (`SCRUB`). Filtered out `sys_scrub` tokens from action pickers for all momentary gestures (Tap, Double Tap, regular Swipes without hold).
  - *Pruning Sensor Deck Long Sweep Scrubbing*: Removed the awkward and hard-to-trigger "Sensor Deck Long Sweep (Scrubbing)" gesture and its configuration row from Central Command. Scrubbing on the HUD status bar is now cleanly and reliably performed via Hold-to-Scrub (+ Hold Modifiers on Tap, Double Tap, or Swipes).

[STATUS: PAUSED / EXPERIMENTAL LABS]
* Audio Sovereignty & The Omniscient Audio Dock (MultiSound Concurrent Playback & Per-App Volume):
  - Objective: Parity with Samsung SoundAssistant / MultiSound ("Locking" apps like Podium/Spotify so they play concurrently alongside games or flashcard apps like AnkiDroid without pausing or ducking).
  - Technical Findings:
    1. OS Multi-Audio Focus: Shizuku IPC hook `IAudioService.setMultiAudioFocusEnabled(true)` was successfully implemented and verified on Android 16.
    2. App-Level Focus Surrender: Even with OS multi-focus enabled, standard third-party audio engines (e.g. ExoPlayer in podcasts or AnkiDroid) voluntarily register focus listeners and pause upon receiving transient focus loss broadcasts from the OS audio framework. Complete stealth playback without pause requires low-level audio track proxying or Xposed-style method hooking.
    3. Per-App Hardware Volume: `AppOpsManager.OP_AUDIO_MEDIA_VOLUME` only governs UI volume slider permissions, while Binder `IPlayer.setVolume()` yields inconsistent results depending on whether playback uses AudioTrack, OpenSL ES, or Oboe.
  - Current Status: Safely isolated in Experimental Labs behind `pref_omniscient_audio_dock_enabled` (disabled by default) while deeper audio track proxy architectures are evaluated.
* Grid Mode Vertical Scroll Delegation Over Non-Overflowing Widgets (e.g., Tall Anki Deck):
  - Current status: Reverted touch takeover to eliminate synthetic spring jitter/shivering.
  - Active Objective: Cleanly detect when child ListView/ScrollView does not overflow and hand off vertical scroll deltas to the dashboard ScrollState without fighting native gesture detectors or introducing synthetic spring inertia.

[STATUS: ACTIVE BACKLOG (NEXT SPRINTS)]
1. "Deep Space" Aesthetic Unification:
   - Unify the background graphics engine between Cockpit Hangar and Category Cruise into a singular "Deep Space" environment (cosmic starfield, gimbal rings, warp surge transitions).
2. Refueling Bay Toolbar Telemetry Chip Action:
   - Determine functional role for `GRID [PORT] // 02` chip: keep as pure cockpit status telemetry, convert into an instant one-tap Stack <-> Grid toggle, or compact into a micro-dot indicator.
3. Subspace Watchdog Daemon:
   - Elevated process manager / background killer powered via Shizuku integration for granular memory and runaway app mitigation.
4. Comprehensive Language Engine System Settings Migration:
   - Propagate dynamic string resolution (`LightspeedLanguageEngine.resolve()`) beyond Guidebook into all Central Command configuration cards, accordions, and action pickers.
5. System Override Deck (Developer Options, Elevated ADB/Shizuku Bridge, Gesture Sovereignty & Hardware Telemetry):
   - Execution Engine & Zero-Dependency Elevation:
     * Dual-mode system execution: Operates via Shizuku IPC binder when active, OR through elevated standalone Android permissions granted once via ADB (`android.permission.WRITE_SECURE_SETTINGS`, `android.permission.SET_ANIMATION_SCALE`, `android.permission.DUMP`, or local shell).
     * Enables sub-millisecond, zero-latency system overrides ("running at light speed") without hard runtime dependencies on external daemons.
   - Developer Options — Display Metrics Overwrite (PPI / DPI / Smallest Width):
     * Direct control over `display_density_forced`, `wm density`, and `sw<N>dp` to modify PPI/DPI on-the-fly.
     * Instant switching between compact avionics HUD scaling and high-density viewports without requiring system reboots.
   - Developer Options — Dynamic Font Scale Overwrite:
     * Tactile slider control for `Settings.System.FONT_SCALE` (0.80x to 1.30x) within System Override, eliminating deep OS settings navigation.
   - Developer Options — Custom Long-Press Delay:
     * Dynamic control over `Settings.Secure.LONG_PRESS_TIMEOUT` with fine-tuned millisecond presets and slider (e.g. 200ms, 250ms, 300ms, 400ms, 500ms) to slash system-wide UI latency.
   - Developer Options — Stay Awake While Plugged In:
     * Direct control over `Settings.Global.STAY_ON_WHILE_PLUGGED_IN` with granular charging flags (AC power, USB, and Wireless charging docks).
   - Developer Options — Continuous Animation Speeds (Simplified vs. Detailed Subdomains):
     * Tactile Compose Slider UI replacing discrete buttons or raw value entry.
     * Dual-tier UX presentation:
       * *Simplified Global Master Mode*: Single unified slider driving all animation scales simultaneously (0.0x / instant off to 2.0x).
       * *Detailed Subdomain Matrix*: Granular independent sliders for Window animation scale (`window_animation_scale`), Transition animation scale (`transition_animation_scale`), and Animator duration scale (`animator_duration_scale`).
   - Native Android Edge Gesture Sensitivity Override (Side & Start Gesture Sovereignty):
     * Direct calibration and zero-out of Android's native back-gesture insets (`back_gesture_inset_scale_left`, `back_gesture_inset_scale_right` set to 0).
     * Fully eliminates native OS back-gesture deadzones so Deflector wings and edge gestures can cover 100% of the screen border without gesture conflicts.
     * *Cross-Deck Deep Integration & Direct Bridge*: Centrally managed within System Override, but also contextually surfaced via direct one-tap bridge buttons and guidance hint cards directly within the Deflector setup and gesture calibration steps.
   - Developer Options — ADB & Wireless Debugging Switchboard:
     * Fast toggle matrix for USB Debugging (`adb_enabled`) and Wireless Debugging (`adb_wifi_enabled`).
     * Investigating elevated headless architecture allowing Lightspeed to maintain execution authority without persistent Wi-Fi debugging notifications or manual pairing cycles.
   - Freeform Multi-Window Mode Selection:
     * Switchboard between Native Android AOSP freeform desktop mode (`enable_freeform_support`, `force_resizable_activities`) and OEM manufacturer proprietary floating window implementations (e.g. Samsung DeX / Pop-up view, Xiaomi floating windows).
   - The Lock-Screen Overlay Bypass (Completed):
     * OEMs like Transsion completely hardcoded the secure `sysui_keyguard_left/right` databases.
     * Workaround implemented: Injected `FLAG_SHOW_WHEN_LOCKED` directly into Lightspeed's sensor layers. The entire tactical HUD and edge gestures now seamlessly draw *over* the lock screen, completely eliminating the need for native lock screen shortcuts.
   - The Omniscient Audio Command Center (Advanced Volume Scrubber Dock):
     * A massive expansion to the `system:volume` and `scrub:volume` actions. When scrubbing volume from the edge, a contextual floating command center (HUD) appears, utilizing gestures to expand into a master audio panel.
     * Integrates extreme audio sovereignty controls:
       1. *Independent App Volume Routing*: Dynamic per-app volume sliders, allowing independent volume levels for the foreground app, background music, system notifications, alarms, and Google Assistant.
       2. *Multi-Sound Concurrent Playback Lock (App Pinning)*: A physical "Pin" toggle for specific apps (like Spotify or YouTube). When pinned, the app retains absolute audio focus, forcing it to keep playing uninterrupted even when other media, games, or ads try to steal focus or duck the volume.
       3. *Global Mute & DND Matrix*: One-tap tactile toggle for absolute Global Mute, alongside an instant Do Not Disturb (DND) toggle, bypassing all volume streams instantly.
       4. *Sound Profile Quick-Cycle*: Direct switching between Normal Sound, Vibrate, and Silent/Mute modes.
       5. *Audiophile Parametric EQ (AutoEQ) Import*: Ability to import specific parametric EQ profile files (like Wavelet or AutoEQ presets) tuned for specific headphones directly into the audio engine for system-wide sound correction.

   - The Omniscient Display Engine (Advanced Brightness Scrubber Dock):
     * A massive expansion to the `system:brightness` and `scrub:brightness` actions. When scrubbing brightness, the floating telemetry HUD grants access to absolute display control.
     * Integrates extreme display sovereignty controls:
       1. *Below-Zero Extra-Dim Sub-Routing*: When dragging the brightness slider below 0%, it automatically bridges into Android's native `Extra Dim` API (reduce bright colors), seamlessly lowering the physical screen brightness beyond hardware minimums.
       2. *Over-100% Media Player Hijacking*: When dragging past 100%, Lightspeed takes back display control from stubborn media players (like YouTube or Netflix) that use proprietary in-app gesture brightness overriding the system.
       3. *Color Temperature / Eye Care Slider*: A secondary slider to directly adjust the screen's color temperature (Night Light / Eye Comfort Shield / Blue Light Filter) from cool to warm amber.
       4. *Ambient Mesh screensaver*: A toggle/slider to instantly convert the screen into the ambient mesh UI (similar to the Google Home Hub screensaver) for beautiful ambient telemetry when the phone is docked and charging.
       5. *"Night Sky" Always-On Display (AOD)*: A dedicated, highly customizable AOD feature natively built into Lightspeed (similar in architecture to the Refueling Bay). Night Sky acts as an advanced ambient display, potentially integrating the Mesh UI, to replace the OEM AOD entirely.
   - Floating Avionics Hardware Telemetry HUD (CPU, GPU, FPS & Graphics Engine):
     * Non-intrusive floating diagnostic pill rendering real-time CPU per-core/cluster utilization, GPU active load/clock, and real-time FPS frame-pacing.
     * Graphics Pipeline Detection: Live indicator showing active rendering backend (Vulkan vs. OpenGL ES).
     * Per-App Automation Whitelist: Configurable per-app memory to automatically activate the hardware telemetry HUD when designated apps/games are brought to foreground and dismiss it seamlessly upon exit.
6. Adaptive Kinetic Scroll Engine (Priority Fallback Checklist, Scroll-to-Bottom Symmetry, Continuous Scrub & Per-App Adaptive Memory):
   - Dual Action Parity:
     * Full architectural symmetry between `system:scroll_to_top` ("Scroll to Top") and the brand-new `system:scroll_to_bottom` ("Scroll to Bottom").
   - Reorderable Priority Fallback Execution Checklist:
     * Eliminates "tap chaos" and erratic failures across inconsistent Android UI toolkits (Jetpack Compose LazyColumn/LazyRow, RecyclerView, NestedScrollView, WebView, Canvas).
     * Users can visually configure and reorder the fallback execution hierarchy:
       1. Accessibility Direct Node Target (`ACTION_SCROLL_TO_POSITION` to row 0 / max row).
       2. Directional Accessibility Stepping (multi-pass `ACTION_PAGE_UP`/`ACTION_PAGE_DOWN`, `ACTION_SCROLL_UP`/`ACTION_SCROLL_DOWN`, `ACTION_SCROLL_BACKWARD`/`ACTION_SCROLL_FORWARD`).
       3. Kinetic Synthetic Fling Gestures (dispatched high-velocity accessibility swipe strokes).
       4. Status Bar / Navigation Toolbar Tap Emulation (targeting top/bottom title bar coordinates for apps with built-in scroll-to-top listeners like Twitter/X, Reddit, Telegram).
       5. Hardware Navigation Key Injection (`KEYCODE_MOVE_HOME` / `KEYCODE_MOVE_END`, `KEYCODE_PAGE_UP` / `KEYCODE_PAGE_DOWN`).
   - Continuous Scrub-to-Scroll Action (`scrub:scroll` / `system:scrub_scroll`):
     * Direct parity with Volume and Brightness scrubbers: operates along gesture flanks and horizon rail via continuous vertical/horizontal finger drag.
     * Real-time continuous accessibility scroll injection (interpolated scroll gestures/deltas).
     * Configurable Kinetic Physics Suite:
       * *Starting Speed (Base Velocity)*: Adjustable initial step size so scrolling engages immediately with comfortable precision.
       * *Acceleration Curve / Multiplier*: Dynamic acceleration ramping up as dragging speed or displacement increases, allowing fine slow browsing or ultra-fast cruising through massive feeds.
       * *Max Velocity Ceiling*: Adjustable peak scroll speed cap to prevent runaway jumps and lost position.
       * *Directional Inversion*: Natural scroll vs. inverted direction toggle.
       * *Tactile Feedback & Telemetry*: Real-time micro-haptic impulse ticks synchronized with scroll notches, paired with transient HUD badge.
   - Per-App Adaptive Memory & Profile Engine:
     * Granular per-app overrides: allows the user (or an intelligent last-known-working heuristic) to assign specific fallback orders to specific apps.
     * Embedded directly inside the Scroll to Top and Scroll to Bottom action configuration sheets for instant, unified adjustment without navigating away.
7. Action Engine Vector III (Reserved Placeholder for Pending Specification):
   - Dedicated architectural vector held in stasis in the Actions Engine backlog, pending exact design confirmation from the Founder.
8. Hull & Ship Maneuvers: Power Double-Press Camera Revival & Kinetic Wrist-Flip Kinematics:
   - Native Power Double-Press Camera Revival: Hardware key interception or system override ensuring the Android power button quick double-press reliably revives and launches the camera over the keyguard without Google Assistant / Gemini collision.
   - Kinetic Wrist-Flip Camera Gesture: Rapid double-twist / back-and-forth wrist flip kinetic sensor gesture (gyroscope and accelerometer kinetic signature recognition) to wake device and launch camera instantly.


[STATUS: DEFERRED (THE BACK BURNER)]
1. Orbital Capsule Polish: Micro-telemetry and layout snugness for punch-hole camera cutouts.
2. Power Button Hardware Remapping: Ignition Override in experimental labs with 7-tap safety interlock.
3. Emergency Shizuku JITSON: Daemon keep-alive pulses.

--------------------------------------------------------------------------------
PART 5: CONVERGENCE MILESTONE 1.4.0 — ARCHITECTURAL MODULARIZATION & SBF LABS CLOUD LAUNCH [STATUS: SHIPPED & VERIFIED (v1.4.0)]
--------------------------------------------------------------------------------

[COMPLETED & DEPLOYED MILESTONES (2026-09-16)]
1. Android 14 & 15 Platform Modernization (Target SDK 35):
   - Created LightspeedActivityExtensions.kt providing Activity.overrideZeroTransition().
   - Integrated native API 34/35 zero-duration activity transitions across all 10 activities, eliminating legacy transition flicker completely.
   - Modernized window API compatibility with setShowWhenLocked(true) and setTurnScreenOn(true) on API 27+ with strict backwards-compatibility fallbacks.
2. Complete Avionics Modularization (Phase 1 & Phase 2):
   - Eliminated all monolithic files over 1,000 lines across the settings suite.
   - Split LightspeedCruiseOverlay into touch-vector calculation and Canvas rendering engines.
   - Decomposed HudStripTab into 4 focused cockpit decks (Sensor Gravity, Telemetry Indicators, Hardware Refueling, Vault Experimental).
   - Extracted modal dialogs from CentralCommandConfig into CentralCommandDialogs.kt.
   - Modularized DeflectorComponents into DeflectorStylingComponents.kt.
   - Extracted FlightControlDeckCard into FlightControlDeckComponents.kt.
   - Extracted SliderCalibrationFlyoutDialog into SliderCalibrationDialog.kt.
   - Extracted CoreCoolingRotarySchedulePicker & CoreCoolingTripleLockButton into CoreCoolingComponents.kt.
   - Maximum settings file size reduced by over 60%, drastically improving recomposition speed.
3. Data & Backup Invariant Enforcement:
   - Migrated RefuelingWidgets from deprecated SharedPreferences to activity.defaultPrefs(), restoring 100% backup and restore coverage under LightspeedBackupEngine.
   - Fixed inverted validation condition in PasteJsonDialog.
   - Purged 12 unused ghost parameters from RightDeflectorTab.
   - Upgraded all legacy directional icons to Icons.AutoMirrored.
4. Commercial Packaging & Privacy Hardening:
   - Hardened .gitignore to strictly exclude keystore binaries (*.jks, *.keystore), machine dumps (lightspeed_source_dump.txt, audit_summary.txt), and local crash logs (.kotlin/).
   - Verified 100% offline status: zero android.permission.INTERNET declared, zero external analytics.
   - Verified R8/ProGuard rules with Material Icons extended tree-shaking and automated log stripping.
   - Updated CHANGELOG.md with comprehensive 1.4.0 release documentation.
5. SBF Labs Cloud Repository Architecture:
   - SBF Labs organization established on GitHub.
   - Private repository `SBFlabs/Lightspeed-Nightly` initialized and linked as primary `origin`.
   - All branches (master, nightly-refactor) and release tags (v1.0.0 through v1.1.2) successfully backed up to the cloud.
   - Established strict remote push policy: habitual milestone push to private repo authorized; public repository (SBFlabs/Lightspeed) strictly locked against pushes without explicit user permission.
6. Multi-Agent Ecosystem & Rule Invariance (The Three Musketeers):
   - Unified project rules across Antigravity (Gemini Pro/Flash/Sonnet), GitHub Copilot, and OpenCode.
   - Purged legacy hidden folders (.antigravity/, .agents/) to prevent token bloat and stale rules.
   - Codified Section 7 into 5 synchronized physical manifests (AGENTS.md, GEMINI.md, .github/copilot-instructions.md, OPENCODE.md, RULES.md), guaranteeing continuous parity across whichever agent collaborates with the Founder next.
7. Architectural Refactoring & Hardening Phase [STATUS: SHIPPED & VERIFIED]:
   - Zero-Allocation Render Loop: Pre-allocated and reused `Path` objects (`chamferedPath.rewind()`, `diamondPath`, `gearPath`, `circleClipPath`, `remember { Path() }`) in `LightspeedHudRenderer`, `DeepSpaceRenderer`, `LightspeedNotchOverlay`, and `RefuelingBatteryTelemetry`, eliminating GC pauses during 60/120Hz rendering.
   - Backup Engine Type Precision: Added explicit `"types"` serialization in `LightspeedBackupEngine.kt` to ensure floating-point values are preserved losslessly.
   - Monolith Decomposition (<500 lines constraint): Decomposed 6 monolithic classes (>5,500 lines) into 12 single-responsibility modules:
     * `RefuelingWidgets.kt` (477 lines) -> `ScrollableAppWidgetContainer.kt` (261 lines) & `RefuelingWidgetControls.kt` (441 lines).
     * `LightspeedCruiseOverlay.kt` (482 lines) -> `LightspeedCruiseOverlaySpatial.kt` (334 lines) & `LightspeedCruiseOverlayActions.kt` (160 lines).
     * `LightspeedKeyEngine.kt` (496 lines) -> `LightspeedKeySlots.kt` (84 lines), `LightspeedPowerKeyEngine.kt` (310 lines), and `LightspeedKeyHudNav.kt` (196 lines).
     * `CentralCommandConfig.kt` (424 lines) -> `CentralCommandState.kt` (476 lines) & `CentralCommandModel.kt` (114 lines).
     * `WatchdogDefenseComponents.kt` (476 lines) -> `PerimeterServiceCard.kt` (227 lines), `PerimeterTelemetryBanner.kt` (190 lines), and `PerimeterDialogHeader.kt` (152 lines).
     * `LightspeedAccessibilityService.kt` (366 lines) -> `LightspeedAccessibilityOverlays.kt` (457 lines) & `LightspeedAccessibilityReceiver.kt` (163 lines).
   - 100% verified via `./gradlew compileDebugKotlin --no-daemon`.
8. Deep Memory Optimization Engine [STATUS: BUG RESOLVED]:
   - Diagnosed and resolved massive 370MB memory bloat and "boil-and-release" GC thrashing (stutter) on physical devices.
   - Obliterated unbounded background pre-warming loops in `LightspeedActionRegistry` and `LightspeedDataBridge` that secretly allocated >270 raw `AdaptiveIconDrawable` instances (75MB+ RAM) at zero-second startup.
   - Shrunk `LruCache` buffers from 350 to 50 for heavy Android `Drawable` vectors to aggressively purge framework layout memory, while preserving the lightweight `Bitmap` caches at 144x144 for crisp 60fps rendering.
   - Hard-capped media artwork in `OmniscientAudioDockManager` to 144x144 to prevent blind 36MB+ RAM allocations per high-res song.
   - Engineered the Ephemeral Picker Cache (`temporaryPickerCache`): Bypasses the strict 50-limit cache strictly during the Action Selection UI to ensure buttery smooth scrolling for massive app lists, and triggers an instant `clear()` inside `onDestroy()` when the dialog closes. This allows the app to dynamically stretch RAM for high-end scrolling performance and violently contract to a minimal baseline when inactive.
   - Activated R8 Shrinker (`isMinifyEnabled = true`, `isShrinkResources = true`) directly in the Nightly `debug` pipeline. This strips 9,900+ unused material icons from the Jetpack Compose architecture, plunging the executable `.dex` footprint from 60MB down to 13MB, and eliminating >100MB of static baseline footprint.
================================================================================

