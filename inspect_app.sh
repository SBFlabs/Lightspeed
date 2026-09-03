#!/usr/bin/env bash

OUT="audit_summary.txt"
echo "=== 1. PROJECT ARCHITECTURE & FILE SIZES (Over 300 Lines) ===" > "$OUT"
find . -type f \( -name "*.kt" -o -name "*.java" \) ! -path "*/build/*" -exec wc -l {} + | awk '$1 > 300 {print $1, $2}' | sort -nr >> "$OUT"

echo -e "\n=== 2. MANIFEST & PERMISSIONS ===" >> "$OUT"
find . -name "AndroidManifest.xml" ! -path "*/build/*" -exec cat {} + | grep -E "uses-permission|permission|exported|usesCleartextTraffic" >> "$OUT"

echo -e "\n=== 3. DEPENDENCIES & BUILD CONFIG ===" >> "$OUT"
find . -name "build.gradle*" ! -path "*/build/*" -exec grep -E "implementation|api|minifyEnabled|shrinkResources" {} + >> "$OUT"

echo -e "\n=== 4. HARDCODED SECRETS & NETWORKING STRINGS ===" >> "$OUT"
grep -rnEi "(api_key|apikey|secret|password|bearer|http://|https://)" --exclude-dir={build,.git,.gradle} app/src/ 2>/dev/null | head -n 30 >> "$OUT"

echo -e "\n=== 5. TOP-LEVEL STRUCTURE ===" >> "$OUT"
find app/src/main/ -maxdepth 3 ! -path "*/build/*" >> "$OUT"

echo "Audit report generated at: $OUT"
cat "$OUT"
