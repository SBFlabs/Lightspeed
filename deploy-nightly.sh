#!/usr/bin/env bash
set -e

DEVICE_IP="192.168.100.10:5555"
PROJECT_DIR="$HOME/Lightspeed"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJECT_DIR"

echo "📱 [1/4] Connecting to device via Wireless ADB ($DEVICE_IP)..."
adb disconnect 2>/dev/null || true
adb connect "$DEVICE_IP"

echo "💾 [2/4] Checking and auto-committing workspace changes..."
if [[ -n $(git status --porcelain) ]]; then
    COMMIT_MSG="Nightly Auto-Build $(date '+%Y-%m-%d %H:%M:%S')"
    git add -A
    git commit -m "$COMMIT_MSG"
    echo "✓ Committed: $COMMIT_MSG"
else
    echo "✓ Git tree clean (no new changes to commit)."
fi

echo "⚙️ [3/4] Compiling Lightspeed Nightly APK..."
./gradlew packageDebug --rerun-tasks

echo "🚀 [4/4] Installing Nightly build to device ($APK_PATH)..."
adb -s "$DEVICE_IP" install -r -d "$APK_PATH"

echo "🔍 Verifying physical installation on device..."
adb -s "$DEVICE_IP" shell "dumpsys package com.sbf.lightspeed.nightly | grep -E 'versionCode|lastUpdateTime'"

echo "✅ Lightspeed Nightly deployed successfully!"
