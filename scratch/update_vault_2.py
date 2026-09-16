import re

with open('VAULT.md', 'r') as f:
    content = f.read()

# Update Audio section with Headphone EQ profiles
old_audio = "4. *Sound Profile Quick-Cycle*: Direct switching between Normal Sound, Vibrate, and Silent/Mute modes."
new_audio = """4. *Sound Profile Quick-Cycle*: Direct switching between Normal Sound, Vibrate, and Silent/Mute modes.
       5. *Audiophile Parametric EQ (AutoEQ) Import*: Ability to import specific parametric EQ profile files (like Wavelet or AutoEQ presets) tuned for specific headphones directly into the audio engine for system-wide sound correction."""
content = content.replace(old_audio, new_audio)

# Update Display section with Night Sky AOD
old_display = "4. *Ambient Mesh screensaver*: A toggle/slider to instantly convert the screen into the ambient mesh UI (similar to the Google Home Hub screensaver) for beautiful ambient telemetry when the phone is docked and charging."
new_display = """4. *Ambient Mesh screensaver*: A toggle/slider to instantly convert the screen into the ambient mesh UI (similar to the Google Home Hub screensaver) for beautiful ambient telemetry when the phone is docked and charging.
       5. *"Night Sky" Always-On Display (AOD)*: A dedicated, highly customizable AOD feature natively built into Lightspeed (similar in architecture to the Refueling Bay). Night Sky acts as an advanced ambient display, potentially integrating the Mesh UI, to replace the OEM AOD entirely."""
content = content.replace(old_display, new_display)

with open('VAULT.md', 'w') as f:
    f.write(content)
