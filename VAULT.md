================================================================================
LIGHTSPEED MASTER AVIONICS VAULT: COMPLETE ACCUMULATED BACKLOG & DREAMS ARCHIVE
================================================================================

--------------------------------------------------------------------------------
PART 1: THE BACK BURNER (ESTABLISHED DEFERRED ARCHITECTURE)
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
** Add systemUI tuner setting I use plus the ones from that Foss app for pixels and animation speed
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
** Scroll to bottom & scrub to scroll
** Power menu
* Rotation mode scrubber & actions

[ANT / BUGS REGISTER]
* Calendar icon (dynamic date sync)
* Screen timeout HUD custom choice/action
* Scroll action fallback fixes (tap chaos)
* Freeform windows OEM vs stock android
* Progressive blur turning whitish
** Rotating back to portrait after closing Brawl Stars messes things up? pressing Home fixes it

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
** HUD for vol & brightness (brightness To always take control from Video playing brightness hoarding... also swiping Down on brightness triggers extra dimming acc serv and up To exit? OR use Edge Seek?

[SUPERAPP STRATEGY & MODULARITY]
❕ Is this considered a superapp? Are super apps suitable for a hobby project utilizing ai agent and a medical student as a manager?
- Verdict: OS-level Meta-Shell / Cockpit. Single monolithic APK with strict internal domain separation (:engine:gestures, :engine:overlay, :launcher:cruise). Avoid external multi-APK plugin fragmentation.

[LINEAGE & DNA REGISTER (FEATURES INSPIRED BY / PORTED FROM)]
+ Recents, Be Nice, Smart Edge, LaunchTime
+ Animator speed, SystemUI Tuner
+ Atmo Engine (overlay instead of live wallpaper option), LifeDots
+ Edge Gestures, UbikiTouch, Action Notch, Samsung's One Hand Operation+, JINA Drawer & Sidebar
================================================================================
