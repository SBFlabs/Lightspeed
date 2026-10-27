### 🕹️ Cybernetic Trackpad
- **Floating Cursor Navigation**: Precision floating cursor navigation with sub-pixel kinetic physics, customizable acceleration curves, dynamic haptic resistance, and gesture boundary awareness.
- **Robot Arm Buttons & Bot Macros**: Configurable trackpad buttons acting as automated "robot arms" capable of recording, editing, and executing multi-step macros, automated screen touches, and condition-based UI actions.

### 🎛️ Sonic Deck
- **Per-App Volume Mixer**: Floating audio mixer allowing independent volume control across active media players.
- **Offline Post Background Muffle**: Real-time neural audio processing for voice isolation and background noise attenuation.
- **Privileged Sonic Service Lifecycle**: Sonic Deck runs an isolated privileged service (`sonic_service` via Shizuku) taking ~100–150 MB RAM while enabled. When toggled off, the background daemon is immediately shut down and unbound to completely free system memory.

### ☀️ Vista Deck & Video Override
- **Unified Display Optics**: Integrated brightness and display wellness control panel with configurable auto-reset triggers.
- **Video Override**: Automatic transparent overlay engagement above 100% (boost zone) and below 1% (Extra Dim floor) to override stubborn media players, leaving native Android display controls 100% untouched throughout the 1%–100% standard range.
- **Astrogation Airlock**: The 5-second mindful pause before opening an app now shows both your **App Time** and your **Sector Time** (total time spent today across every app in that app's category), so you can see the bigger picture before you dive in.

### 📳 Tactile & Haptics Engine
- **Per-Domain Haptic Tuning**: Every Lightspeed surface now has its own haptic domain — System baseline, Central Command UI taps, Deflector edge triggers, Continuous scrub, Cockpit Hangar rotary spinning, Gear set jumps, Category Cruise cylinder scroll, Spatial layer transitions, and Cursor/app focus — each independently configurable with precision snap-stop sliders.
- **Fine-Grained Slider Control**: The new haptic domain slider lets you sweep through preset profiles (Off → Soft → Crisp → Heavy) with horizontal snap stops, and fine-tune vertically to land on any intensity between them.
- **Cockpit & Category Cruise Sub-Accordions**: Dedicated tactile accordions inside Cockpit Hangar and Category Cruise group all feedback controls together so tuning is never buried.
- **ERM Tablet Fix**: Haptic engine now correctly compensates for tablets with ERM (eccentric rotation mass) actuators, applying extended pulse durations to overcome motor inertia and produce reliable feedback instead of silent misfires.
- **Throttle Cooldown Control**: A global haptic cooldown slider lets you set the minimum interval between back-to-back pulses, preventing motor saturation during rapid gestures.

### 🌌 Category Cruise
- **Updated HUD Tab Options**: Added new options in the HUD tab inside the Category Cruise edit menu.

### 🛰️ Cockpit Hangar
- **Overhauled Layout**: Rearranged cockpit avionics layout, now tablet and horizontal/landscape screen friendly.

### 🛡️ Master Stealth Cloak & Quick Settings Matrix
- **1-Tap Emergency Cutoff**: One tap on the tile dismantles everything Lightspeed has running: it closes all floating overlays (Category Cruise, Trackpad, Space Pods, Airlock, Sonic Deck, Vista Deck), clears Video Override, Extra Dim, Eye Care and Smart Pixels, releases any forced screen orientation, restores Android's native edge back gesture, resets per-app volumes and shuts down the Sonic service, then switches off Lightspeed's accessibility service and pauses its watchdog. Banking and DRM apps see a completely stock device. Tap again to re-arm everything instantly.
- **Long-Press Stealth Cloak Matrix**: Long-pressing the tile opens the Cloak Matrix to select other installed accessibility services (password managers, automation tools) to switch off together with Lightspeed, and bring them back when you re-arm, so sensitive apps launch without interference.
- **Single QS Tile**: Quick Settings now offers just one tile, the Master Cloak (tap to cloak/re-arm with haptics and a toast, long-press for the Cloak Matrix). Redundant secondary tiles were removed.

### 📦 Split Downloads
- **Per-Architecture APKs**: Separate `arm64-v8a` and `armeabi-v7a` builds instead of one universal APK, so you only download the code your phone can run.

Lightspeed is built by one person, runs 100% offline, and has no ads, no telemetry and no trackers. If it makes your phone nicer to use, here are a few easy ways to help:
- **Show it off**: Post a screenshot or a short clip of your setup on Reddit, XDA, YouTube or in a group chat. One-thumb gestures look best in motion, and a single share can bring in someone who needed this.
- **Tell one friend**: Know someone who loves tinkering with their Android? Send them the link.
- **Star the repo**: A star on [GitHub](https://github.com/SBFlabs/Lightspeed) helps others discover the app.
- **Tell me what you think**: Bugs, typos, ideas and wishes are all welcome. You can reach the issue tracker straight from Central Command.
- **Buy me a coffee**: [buymeacoffee.com/sbflabs](https://buymeacoffee.com/sbflabs/e/579114) unlocks unlimited flight decks and all optical themes, and keeps development going.

---
📖 **Full changelog:** [CHANGELOG.md](https://github.com/SBFlabs/Lightspeed/blob/master/CHANGELOG.md)  
🛠️ **Code Refactoring:** Consolidated Quick Settings into one service, unified left/right deflector scrub tracking, simplified the video-override display logic, and split build outputs per CPU architecture.  
🔑 **SHA-256 Checksums:**  
- **`Lightspeed-v1.3.0-arm64-v8a.apk`**:  
  `7e343babdf8512ea19ed5e42661982b518eae8e92e1ae7c2913870deadac86f4`  
- **`Lightspeed-v1.3.0-armeabi-v7a.apk`**:  
  `689a7239ea70f9a41e903606a73bde7a7fc4cd24355d0a98dddddcc040025660`  
