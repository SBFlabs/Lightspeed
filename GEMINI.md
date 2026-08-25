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
