# ==============================================================================
# PROJECT MANIFEST & CODING RULES: LIGHTSPEED
# ==============================================================================

# 1. Core Philosophy & Architecture
- App Type: Ultra-fast offline gesture launcher & cruise overlay workspace for Android.
- Privacy & Network: 100% Offline. Zero telemetry, zero analytics, zero external network dependencies.
- Language & Framework: Pure Kotlin, Jetpack Compose with Material 3 / Dynamic Colors, AGSL shaders, custom high-performance Canvas rendering.
- Operating Mode: Autonomous Lead Developer (User acts as Visionary PM / Product Owner).

---

# 2. Release Strategy & Strict Immutability
- **Nightly Target (Active Development & Iteration)**:
  * Application ID: `com.sbf.lightspeed.nightly` (App Label: "Lightspeed Nightly")
  * ALL day-to-day development, refactoring, feature work, bug fixes, testing, builds (`assembleDebug`), and ADB installs MUST target `com.sbf.lightspeed.nightly` ONLY.
  * Git Commits: Local commits on branch `nightly-refactor`. Never push to remote without instruction.
- **Stable Release (STRICT IMMUTABILITY RULE)**:
  * Application ID: `com.sbf.lightspeed` (App Label: "Lightspeed")
  * **LOCKED**: NEVER build (`assembleRelease`), install, modify, or promote to `com.sbf.lightspeed` UNLESS the user explicitly gives a direct command (e.g., "Promote nightly to stable release").

---

# 3. Autonomous Execution Loop (Mandatory After Every Task)
Whenever any code edit, bug fix, or feature is completed:
1. **Hardware & Process Hygiene (Strict 8GB RAM / 4th-Gen i7 Safeguards)**:
   * **Daemon & Worker Invariance**: Always enforce `org.gradle.daemon=false`, `org.gradle.parallel=false`, `org.gradle.workers.max=2`, `org.gradle.jvmargs=-Xmx2560m`, and `kotlin.compiler.execution.strategy=in-process` in `gradle.properties`.
   * **Zero Background Process Stacking**: NEVER launch multiple Gradle commands, background tasks, or `schedule` asynchronous timers concurrently. Always run builds strictly sequentially in the foreground and verify completion before initiating any subsequent tool calls to prevent CPU starvation (>90%) and memory exhaustion that drops host services (SSH/Jellyfin).
   * **Process Awareness & Instant Flush**: Never assume prior commands are finished without checking task state. Always execute `killall -9 java 2>/dev/null || true` immediately after every compilation to purge any transient compiler memory.
2. **Compile & Deploy**:
   * Run `./deploy-nightly.sh` synchronously.
3. **Local Git Commit**:
   * Run `git add -A && git commit -m "<concise descriptive message>"`.
4. **Wireless ADB Silent Deploy**:
   * Run `adb -s 192.168.100.10:5555 install -r app/build/outputs/apk/debug/app-debug.apk`.
   * **STRICT SILENT DEPLOY**: NEVER run `am start` to launch activities on device upon installation.
5. **Physical Device Verification**:
   * Always run `adb -s 192.168.100.10:5555 shell "dumpsys package com.sbf.lightspeed.nightly | grep lastUpdateTime"` to verify that the phone physically updated.
6. **Report to User**:
   * State the commit hash, verified `lastUpdateTime` from device, and concise summary of changes.

---

# 4. Backup & Restore Engine Invariance
- **Full Coverage Requirement**:
  * Every new preference, setting, gear set order, gear name, custom display name, physics profile, and reticle crosshair style MUST be exported to and imported from `LightspeedBackupEngine`.
- **Custom Icon Portability**:
  * Custom item icons (whether from gallery photos or third-party icon packs) must be embedded directly into the backup JSON payload as Base64 strings under `"shortcut_icons"` so backups remain completely self-contained across device migrations.
- **Instant Cache Flush & Hot Reload**:
  * Backup import MUST flush `LightspeedShortcutManager` and `LightspeedIconManager` memory caches and invoke `reloadPreferences()` so the live overlay updates immediately without app restarts.

---

# 5. UI / Overlay Architecture Guidelines
- **Overlay Window Hierarchy & Z-Ordering**:
  * Because `LightspeedCruiseOverlay` runs in `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`, any regular Android Activity or Compose dialog sits *behind* the overlay canvas.
  * When opening an edit dialog or configuration window from the overlay, `dismissOverlay()` MUST be called first, and `onDestroy()` in the dialog must invoke `reopenCockpitHangar(setIndex)` to return the user seamlessly.
- **Gesture Separation**:
  * **Short Tap (< 400ms)**: Executes immediate primary action (e.g. Launching, selecting, or arming Eject Mode with red ✕ and telemetry alert).
  * **Long Press (>= 400ms)**: Smoothly opens customization/editing without showing premature warning states, red crosses, or alarm shakes during the hold.
- **Visual Design**:
  * Follow Material 3 Expressive and dynamic dark color palettes (`dynamicDarkColorScheme`).
  * Collimators, reticles, telemetry badges, and laser guides must remain crisp, tactical, and responsive.
