#!/bin/sh
# Builds pinch.dex (test tool, not part of the app). Needs JDK and Android SDK build-tools.
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
JAR="$SDK/platforms/android-35/android.jar"
D8="$(ls "$SDK"/build-tools/*/d8.bat "$SDK"/build-tools/*/d8 2>/dev/null | tail -1)"
rm -rf out && mkdir out
javac -source 11 -target 11 -cp "$JAR" -d out Pinch.java
"$D8" --min-api 30 --lib "$JAR" --output out out/Pinch.class
mv out/classes.dex pinch.dex
echo built pinch.dex
