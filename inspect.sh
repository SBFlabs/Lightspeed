#!/usr/bin/env bash

OUT="lightspeed_summary.txt"
exec > "$OUT" 2>&1

echo "==================== 1. RECENT COMMITS ===================="
git log -n 12 --oneline 2>/dev/null || echo "No git repo detected"

echo -e "\n==================== 2. SOURCE TREE ===================="
if command -v tree >/dev/null 2>&1; then
    tree app/src/main/ -I 'res|assets|build' --prune
else
    find app/src/main -type f \( -name "*.kt" -o -name "AndroidManifest.xml" \) | sort
fi

echo -e "\n==================== 3. MANIFEST & PERMISSIONS ===================="
cat app/src/main/AndroidManifest.xml 2>/dev/null

echo -e "\n==================== 4. DEPENDENCIES & SDK TARGETS ===================="
grep -E "dependencies\s*\{|implementation|api|targetSdk|minSdk" app/build.gradle* 2>/dev/null

echo -e "\n==================== 5. CLASS & INTERFACE OUTLINES ===================="
find app/src/main -name "*.kt" | while read -r file; do
    echo "--- $file ---"
    grep -En "^(sealed |data |enum )?(class|interface|object) " "$file"
done

echo -e "\n==================== 6. FILE SIZES (LOC) ===================="
find app/src/main -name "*.kt" -exec wc -l {} + 2>/dev/null | sort -nr | head -n 25

echo -e "\nInspection complete."
