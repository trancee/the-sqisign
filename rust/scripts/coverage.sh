#!/usr/bin/env bash
# Coverage gate (100% lines, functions, regions and branches), run unchanged locally and in CI
# (`.github/workflows/rust.yml`). Branch coverage needs nightly: `COVERAGE_TOOLCHAIN`, by default the pinned
# nightly of the Miri job, with `llvm-tools-preview`.
#
# It cleans first: instrumented binaries left in `target/llvm-cov-target` by an earlier source revision would
# otherwise be reported as uncovered code. A missed `--fail-under` threshold exits 1 without a message, so the
# gate runs on the text report, whose table and missing lines show which; the JSON report feeds
# `branch_coverage.py`, which runs even if the text gate fails.
#
# Usage: `scripts/coverage.sh [extra cargo-llvm-cov arguments for the test run]`, from any directory.
set -euo pipefail
cd "$(dirname "$0")/.."
cov=(cargo "+${COVERAGE_TOOLCHAIN:-nightly-2026-10-23}" llvm-cov)
pkgs=(--branch -p sqisign -p sqisign-capi)
"${cov[@]}" clean --workspace
"${cov[@]}" --locked "${pkgs[@]}" --no-report "$@"
status=0
"${cov[@]}" report "${pkgs[@]}" --show-missing-lines \
  --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100 || status=$?
"${cov[@]}" report "${pkgs[@]}" --json --output-path coverage.json
python3 scripts/branch_coverage.py coverage.json || status=$?
exit "$status"
