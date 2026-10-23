#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}"
KEYSTORE="${MUSCLEJOURNAL_KEYSTORE:-$HOME/.android/debug.keystore}"
STORE_PASSWORD="${MUSCLEJOURNAL_STORE_PASSWORD:-android}"
KEY_ALIAS="${MUSCLEJOURNAL_KEY_ALIAS:-androiddebugkey}"
KEY_PASSWORD="${MUSCLEJOURNAL_KEY_PASSWORD:-android}"

[[ -x "$JAVA_HOME/bin/java" ]] || { echo "JDK missing: $JAVA_HOME" >&2; exit 1; }
[[ -x "$BUILD_TOOLS/zipalign" ]] || { echo "zipalign missing: $BUILD_TOOLS" >&2; exit 1; }
[[ -x "$BUILD_TOOLS/apksigner" ]] || { echo "apksigner missing: $BUILD_TOOLS" >&2; exit 1; }
[[ -f "$KEYSTORE" ]] || { echo "Keystore missing: $KEYSTORE" >&2; exit 1; }

mkdir -p tmp/logs tmp/release
BUILD_LOG="tmp/logs/build-release-$(date +%Y%m%d-%H%M%S).log"
./gradlew :app:assembleRelease --offline --console=plain 2>&1 | tee "$BUILD_LOG"

UNSIGNED="tmp/build/app/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$UNSIGNED" ]] || { echo "Unsigned release APK missing" >&2; exit 1; }

AAPT2="$BUILD_TOOLS/aapt2"
VERSION_NAME="$("$AAPT2" dump badging "$UNSIGNED" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" | head -1)"
[[ -n "$VERSION_NAME" ]] || { echo "Unable to read versionName" >&2; exit 1; }

ALIGNED="tmp/release/MuscleJournal-$VERSION_NAME-aligned.apk"
FINAL="tmp/release/MuscleJournal-$VERSION_NAME.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$STORE_PASSWORD" \
  --key-pass "pass:$KEY_PASSWORD" \
  --out "$FINAL" \
  "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$FINAL"
"$BUILD_TOOLS/zipalign" -c -p 4 "$FINAL"
rm -f "$ALIGNED"
(cd tmp/release && shasum -a 256 "MuscleJournal-$VERSION_NAME.apk" | tee "MuscleJournal-$VERSION_NAME.apk.sha256")
echo "Release APK: $FINAL"
