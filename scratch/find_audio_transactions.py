import subprocess
import re

out = subprocess.check_output("adb shell dumpsys audio | grep -i appVolume", shell=True, text=True)
print("Dumpsys audio:")
print(out)
