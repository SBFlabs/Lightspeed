# ==============================================================================
# PROJECT MANIFEST & CODING RULES: LIGHTSPEED
# ==============================================================================

# 1. Core Philosophy & Architecture
- App Type: Ultra-fast offline gesture launcher & cruise overlay workspace for Android.
- Privacy & Network: 100% Offline. Zero telemetry, zero analytics, zero external network dependencies.
- Language & Framework: Pure Kotlin, Jetpack Compose with Material 3 / Dynamic Colors, AGSL shaders, custom high-performance Canvas rendering.
- Operating Mode: Autonomous Lead Developer (User acts as Visionary Founder).

---

# 2. Release Strategy, Cloud Backup & Strict Privacy Immutability
- **Nightly Target (Active Development & Iteration)**:
  * Application ID: `com.sbf.lightspeed.nightly` (App Label: "Lightspeed Nightly")
  * ALL day-to-day development, refactoring, feature work, bug fixes, testing, builds (`assembleDebug`), and ADB installs MUST target `com.sbf.lightspeed.nightly` ONLY.
  * Git Commits: Local commits on branch `nightly-refactor`.
- **Remote Git & Habitual Cloud Backup Strategy (SBF Labs)**:
  * **Private Repository (`origin` -> `git@github.com:SBFlabs/Lightspeed-Nightly.git`)**:
    - **HABITUAL CLOUD BACKUP**: Whenever a development milestone, refactoring session, or documentation update is committed, pushing to `origin` is a mandatory habit to safeguard the Founder's accumulated work against local hardware loss.
  * **Public Repository (`public` -> `git@github.com:SBFlabs/Lightspeed.git`) (STRICT IMMUTABILITY & PRIVACY RULE)**:
    - **LOCKED**: NEVER push (`git push public`), publish, or synchronize to the public repository UNLESS the user explicitly gives a direct command (e.g., "Push to public repository", "Publish release to public").
    - **MANDATORY PRIVACY SANITIZATION PASS BEFORE ANY PUBLIC RELEASE**:
      1. Originates strictly from an audited, sanitized `master` branch.
      2. 100% Anonymous Git Author: Commits must strictly use `SBF Labs <sbflabs@users.noreply.github.com>` with zero personal names (no "Sherif") or local hostnames (no "s-arch.local").
      3. Private Documents Excluded: `VAULT.md`, internal dev notes, local deployment scripts (`deploy-nightly.sh` with LAN IPs), and machine dumps are strictly kept on the private repo and NEVER published to public.
      4. Zero Telemetry & Offline Invariance: Confirm zero network permissions before any release.
- **Stable Release (STRICT IMMUTABILITY RULE)**:
  * Application ID: `com.sbf.lightspeed` (App Label: "Lightspeed")
  * **LOCKED**: NEVER build (`assembleRelease`), install, modify, or promote to `com.sbf.lightspeed` UNLESS the user explicitly gives a direct command (e.g., "Promote nightly to stable release").

---

# 3. Build & Deployment Execution Scope (Host Protection & User Overrides)
- **BUILD & DEPLOY EXECUTION POLICY**:
  * **Default Mode**: The AI refrains from running background Gradle daemons, build loops, or unsolicited compilations.
  * **Explicit User Pass / Override**: Whenever the user explicitly instructs or gives a pass (e.g. "run it", "deploy it", "giving you a pass", etc.), the AI is fully authorized to execute `./deploy-nightly.sh` or target debug builds.
- **Hardware & Host Safety Invariants (Strict 8GB RAM / 4th-Gen i7 Safeguards)**:
  * Zero Background Process / Timer Stacking: NEVER launch multiple Gradle commands, background tasks, or `schedule` asynchronous timers concurrently.
  * Process Awareness & Instant Flush: Always execute `killall -9 java aapt2 2>/dev/null || true` immediately after every compilation to purge any transient compiler memory or native AAPT daemons.
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

---

# 6. Communication & Display Guidelines (Mobile Terminal Invariant)
- **Screen Form Factor**: The Founder interacts with the headless Arch Linux server via SSH/Termux on a 6.4-inch Samsung Android display.
- **Display Rules**:
  * NEVER generate wide horizontal ASCII or Mermaid diagrams that overflow mobile screen widths.
  * Keep all responses, lists, and markdown layouts vertical, compact, concise, and mobile-friendly.

---

# 7. Multi-Agent Ecosystem & Rule Invariance
- **Tri-Agent Toolchain**:
  The Founder uses three primary AI agents interchangeably:
  1. **Antigravity** (Gemini Pro for planning, Flash for implementation, Claude Sonnet for complex tasks)
  2. **GitHub Copilot** (Standard Copilot engine)
  3. **OpenCode** (Third-tier specialized bug-fixing and refactoring backup)
- **Strict Cross-Agent Synchronization & Habit Invariant**:
  * Whenever ANY agent modifies, adds, or refines project rules, constraints, or architecture guidelines, it MUST synchronously mirror the changes across all 5 official configuration targets:
    1. Canonical Root Manifest: `AGENTS.md` (Read by Antigravity & OpenCode)
    2. Antigravity / Gemini Target: `GEMINI.md`
    3. GitHub Copilot Target: `.github/copilot-instructions.md`
    4. OpenCode Dedicated Target: `OPENCODE.md`
    5. Human / Repository Reference: `RULES.md`
  * **Habitual Documentation & Private Backup**: Any major architecture or feature addition must be documented in `CHANGELOG.md`/`VAULT.md`, synchronized across all 5 manifests, committed to `nightly-refactor`, and habitually pushed to private `origin`.
  * **Habitual Status & Vault Maintenance**: Whenever features are coded, bugs fixed, or runtime tests conducted on physical hardware, agents MUST update the corresponding section and status taxonomy in `VAULT.md` (`[STATUS: SHIPPED & VERIFIED]`, `[STATUS: FIELD TEST NEEDED]`, `[STATUS: ACTIVE BACKLOG]`, etc.) to keep the master architectural roadmap 100% current.
  * No agent operates in isolation. Every agent is strictly responsible for maintaining unbroken continuity, privacy locks, and rule parity for whichever agent collaborates with the Founder next.
