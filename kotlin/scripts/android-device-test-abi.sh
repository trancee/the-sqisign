#!/usr/bin/env bash
# Runs the assembled device-test APK with its native libraries for one ABI, e.g. arm64-v8a on an x86_64 emulator
# through the system image's ARM translation (API 30+). Run from kotlin/ after assembleAndroidDeviceTest.
# Usage: scripts/android-device-test-abi.sh <abi>
set -euo pipefail

abi=${1:?usage: $0 <abi>}
apk=$(ls build/outputs/apk/androidTest/*.apk)
package=org.sqisign.test

adb install -r -t --abi "$abi" "$apk"
trap 'adb uninstall "$package" > /dev/null || true' EXIT

installed_abi=$(adb shell pm dump "$package" | tr -d '\r' | sed -n 's/^ *primaryCpuAbi=//p' | head -1)
echo "device ABIs: $(adb shell getprop ro.product.cpu.abilist | tr -d '\r'), installed ABI: $installed_abi"
[ "$installed_abi" = "$abi" ] || { echo "expected $abi"; exit 1; }

output=$(adb shell am instrument -w "$package/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r')
echo "$output"
grep -q '^OK (' <<< "$output"
