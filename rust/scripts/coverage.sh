#!/usr/bin/env bash
# Coverage gate (100% lines, functions and regions), run unchanged locally and in CI
# (`.github/workflows/rust.yml`). It cleans first: instrumented binaries left in `target/llvm-cov-target`
# by an earlier source revision would otherwise be reported as uncovered code.
#
# Usage: `scripts/coverage.sh [extra cargo-llvm-cov arguments]`, from any directory.
set -euo pipefail
cd "$(dirname "$0")/.."
cargo llvm-cov clean --workspace
exec cargo llvm-cov --locked -p sqisign -p sqisign-capi \
  --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100 "$@"
