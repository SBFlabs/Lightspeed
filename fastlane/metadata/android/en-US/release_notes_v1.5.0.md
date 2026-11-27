### ✨ Features
- **Sonic & Vista System Decks + 48 kHz Voice Isolation 2.0:** Sonic Deck and Vista Deck graduate into top-level HUD Strip accordions with a unified pull-to-open System Deck Continuum, instant Quick Sonic / Quick Optics toggle strips, and 100% offline 48 kHz stereo Neural Voice Isolation via native C++ DSP (preserving natural high-frequency voice clarity with a smart VAD gate and soft limiter, zero network permissions).
- **Full Arabic Localization & Decoupled RTL Layout:** Added Modern Standard Arabic across the entire app, Guidebook, Bilingual mode, Refueling Bay, Cockpit, widgets, and all 13 themes, paired with an independent **RTL / Left-Handed Layout** toggle that mirrors UI panels for any language while keeping Deflector wings anchored to physical screen edges.
- **Pass-Through Telemetry Micro-HUD & Alpha-Shade HUDs:** Redesigned the per-app Telemetry Micro-HUD for 100% touch pass-through in 4 styles (`Alpha Shade`, `Glass Capsule`, `Modular Pods`, `Micro Gauges`) with dual rocket-thruster drag handles, custom font picker, and live preview, plus 5 frameless **Alpha-Shade Signature HUDs** expanding Scrubber HUDs to 18 styles.
- **Live Try-Before-You-Buy Previews, Direct Call & Contextual Tips:** Preview any locked Flight Control Deck theme, sculpted Cockpit gear shape, or reticle collimator live before unlocking in a streamlined 13-theme showcase, dial contacts in one gesture with the new **Direct Call** system action, and browse rotating window-aware tips with 0ms double-tap Tab Blueprint access.

### 🐛 Bug Fixes
- **1:1 Native Hardware Eye Care Sync & Smart Pixels Grids:** Removed the legacy software yellow tint overlay in favor of pure OS hardware Eye Care (`Night Light` / `Eye Comfort Shield`) with linear intensity sync, decoupled Eye Care from Extra Dim and brightness auto-reset so scrubbing never drops warmth, and wired `1x1`, `2x2`, and `4x4` Smart Pixels block sizes.
- **Side Deflector Touch Integrity & Horizon Rail Stability:** Eliminated side Deflector gesture drops (`ACTION_CANCEL` on swipe-and-hold, two-step, and scrub triggers), stopped Horizon Rail / micro-ticker blinking on Deflector touches, and restricted Continuum deck pulls to inward directional swipes.
- **Zero-Stutter Tabs, Masked Theme Captures & Solo Collapse:** Eliminated single-frame Central Command tab-switch stutter, prevented Lightspeed from capturing its own UI when switching screenshot-backed glass themes, and enforced Anchored Solo / Solo collapse rules across nested accordions and scrollable Blueprint screens.
- **Lossless Ring-Set Backup Restore & 45s Key Lockout Cap:** Removed the 2,000-character restore limit that truncated customized Cockpit ring sets, preserved pinned apps and Space Pod icons across Config Vault imports, and capped offline Infinity key retry lockouts at 45 seconds max with automatic reset on app updates.

### 💙 Love Lightspeed? Help It Grow
[![Buy Me A Coffee](https://img.shields.io/badge/Buy_Me_A_Coffee-Lightspeed_∞-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/sbflabs/e/579114)

- **Show it off**: On Android subreddits or [XDA Forums](https://xdaforums.com/t/4804419/), if you find users talking about third-party launchers breaking gestures, or looking for a full-fledged gesture app or launcher-agnostic meta-launcher & workstation, show Lightspeed off! A screenshot, quick clip, or recommendation goes a long way — or simply direct them to [r/SBFlabs](https://www.reddit.com/r/SBFlabs/) and [YouTube (@SBFlabs)](https://www.youtube.com/@SBFlabs) for video showcases and screenshots.
- **Star the repo**: A star on [GitHub](https://github.com/SBFlabs/Lightspeed) helps more users discover Lightspeed.
- **Buy me a coffee**: [buymeacoffee.com/sbflabs](https://buymeacoffee.com/sbflabs/e/579114) — $1.99 one-time Founder's License (globally friendly pricing · Google Pay supported) unlocks unlimited flight decks and all signature themes, helps keep 100% offline development thriving, and includes a free Google Play code if/when a Play Store version launches. The $1.99 Founder's Rate runs through v1.5.x before stepping up in v1.6.0 with Trackpad Automation, and early Founders keep lifetime access forever.

---
📖 **Full changelog:** [CHANGELOG.md](https://github.com/SBFlabs/Lightspeed/blob/master/CHANGELOG.md)  
🛠️ **Code Refactoring:** Unified Sonic and Vista Deck window hosting, routed dual-flank Deflector and Trackpad edge triggers through a shared edge arbiter, moved STFT and polyphase resampling into native 16 KB-aligned C++ DSP, extracted icon browser and ghost-tip canvas modules under file-size ceilings, pre-downscaled fallback refraction backdrops 3×, and hardened audio session, JNI, and overlay teardown lifecycles.

🛡️ **VirusTotal Verification (0 Detections):**
- `app-arm64-v8a-release.apk`: `0/67` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/4c07a072375254aa5f096566660fe671faa513b6a70379292847df58754a9f5d)
- `app-armeabi-v7a-release.apk`: `0/64` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/6f5feb574b1be2ddc4936cd8d98263977bb468bdfc63b0d269a1cefc17e75b06)
- `app-universal-release.apk`: `0/67` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/b72cb79e83100ce1e3a8ca3fa5821e398cca3ae38054c8ae08574d66327ecfe9)

🔑 **SHA-256 Checksum:**
- `app-arm64-v8a-release.apk`: `4c07a072375254aa5f096566660fe671faa513b6a70379292847df58754a9f5d`
- `app-armeabi-v7a-release.apk`: `6f5feb574b1be2ddc4936cd8d98263977bb468bdfc63b0d269a1cefc17e75b06`
- `app-universal-release.apk`: `b72cb79e83100ce1e3a8ca3fa5821e398cca3ae38054c8ae08574d66327ecfe9`
