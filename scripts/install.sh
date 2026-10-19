#!/usr/bin/env bash
# Installs the release (default) or debug APK on the single connected adb device.
# Usage: scripts/install.sh [release|debug]
set -euo pipefail
cd "$(dirname "$0")/.."

type="${1:-release}"
case "$type" in
  release) build_task="build"; package="dev.russell.focuspodcast" ;;
  debug) build_task="build-debug"; package="dev.russell.focuspodcast.debug" ;;
  *) echo "install: unknown build type '$type' (expected release or debug)" >&2; exit 2 ;;
esac

apk="$(ls app/build/outputs/apk/"$type"/*.apk 2>/dev/null | head -n1 || true)"
[ -n "$apk" ] || { echo "install: no $type APK found; run 'pixi run $build_task' first" >&2; exit 1; }

devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$devices" ] || { echo "install: no adb device in 'device' state (check USB debugging / authorization)" >&2; exit 1; }

echo "install: $apk"
if ! out="$(adb install -r "$apk" 2>&1)"; then
  echo "$out" >&2
  if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$out"; then
    echo "install: the installed app is signed with a different key. Uninstall it first:" >&2
    echo "    adb uninstall $package" >&2
  fi
  exit 1
fi
echo "$out"
