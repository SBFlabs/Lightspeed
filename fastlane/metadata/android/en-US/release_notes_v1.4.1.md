### ✨ Features
- **Multi-Language (`EN`, `PT-BR`, `ES`) & Real-World Bilingual Mode:** Full Brazilian Portuguese and Spanish localization with first-launch auto-detection, instant switching with no app restart, Bilingual secondary subtext headers, a 2-axis Guidebook switcher with rolling language wheel, Lore + Plain vocabulary pairing, and translated READMEs.
- **Material 3 Default, Unified Core Setup & Day-1 Defaults:** New opaque Material 3 theme synced to wallpaper colors (3 free / 10 Lightspeed ∞ themes), unified Arm Lightspeed Core onboarding screen, Signature HUD scrubber defaults, Live Lab Backdrop Opacity, and pop-ups that stay scrollable and on-screen at large display sizes.
- **Screen Time Gauges, Offline Calendar Countdown & Culprit Hunter Upgrades:** Added Day/Week/Month/Year progress bars, an Ignore Apps exclusion list, a 4-wheel countdown editor, strictly opt-in 100% offline Next Calendar Event countdowns, plus Culprit Hunter numbered quick-jump sort rails and built-in `!` metric explainers.
- **Category Cruise Launch Animations & Ergonomics:** 5 selectable icon-centered launch spark animations when opening apps, closer thumb-reach category labels, smoother vertical scrolling over HUD widgets, a distinct two-beat haptic when revealing Central Command, and a 2-option Airlock Abort action (`Close App` vs `Go Home`).
- **Global Overlay Fonts & Independent Text Scaling:** Apply your chosen device font across Cockpit, Category Cruise, and HUD widgets, or tune Category Cruise and Cockpit Hangar text sizes independently.

### 🐛 Bug Fixes
- **Astrogation Airlock Edge Cases:** Stopped Airlock from re-triggering when exiting long fullscreen videos (passive playback without touch events), eliminated 1-frame overlay flashes during rapid app switches, and debounced transient launcher or in-app updater screens.
- **Backdrop Blur, High-Density Contrast & Window Dimming:** Verified SurfaceFlinger blur compatibility, prevented self-screenshots on blur mode switches, sharpened high-density frosted HUD glass contrast, and fixed Samsung One UI translucent window dimming.
- **Live Lab Surface Opacity, 120Hz Scroll & Landscape Horizon Rail:** Fixed Live Lab Surface Opacity having no visible effect across themes, eliminated Central Command scroll stutter and oversized display-size popup clipping, and fixed full-width Horizon Rail spans across landscape cutouts.
- **Battery Metrics, Tune Panel & Widget Scrolling:** Fixed `LAST CHG` battery spans matching `7-DAY`, filtered uncalibrated OEM battery cycle counts, pinned the HUD Tune panel without ghost font dropdowns, fixed Refueling Bay `< 0.5W` rounding, and allowed vertical scrolling over non-overflowed widgets.

### 💙 Love Lightspeed? Help It Grow
[![Buy Me A Coffee](https://img.shields.io/badge/Buy_Me_A_Coffee-Lightspeed_∞-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/sbflabs/e/579114)

- **Show it off**: On Android subreddits or [XDA Forums](https://xdaforums.com/t/4804419/), if you find users talking about third-party launchers breaking gestures, or looking for a full-fledged gesture app or launcher-agnostic meta-launcher & workstation, show Lightspeed off! A screenshot, quick clip, or recommendation goes a long way — or simply direct them to [r/SBFlabs](https://www.reddit.com/r/SBFlabs/) and [YouTube (@SBFlabs)](https://www.youtube.com/@SBFlabs) for video showcases and screenshots.
- **Star the repo**: A star on [GitHub](https://github.com/SBFlabs/Lightspeed) helps more users discover Lightspeed.
- **Buy me a coffee**: [buymeacoffee.com/sbflabs](https://buymeacoffee.com/sbflabs/e/579114) ($1.99 · Google Pay supported) unlocks unlimited flight decks and all signature themes, keeps 100% offline development thriving, and includes a free Google Play code if/when a Play Store version launches.

---
📖 **Full changelog:** [CHANGELOG.md](https://github.com/SBFlabs/Lightspeed/blob/master/CHANGELOG.md)  
🛠️ **Code Refactoring:** Modularized oversized HUD, vitals, and tune modals into smaller focused components, eliminated per-frame canvas allocations, switched Settings accordions to hardware RenderNode clipping, and added format-string and hardcoded-UI-string compliance audits.

🔑 **SHA-256 Checksum:**
- `app-arm64-v8a-release.apk`: `8cb784c1bc7be1f19dc999b78f8fb294d3cfb7529dc0658cb5a2bc9c4e0c79ce`
- `app-armeabi-v7a-release.apk`: `f75f1becaaf241ae3ba08e615ac01200ca3888b57341decb4fc67b95a22e9ddc`
