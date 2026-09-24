# 🚀 Lightspeed

**Lightspeed** is an ultra-fast, 100% offline gesture launcher and cruise overlay workspace for Android with a tactical, space-flight-inspired interface.

Instead of cluttered app grids and static home screens, Lightspeed replaces your daily interactions with a fluid, kinetic gesture surface designed for high-speed single-handed navigation.

---

## 📦 Inaugural Public Release: v1.0.0

This is the official public release debut of **Lightspeed (v1.0.0)**.

- **Download APK**: Grab the production release APK from the official [Releases](https://github.com/SBFlabs/Lightspeed/releases) tab.
- **Application ID**: `com.sbf.lightspeed`
- **Supported Android Versions**: Android 8.0 (API 26) through Android 15 & Android 16 (API 35/36).

---

## 🕹️ Core Feature: The Central Pill

The **Central Pill** is a sleek tactical trigger bar anchored flush to the edge of your screen at the vertical center (between the deflector sectors). It serves as your primary command surface for launching and navigation, executed in a single uninterrupted thumb gesture:

1. **Vertical First Swipe (Category Axis & Central Command)**:
   - Glide your thumb upward from the pill to browse through your app categories (e.g. Avionics, Tools, Media, Social).
   - Gliding all the way to the **bottom-most slot** selects the direct **Settings action**. Lifting your finger immediately opens **Central Command** (the master configuration deck). You do not need a separate settings app icon on your screen—the category slider provides instant 1-touch access.
2. **Horizontal Second Swipe (App Selection)**:
   - While hovering over any app category, glide your thumb horizontally left or right to sweep across the apps in that group.
3. **Lift to Launch**:
   - Simply lift your finger while positioned over an app to launch it at lightspeed.
4. **Horizontal First Swipe (Cockpit Gear Sets)**:
   - If you initiate your thumb movement horizontally from the pill instead of vertically, you enter the **Cockpit Gear Rings**.
   - Continue swiping horizontally without lifting your finger to fluidly rotate between different specialized gear sets.
5. **Cockpit Hangar Deployment**:
   - While in the Cockpit Gear view, swipe inward toward the center until your thumb rests in the **innermost tactical gear ring**, then **lift your finger**.
   - This immediately deploys the **Cockpit Hangar**—a full tactile configuration hangar where you can slot apps, direct system shortcuts, home screen shortcuts, toggle actions, and assign custom icons.

---

## 📡 The Sensor Area (Top Gesture Strip)

The **Sensor Area** is a high-speed gesture strip embedded directly across the top edge and status bar of your display. It turns the top of your phone into an active telemetry and action deck, armed with tactile defaults out of the box:

*(All actions below are customizable in Central Command to launching any app, a shortcut, a home screen app shortcut, or a system action)*:

- **Single Tap**: Scroll to top in supported lists or return immediately to your primary deck.
- **Double Tap**: Orientation Gravity Reset.
- **Tap & Hold**: Notifications shade.
- **Swipe Down**: Notifications shade.
- **Swipe Left**: Perimeter Watchdog.
- **Swipe Left + Hold**: Pop-up Window (freeform floating multi-window).
- **Swipe Right + Hold**: Split Screen mode.
- **Zero Accidental Touches**: High-precision touch zones ensure status bar gestures trigger reliably without interfering with ordinary notification shade pulls.

---

## 🛡️ Deflector Flanks (Edge Gestures)

The left and right screen boundaries act as tactical **Deflector Surfaces** for seamless edge navigation.

*(Every deflector gesture is 100% customizable in Central Command to launching any app, a shortcut, a home screen app shortcut, or a system action; the bindings below are battle-tested out-of-the-box defaults)*:

- **Default Gesture Examples**:
  - **Inward Swipe**: System Back.
  - **Inward Hold**: Gracefully close active foreground app and remove it from recents (powered by Shizuku).
  - **Swipe Down**: Return Home.
  - **Down-Inward**: Open Notifications shade.
  - **Down-Inward Hold**: Open Quick Settings.
  - **Up-Inward**: Instant Screenshot.
- **Precision Scrubbers**:
  - Inward sweep and hold from the right edge smoothly scrubs **Screen Brightness**.
  - Inward sweep and hold from the left edge smoothly scrubs **Media Volume**.
  - Built with spring physics, tactile ticks, and zero UI stutter.
- **Golden Rope Cross-Flank Mirroring**:
  - Tap the Golden Rope to unify or split upper and lower sector gestures.
  - Long-press (>= 400ms) on the rope to symmetrically mirror gesture bindings across to the opposite flank with inverted directions (Swipe Right <-> Swipe Left).

---

## ⚡ Elevated Capabilities (Shizuku Highly Recommended)

Lightspeed operates with standard Android accessibility and overlay permissions. However, **enabling Shizuku is highly, highly recommended** to unlock desktop-class system authority and the full flight experience:

- **Native Previous App Switching**: Switches between recent tasks with zero launcher transition delay.
- **Graceful Task Closer**: Gracefully closes the active foreground application and cleans it directly out of the Android recents stack.
- **Pop-up Freeform Windows**: Launch applications directly into floating multi-window sessions.
- **Watchdog Sentinels**:
  - **Core Watchdog**: Monitors Lightspeed’s own service health and automatically revives the launcher if the Android system kills it.
  - **Perimeter Watchdog**: Actively monitors external accessibility tools (such as Key Mapper or MacroDroid) and resurrects them via elevated Shizuku IPC if OEM battery cleaners kill them.
- **System Overrides (Use-Case Dependent)**:
  - Accessible directly from Central Command.
  - **Native Edge Sovereignty**: Android's native back gestures can collide with third-party edge gestures. With a single tap via Shizuku, Lightspeed zeroes out Android's native back-gesture insets, granting 100% clean edge touch control without ghost back navigation.

---

## 🌌 A Lot More Features to Try

Beyond the primary controls, Lightspeed includes an entire operating deck of maneuvers and avionics:

- **Hull Maneuvers**:
  - Hardware **Volume Button** overrides and dual-key combinations.
  - **Power Button Mapping** *(Experimental Labs)*: Multi-click tap actions and screen-state overrides housed under Central Command's Experimental Labs.
  - Tactile **Back-Tap Gestures** using device accelerometer physics.
- **Telemetry Trackers & Avionics**:
  - **Horizon Rail**: Dynamic, multi-stream telemetry and media playback bar spanning edge-to-edge across both portrait and landscape displays.
  - **Orbital Capsule** *(Experimental Labs)*: Tactical HUD status telemetry and dynamic capsule indicators (accessible via Central Command -> Experimental Labs).
- **Power & Dev Deck**: Automatically detects and slots developer and power tools installed on your device (GitHub, Termux, Shizuku, MiXplorer, X-plore, Key Mapper, MacroDroid) with zero manual setup.
- **High-Performance AGSL & Canvas Graphics**: Custom shaders, tactile collimators, liquid-glass sliders, and animated reticles that run at maximum device refresh rate with zero background battery drain.
- **Self-Contained Backup & Restore**: Full JSON backup engine that encodes custom icons as Base64 strings. Backups are completely portable across devices and hot-reload your workspace instantly with zero app restarts.
- **Per-Gesture Hold Tuning**: Individual hold-duration sliders (150ms–800ms) for every gesture to calibrate the exact feel of your hardware.

---

## 🔐 Permissions Model & Why Each Is Required

Lightspeed requires zero internet access. Every permission requested serves a specific, transparent function:

- **Accessibility Service**: Required to capture gestures (edge swipes, sensor taps) and perform global navigation (Back, Home, Recents, Notifications). Renders the tactile cockpit, deflectors, and HUD over running apps natively without needing separate overlay permissions.
- **Shizuku API (`moe.shizuku.manager.permission.API_V23`)** *(Highly Recommended)*: Enables graceful app closure, instant task switching, edge back neutralization, and Watchdog resurrection.
- **Modify System Settings (`WRITE_SETTINGS`)**: Allows precision brightness scrubbing and screen timeout adjustments.
- **Write Secure Settings (`WRITE_SECURE_SETTINGS`)**: Used by System Overrides to neutralize native back insets (can be granted via Shizuku or ADB).
- **Ignore Battery Optimizations**: Ensures Android OEM task-killers do not put the gesture engine to sleep.
- **Query All Packages (`QUERY_ALL_PACKAGES`)**: Discovers installed apps so you can categorize and launch them.
- **Vibrate (`VIBRATE`)**: Powers tactile haptic feedback for ticks, slider steps, and gesture arming.
- **Notifications (`POST_NOTIFICATIONS`)**: Displays active flight telemetry and ongoing status indicators.
- **Call Phone (`CALL_PHONE`)** *(Optional)*: Required only if you add direct-dial contact shortcuts to Cockpit Gears.
- **Zero Internet Permission**: `android.permission.INTERNET` is completely absent. The app has no network access and cannot transmit telemetry.

---

## 🔒 100% Offline & Strict Privacy Model

Lightspeed is built on a foundation of absolute privacy and zero trust:

- **Zero Network Permissions**: The `android.permission.INTERNET` permission is completely omitted from the Android manifest. The app cannot make network calls, send telemetry, or connect to any remote server.
- **Zero Analytics & Trackers**: No Firebase, no Google Analytics, no crash reporters, and zero third-party tracking SDKs.
- **Zero Account Requirement**: No sign-ins, no cloud dependencies, and no remote databases. All configurations and shortcuts stay strictly on your local device.
- **Auditable Source**: Publicly shared for inspection, security reviews, and privacy audits.

---

## 🚀 Getting Started & First Flight

1. **Install the APK**: Download `Lightspeed-v1.0.0.apk` from the [Releases](https://github.com/SBFlabs/Lightspeed/releases) page and install it.
2. **Grant Permissions**:
   - **Accessibility Service**: Turn on Lightspeed in Android Accessibility Settings. All deflectors and tactile overlays attach immediately.
   - **Shizuku** *(Highly Recommended)*: Authorize Lightspeed in the Shizuku app to enable desktop-class app switching and System Overrides.
3. **Begin Navigation**:
   - Touch the Central Pill at the center edge of your display.
   - Swipe up to browse categories, swipe right to pick an app, and lift to launch!

---

## 💬 Community, Issues & Feedback

GitHub Issues are open and available for help, but are primarily focused on:
- Tracking reproducible **bugs and hardware edge cases**.
- Catching **regressions** across Android OS updates.
- Ideas for **polishing** existing features, gestures, and tactile interactions.

Feature suggestions are always welcome! However, they will be logged into the development backlog for future milestone deployment rather than immediate or recent implementation.

---

## ☕ Free & Supporter Longevity Model

The vast majority of Lightspeed is and will remain free forever. Core gesture navigation, ordinary app launching, deflectors, and backup reliability have zero paywalls.

The **Lightspeed Infinity** tier is designed not as a restrictive constraint, but as a voluntary thank-you incentive for supporters who want to fuel ongoing development, polish, and longevity through [Buy Me a Coffee](https://buymeacoffee.com/sbflabs/e/579114). Supporter contributions unlock third-tier visual perks, specialized cockpit themes, custom reticle collimators, and unlimited Cockpit Gear sets.

---

## 📄 License & Terms

Lightspeed is **source available, not open source**.

You are welcome to inspect the source code, conduct independent security and privacy audits, and compile local personal builds for your own non-commercial use. Redistribution of source code or compiled binaries, commercial use, and publishing derivative works are not permitted without prior written consent from SBF Labs.
