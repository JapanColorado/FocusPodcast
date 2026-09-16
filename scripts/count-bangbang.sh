#!/usr/bin/env bash
# Ratchet: the number of Kotlin not-null assertions (!!) may never grow.
set -euo pipefail
cd "$(dirname "$0")/.."

max="$(cat scripts/bangbang.max)"
dirs=()
for d in app core lib focus-common focus-theme searchpreference; do [ -d "$d" ] && dirs+=("$d"); done
count="$(grep -rho '!!' --include='*.kt' "${dirs[@]}" | grep -v '/build/' | wc -l || true)"
echo "Kotlin !! count: $count (max $max)"
if [ "$count" -gt "$max" ]; then
  echo "count-bangbang: too many !! assertions; lower the count or update scripts/bangbang.max" >&2
  exit 1
fi
