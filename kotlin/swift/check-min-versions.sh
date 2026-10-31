#!/usr/bin/env bash
# Fails when an object in SqiSign.xcframework needs a newer iOS or macOS than a SwiftPM manifest declares in
# `platforms`; the linker only warns, and the app could then crash on the declared minimum.
#   check-min-versions.sh <SqiSign.xcframework> <Package.swift>...
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <SqiSign.xcframework> <Package.swift>..." >&2
  exit 2
fi
xcframework=$1
shift

# Highest LC_BUILD_VERSION minos in a static library, e.g. 15.0.
highest_minos() {
  otool -l "$1" | awk '$1 == "minos" { print $2 }' | sort -t. -k1,1n -k2,2n | tail -1
}

# Major version declared for a platform in a manifest, e.g. `.iOS(.v15)` -> 15.
declared() {
  sed -n "s/.*\.$2(\.v\([0-9]*\)).*/\1/p" "$1" | head -1
}

status=0
for manifest in "$@"; do
  for slice in ios-arm64:iOS ios-arm64-simulator:iOS macos-arm64:macOS; do
    dir=${slice%%:*}
    platform=${slice##*:}
    binary="$xcframework/$dir/SqiSign.framework/SqiSign"
    want=$(declared "$manifest" "$platform")
    have=$(highest_minos "$binary")
    if [[ -z $want || -z $have ]]; then
      echo "$manifest $dir: cannot read the declared ($want) or built ($have) minimum" >&2
      status=1
    elif [[ $have != "$want.0" && $(printf '%s\n' "$want.0" "$have" | sort -t. -k1,1n -k2,2n | tail -1) == "$have" ]]
    then
      echo "$manifest declares $platform $want, but $dir needs $have" >&2
      status=1
    else
      echo "$manifest $dir: declared $platform $want, built for $have"
    fi
  done
done
exit $status
