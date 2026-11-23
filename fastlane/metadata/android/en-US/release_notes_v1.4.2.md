### ✨ Features
- **Real Network Usage Analytics & Culprit Hunter `NETWORK` Tab:** Upgraded System Vitals Cellular and Wi-Fi rows to hardware-backed `NetworkStatsManager` queries (`1H`, `3H`, `Today`, `3D`, `7D`, `30D`) using existing Usage Access with zero internet permissions, added a 5th `NETWORK` tab in Culprit Hunter with transport filters, reverse sort, and quick-jump rail, and auto-hidden Cellular controls on Wi-Fi-only devices.
- **Multi-Touch Category Cruise:** Use a second finger to **tap** hosted Category Cruise widgets or grid apps directly without pinning first, powered by split-touch event routing and stationary-finger jitter suppression.
- **Native Android Settings Shortcuts & HUD Deep-Links:** Added an **Android Native** subcategory under System Actions (`Wi-Fi Settings`, `Bluetooth Settings`, `Battery Usage`, `Digital Wellbeing`) with OEM fallback chains, plus 1-tap contextual deep-links on the Battery Usage HUD, Screen Time HUD, and Astrogation Airlock settings.
- **Subtle Rotating Tips, `All Tips` Card & Scroll Gestures:** Added subtle rotating localized tips across Category Cruise (including pinned App & Widget grids), Cockpit Hangar, Live Gear Sets, and Cybernetic Trackpad, a scrollable `All Tips` overlay card, persistent multi-span (`LAST CHG`, `TODAY`, `7-DAY`) Shizuku battery drain storage with a `SPAN` pill, and accelerating fling scroll gestures (including Chromium browsers).
- **ARM-Only Universal Release APK:** Added a signed ARM-only `universal` APK (`arm64-v8a` + `armeabi-v7a`, with emulator `x86`/`x86_64` libraries stripped) alongside the dedicated per-ABI splits.

### 🐛 Bug Fixes
- **Cybernetic Trackpad & Macro Editor:** Suspended the trackpad overlay while the Macro Editor is open, fixed backdrop blur on the Macro Editor window itself, added full clear wipe and per-app default scope, and fixed freeform tap/swipe macro dispatch.
- **Category Cruise Launch Effects, Edge Feathering & Landscape Gears:** Fixed launch-effect animation lockups, moved focused Category Cruise tiles inside the clipped scroll layer with progressive edge feathering so they fade smoothly at scroll edges, and centered Landscape Live Gear Sets cleanly.
- **Contrast, Extra Dim Scrubbing, Airlock & Vitals Reliability:** Fixed white-on-white switches on Glass Lava, fixed brightness scrubbing while Extra Dim is active (easing out Extra Dim from any level without snapping brightness to 1%, plus clearing Extra Dim on Auto-Brightness reset), prewarmed and preserved cached Shizuku battery drain data across brief hiccups so System Vitals `APPS` stops flashing empty, re-enabled ~60fps Airlock countdown digit animation with 3-Way Color Engine theming, and prevented 7d/30d supporter reminders from stacking after What's New or Welcome Aboard.
- **Action Selection Menu, RTL & Build Verification:** Made the Action Selection Menu alphabet fast-scroll rail adapt to compact/landscape heights without clipping and include non-Latin app initials, fixed RTL layout alignment inside floating language/vocabulary scrub popups, recognized additional installers (`Obtainium`/`ObtainX`, `Orion Store`, `ShizuStore`, and others), and replaced alarming sideload wording with calm `"Direct / Sideload Install"` labels across `EN`, `PT-BR`, and `ES`.

### 💙 Love Lightspeed? Help It Grow
[![Buy Me A Coffee](https://img.shields.io/badge/Buy_Me_A_Coffee-Lightspeed_∞-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/sbflabs/e/579114)

- **Show it off**: On Android subreddits or [XDA Forums](https://xdaforums.com/t/4804419/), if you find users talking about third-party launchers breaking gestures, or looking for a full-fledged gesture app or launcher-agnostic meta-launcher & workstation, show Lightspeed off! A screenshot, quick clip, or recommendation goes a long way — or simply direct them to [r/SBFlabs](https://www.reddit.com/r/SBFlabs/) and [YouTube (@SBFlabs)](https://www.youtube.com/@SBFlabs) for video showcases and screenshots.
- **Star the repo**: A star on [GitHub](https://github.com/SBFlabs/Lightspeed) helps more users discover Lightspeed.
- **Buy me a coffee**: [buymeacoffee.com/sbflabs](https://buymeacoffee.com/sbflabs/e/579114) — $1.99 one-time Founder's License (globally friendly pricing · Google Pay supported) unlocks unlimited flight decks and all signature themes, helps keep 100% offline development thriving, and includes a free Google Play code if/when a Play Store version launches.

---
📖 **Full changelog:** [CHANGELOG.md](https://github.com/SBFlabs/Lightspeed/blob/master/CHANGELOG.md)  
🛠️ **Code Refactoring:** Split countdown modal touch handling into a dedicated module, removed remaining per-frame HUD and overlay canvas allocations, hardened overlay and service teardown cleanup, and migrated raw preference keys to typed constants.

🛡️ **VirusTotal Verification (0 Detections):**
- `app-arm64-v8a-release.apk`: `0/67` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/dbb0db46f03adf7eff9474b8aa400d1073b502041e1013418c941543ada7d25e)
- `app-armeabi-v7a-release.apk`: `0/67` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/cf00547bbf353953ef7d1ee6ee637f2b8b597ce15b82b6fb8c188f1d961653ee)
- `app-universal-release.apk`: `0/67` detections (`malicious=0, suspicious=0`) — [Report](https://www.virustotal.com/gui/file/c5576c8a51966263850741ea4e2f931e3c2009050d95d7cc0a7180f668865dda)

🔑 **SHA-256 Checksum:**
- `app-arm64-v8a-release.apk`: `dbb0db46f03adf7eff9474b8aa400d1073b502041e1013418c941543ada7d25e`
- `app-armeabi-v7a-release.apk`: `cf00547bbf353953ef7d1ee6ee637f2b8b597ce15b82b6fb8c188f1d961653ee`
- `app-universal-release.apk`: `c5576c8a51966263850741ea4e2f931e3c2009050d95d7cc0a7180f668865dda`
