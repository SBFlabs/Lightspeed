import os
import zipfile

gradle_cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/dev.rikka.shizuku/api/")
if os.path.exists(gradle_cache):
    for root, dirs, files in os.walk(gradle_cache):
        for file in files:
            if file.endswith(".aar"):
                aar_path = os.path.join(root, file)
                print(f"Extracting {aar_path}")
                os.system(f"unzip -p {aar_path} classes.jar > /tmp/shizuku_classes.jar")
                os.system("javap -public -cp /tmp/shizuku_classes.jar rikka.shizuku.Shizuku")
                break
