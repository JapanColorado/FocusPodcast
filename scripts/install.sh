#!/usr/bin/env bash
# Installs the debug APK on the single connected adb device.
set -euo pipefail
cd "$(dirname "$0")/.."

apk="$(ls app/build/outputs/apk/*/debug/*.apk app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n1 || true)"
[ -n "$apk" ] || { echo "install: no debug APK found; run 'pixi run build' first" >&2; exit 1; }

devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$devices" ] || { echo "install: no adb device in 'device' state (check USB debugging / authorization)" >&2; exit 1; }

echo "install: $apk"
if ! out="$(adb install -r "$apk" 2>&1)"; then
  echo "$out" >&2
  if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$out"; then
    echo "install: the installed app is signed with a different key. Uninstall it first:" >&2
    echo "    adb uninstall allen.town.focus.podcast" >&2
  fi
  exit 1
fi
echo "$out"
