#!/usr/bin/env bash
# Line and branch coverage of the C glue (native/src/*.c) through the JVM tests, using Clang source-based coverage.
# Needs Clang plus llvm-profdata and llvm-cov (xcrun on macOS; PATH, or LLVM_PROFDATA/LLVM_COV, elsewhere).
# Run from kotlin/. Extra arguments go to Gradle. Reports: build/native-coverage/{report.txt,html/}.
# Afterwards, a normal build rebuilds the uninstrumented libraries.
set -euo pipefail

if [ "$(uname)" = Darwin ]; then
    profdata=${LLVM_PROFDATA:-$(xcrun -f llvm-profdata)}
    cov=${LLVM_COV:-$(xcrun -f llvm-cov)}
else
    profdata=${LLVM_PROFDATA:-llvm-profdata}
    cov=${LLVM_COV:-llvm-cov}
fi
out=build/native-coverage

# The coverage mapping is read from the CMake build directories, which a build-cache hit does not create, so the
# native builds run too.
./gradlew buildNativeJvm --rerun buildNativeJvmKat --rerun jvmTest --rerun -Psqisign.nativeCoverage=true "$@"

profiles=("$out"/profiles/*.profraw)
[ -e "${profiles[0]}" ] || { echo "no coverage profiles in $out/profiles"; exit 1; }
"$profdata" merge -sparse -o "$out/merged.profdata" "${profiles[@]}"

# The unstripped libraries from the CMake build directories carry the coverage mapping.
objects=()
for task in buildNativeJvm buildNativeJvmKat; do
    lib=$(find "build/sqisign/cmake/$task" -maxdepth 1 \( -name '*sqisign*jni.so' -o -name '*sqisign*jni.dylib' \) | head -1)
    [ -n "$lib" ] || { echo "no library for $task"; exit 1; }
    objects+=(${objects:+-object} "$lib")
done

sources=(native/src)
"$cov" report -instr-profile "$out/merged.profdata" "${objects[@]}" -show-region-summary=false "${sources[@]}" \
    | tee "$out/report.txt"
"$cov" show -instr-profile "$out/merged.profdata" "${objects[@]}" -format=html -output-dir "$out/html" \
    -show-branches=count "${sources[@]}"

# Gate: every glue function runs and lines stay at the floor. The missed lines are malloc failures and upstream
# signing failures, which tests cannot trigger; branches are reported only, for the same reason.
read -r functions lines < <(awk '$1 == "TOTAL" { sub("%", "", $4); sub("%", "", $7); print $4, $7 }' "$out/report.txt")
min_functions=${MIN_FUNCTIONS:-100}
min_lines=${MIN_LINES:-98}
echo "functions ${functions}% (min ${min_functions}%), lines ${lines}% (min ${min_lines}%)"
awk -v f="$functions" -v l="$lines" -v mf="$min_functions" -v ml="$min_lines" 'BEGIN { exit !(f >= mf && l >= ml) }' \
    || { echo "native glue coverage below the minimum"; exit 1; }
