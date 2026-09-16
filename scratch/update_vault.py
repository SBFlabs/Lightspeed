import re

with open('VAULT.md', 'r') as f:
    content = f.read()

# Replace the Floating OEM Volume Slider Companion section with the new expansive Audio & Brightness docks
old_section = """   - Floating OEM Volume Slider Companion (4-in-1 Avionics Audio Dock):
     * Contextual micro-dock anchored directly beneath or adjacent to the native device/OEM volume slider dialog when active.
     * Integrates 4 core audio sovereignty controls in one tactile touchpoint:
       1. *Paired App Volume Slider*: Dynamic secondary slider to adjust volume specifically for the currently foreground app or designated paired audio app, independent of global master media volume.
       2. *Multi-Sound Concurrent Playback Lock*: Enforces concurrent background audio playback for one or multiple designated apps, preventing other media players, videos, or games from stealing audio focus, ducking, or pausing background streams.
       3. *Instant Do Not Disturb (DND) Mode Toggle*: One-tap tactile toggle for system DND state with canopy telemetry.
       4. *Sound Profile Mode Quick-Cycle*: Direct switching between Normal Sound, Vibrate, and Silent/Mute modes."""

new_section = """   - The Omniscient Audio Command Center (Advanced Volume Scrubber Dock):
     * A massive expansion to the `system:volume` and `scrub:volume` actions. When scrubbing volume from the edge, a contextual floating command center (HUD) appears, utilizing gestures to expand into a master audio panel.
     * Integrates extreme audio sovereignty controls:
       1. *Independent App Volume Routing*: Dynamic per-app volume sliders, allowing independent volume levels for the foreground app, background music, system notifications, alarms, and Google Assistant.
       2. *Multi-Sound Concurrent Playback Lock (App Pinning)*: A physical "Pin" toggle for specific apps (like Spotify or YouTube). When pinned, the app retains absolute audio focus, forcing it to keep playing uninterrupted even when other media, games, or ads try to steal focus or duck the volume.
       3. *Global Mute & DND Matrix*: One-tap tactile toggle for absolute Global Mute, alongside an instant Do Not Disturb (DND) toggle, bypassing all volume streams instantly.
       4. *Sound Profile Quick-Cycle*: Direct switching between Normal Sound, Vibrate, and Silent/Mute modes.

   - The Omniscient Display Engine (Advanced Brightness Scrubber Dock):
     * A massive expansion to the `system:brightness` and `scrub:brightness` actions. When scrubbing brightness, the floating telemetry HUD grants access to absolute display control.
     * Integrates extreme display sovereignty controls:
       1. *Below-Zero Extra-Dim Sub-Routing*: When dragging the brightness slider below 0%, it automatically bridges into Android's native `Extra Dim` API (reduce bright colors), seamlessly lowering the physical screen brightness beyond hardware minimums.
       2. *Over-100% Media Player Hijacking*: When dragging past 100%, Lightspeed takes back display control from stubborn media players (like YouTube or Netflix) that use proprietary in-app gesture brightness overriding the system.
       3. *Color Temperature / Eye Care Slider*: A secondary slider to directly adjust the screen's color temperature (Night Light / Eye Comfort Shield / Blue Light Filter) from cool to warm amber.
       4. *Ambient Mesh screensaver*: A toggle/slider to instantly convert the screen into the ambient mesh UI (similar to the Google Home Hub screensaver) for beautiful ambient telemetry when the phone is docked and charging."""

content = content.replace(old_section, new_section)

# Also update Lock-Screen Shortcuts Engine to reflect that we discovered the FLAG_SHOW_WHEN_LOCKED overlay bypass
old_lock_screen = """   - Custom Lock-Screen Shortcuts Engine:
     * Re-mapping left/right lock-screen shortcut slots via direct secure settings manipulation, MacroDroid bridge, or custom intent routing."""

new_lock_screen = """   - The Lock-Screen Overlay Bypass (Completed):
     * OEMs like Transsion completely hardcoded the secure `sysui_keyguard_left/right` databases.
     * Workaround implemented: Injected `FLAG_SHOW_WHEN_LOCKED` directly into Lightspeed's sensor layers. The entire tactical HUD and edge gestures now seamlessly draw *over* the lock screen, completely eliminating the need for native lock screen shortcuts."""

content = content.replace(old_lock_screen, new_lock_screen)

with open('VAULT.md', 'w') as f:
    f.write(content)
