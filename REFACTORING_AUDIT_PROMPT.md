# ==============================================================================
# LIGHTSPEED: CANONICAL REFACTORING & PRE-RELEASE AUDIT PROMPT
# ==============================================================================
# Role: Principal Android Release Engineer & App Store Compliance Auditor
# Recommended Model: High-Tier Reasoning (Gemini Pro or Claude Sonnet)
# Execution Model: Gemini Flash (for surgical step-by-step implementation)
# ==============================================================================

Act as a Principal Android Release Engineer and App Store Compliance Auditor.

Perform a strictly READ-ONLY pre-release audit of this codebase. DO NOT edit, delete, or refactor any files right now.

Inspect the entire repository against commercial release standards across these 5 domains:

1. Monolithic Files & Structural Fragility:
   - Identify every file exceeding 400 lines of code.
   - Flag any class that merges UI layout, database/file I/O, and logic into one place.
   - Highlight the top 3 files at highest risk of breaking if modified.

2. True Offline Architecture & Leak Prevention:
   - Verify AndroidManifest.xml and all source files to guarantee complete offline operation (no INTERNET permission, analytics, telemetry, or remote SDKs).
   - Inspect local storage to ensure any user data is securely stored and cannot be leaked to other apps.

3. Commercial Licensing & Copyright Exposure:
   - Audit every dependency in all build.gradle files.
   - List their software licenses (MIT, Apache 2.0, GPL, AGPL, LGPL, etc.).
   - Explicitly flag any copyleft license (such as GPL/AGPL) that creates legal issues or forces source disclosures when selling/monetizing the compiled app.

4. Startup Velocity, RAM & ROM Footprint:
   - Identify any operations in Application.onCreate() or MainActivity that block the main thread with disk/database reads during cold launch.
   - Scan for static Activity references, unclosed cursors, or uncancelled coroutines that will cause memory leaks or crashes over time.
   - Check build.gradle (release block) to verify if minifyEnabled (R8/ProGuard) and shrinkResources are properly configured to minimize APK size.

5. Dead Code & Release Hygiene:
   - Flag completely unused classes, orphaned layouts, or abandoned dependencies bloating the project.

OUTPUT RULES:
- Provide ONLY the prioritized audit report.
- Group issues strictly by severity: [CRITICAL FOR RELEASE], [WARNING], [OPTIMIZATION].
- For each item, state:
  * File path and line number
  * The exact issue in one concise sentence
- End with a numbered list of all findings. Do not begin fixing anything until I explicitly tell you which number to address.
