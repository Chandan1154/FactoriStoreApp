#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    printf 'Usage: %s <apk> <expected-application-id> <expected-app-name>\n' "$0" >&2
    exit 2
fi

apk="$1"
expected_id="$2"
expected_name="$3"
if [[ ! -f "$apk" ]]; then
    printf 'APK does not exist: %s\n' "$apk" >&2
    exit 1
fi

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
if [[ ! -d "$sdk/build-tools" ]]; then
    printf 'Android SDK build-tools not found; set ANDROID_SDK_ROOT or ANDROID_HOME.\n' >&2
    exit 1
fi

tool_paths="$(python3 - "$sdk/build-tools" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])
versions = sorted(
    (path for path in root.iterdir() if path.is_dir()),
    key=lambda path: tuple(int(part) if part.isdigit() else part for part in path.name.split(".")),
)
for tool in ("aapt", "apksigner"):
    candidate = next((path / tool for path in reversed(versions) if (path / tool).is_file()), None)
    if candidate is None:
        raise SystemExit(f"Android SDK tool not found: {tool}")
    print(candidate)
PY
)"
aapt="$(printf '%s\n' "$tool_paths" | sed -n '1p')"
apksigner="$(printf '%s\n' "$tool_paths" | sed -n '2p')"
badging="$("$aapt" dump badging "$apk")"

if ! printf '%s\n' "$badging" | grep -Fq "package: name='$expected_id'"; then
    printf 'APK application ID does not match expected value: %s\n' "$expected_id" >&2
    exit 1
fi
if ! printf '%s\n' "$badging" | grep -Fq "application-label:'$expected_name'"; then
    printf 'APK application label does not match expected value: %s\n' "$expected_name" >&2
    exit 1
fi
"$apksigner" verify "$apk"
printf 'APK validated: package ID, app label, and APK signature verified.\n'
