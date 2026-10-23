#!/bin/bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[[ -x "$JAVA_HOME/bin/java" ]] || { echo "JDK missing: $JAVA_HOME" >&2; exit 1; }
[[ -d "$ANDROID_HOME/platforms" ]] || { echo "Android SDK missing: $ANDROID_HOME" >&2; exit 1; }
mkdir -p tmp/logs
BUILD_LOG="tmp/logs/build-debug-$(date +%Y%m%d-%H%M%S).log"
./gradlew :app:assembleDebug --console=plain "$@" 2>&1 | tee "$BUILD_LOG"
