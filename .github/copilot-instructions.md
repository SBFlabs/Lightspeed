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

# 3. Autonomous Execution Loop & Host Safety Invariants
Whenever any code edit, bug fix, or feature is completed:
1. **Hardware & Process Hygiene (Strict 8GB RAM / 4th-Gen i7 Safeguards)**:
   * **Post-Mortem Lessons**: Two severe process runaway incidents occurred due to: (a) using the agent `schedule` timer tool during builds, which registered 10+ stacked background tasks, (b) the Kotlin compiler daemon (`KotlinCompileDaemon`) running detached with a 2-hour idle timeout and 2.5GB RAM, and (c) orphaned native `aapt2` workers locking build pipelines. These caused 100% CPU/RAM starvation and crashed host services (SSH and Jellyfin).
   * **Daemon & Worker Invariance**: Always enforce `org.gradle.daemon=false`, `org.gradle.parallel=false`, `org.gradle.workers.max=2`, `org.gradle.jvmargs=-Xmx2560m`, and `kotlin.compiler.execution.strategy=in-process` in `gradle.properties`.
   * **Zero Background Process / Timer Stacking**: NEVER launch multiple Gradle commands, background tasks, or `schedule` asynchronous timers concurrently. The `schedule` tool is strictly forbidden when waiting for builds. Always run `./deploy-nightly.sh` strictly sequentially in the foreground and verify completion before initiating any subsequent tool calls to prevent CPU starvation (>90%) and memory exhaustion that drops host services (SSH/Jellyfin).
   * **Process Awareness & Instant Flush**: Never assume prior commands are finished without checking task state. Always execute `killall -9 java aapt2 2>/dev/null || true` immediately after every compilation to purge any transient compiler memory or native AAPT daemons.
2. **Compile & Deploy**:
   * Run `./deploy-nightly.sh` synchronously.
3. **Local Git Commit**:
   * Auto-committed by `deploy-nightly.sh` with timestamp.
4. **Wireless ADB Silent Deploy**:
   * Executed by `deploy-nightly.sh` (`adb -s 192.168.100.10:5555 install -r -d app/build/outputs/apk/debug/app-debug.apk`).
   * **STRICT SILENT DEPLOY**: NEVER run `am start` to launch activities on device upon installation.
5. **Physical Device Verification**:
   * Always run `adb -s 192.168.100.10:5555 shell "dumpsys package com.sbf.lightspeed.nightly | grep -E 'versionCode|lastUpdateTime'"` to verify that the phone physically updated.
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
# ==============================================================================
# PROJECT MANIFEST & CODING RULES: LIGHTSPEED
# ==============================================================================

# 1. Core Philosophy & Architecture
- App Type: Ultra-fast offline gesture launcher & cruise overlay workspace for Android.
- Privacy & Network: 100% Offline. Zero telemetry, zero analytics, zero external network dependencies.
- Language & Framework: Pure Kotlin, Jetpack Compose with Material 3 / Dynamic Colors, AGSL shaders, custom high-performance Canvas rendering.
- Operating Mode: Autonomous Lead Developer (User acts as Visionary Founder).

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

# 3. Build & Deployment Execution Scope (Host Protection & User Overrides)
- **BUILD & DEPLOY EXECUTION POLICY**:
  * **Default Mode**: The AI refrains from running background Gradle daemons, build loops, or unsolicited compilations.
  * **Explicit User Pass / Override**: Whenever the user explicitly instructs or gives a pass (e.g. "run it", "deploy it", "giving you a pass", etc.), the AI is fully authorized to execute `./deploy-nightly.sh` or target debug builds.
- **AI Core Responsibilities**:
  * Pure Kotlin / Jetpack Compose code creation, modularization, and refactoring.
  * Architectural design, data model expansion, and logic implementations.
  * Git staging and concise local commit messages on branch `nightly-refactor`.
- **Compilation & Deployment**:
  * The user can run `./deploy-nightly.sh` directly or explicitly command the AI to execute it when needed.

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
# ==============================================================================
# PROJECT MANIFEST & CODING RULES: LIGHTSPEED
# ==============================================================================

# 1. Core Philosophy & Architecture
- App Type: Ultra-fast offline gesture launcher & cruise overlay workspace for Android.
- Privacy & Network: 100% Offline. Zero telemetry, zero analytics, zero external network dependencies.
- Language & Framework: Pure Kotlin, Jetpack Compose with Material 3 / Dynamic Colors, AGSL shaders, custom high-performance Canvas rendering.
- Operating Mode: Autonomous Lead Developer (User acts as Visionary Founder).

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

# 3. Build & Deployment Execution Scope (Host Protection & User Overrides)
- **BUILD & DEPLOY EXECUTION POLICY**:
  * **Default Mode**: The AI refrains from running background Gradle daemons, build loops, or unsolicited compilations.
  * **Explicit User Pass / Override**: Whenever the user explicitly instructs or gives a pass (e.g. "run it", "deploy it", "giving you a pass", etc.), the AI is fully authorized to execute `./deploy-nightly.sh` or target debug builds.
- **AI Core Responsibilities**:
  * Pure Kotlin / Jetpack Compose code creation, modularization, and refactoring.
  * Architectural design, data model expansion, and logic implementations.
  * Git staging and concise local commit messages on branch `nightly-refactor`.
- **Compilation & Deployment**:
  * The user can run `./deploy-nightly.sh` directly or explicitly command the AI to execute it when needed.

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
