#!/usr/bin/env bash
# Verifies the toolchain before a Gradle invocation and writes local.properties if missing.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "check-env: $*" >&2; exit 1; }

[ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME is not set; run through 'pixi run <task>'"
java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"
case "$java_version" in
  *'"17.'*) ;;
  *) fail "expected JDK 17 at $JAVA_HOME, got: $java_version" ;;
esac

[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is not set"
[ -d "$ANDROID_HOME/platforms/android-35" ] || fail "Android platform 35 not found under $ANDROID_HOME/platforms (install it with sdkmanager)"
[ -d "$ANDROID_HOME/build-tools/34.0.0" ] || fail "build-tools 34.0.0 not found under $ANDROID_HOME/build-tools"

if [ ! -f local.properties ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "check-env: wrote local.properties (sdk.dir=$ANDROID_HOME)"
fi

if [ ! -f secrets.properties ]; then
  echo "check-env: warning: secrets.properties is missing; release builds need it (see secrets.properties.sample)" >&2
fi
