# Benchmarks and performance budgets

JMH benchmarks for the public API, in [`../src/jvmBenchmark`](../src/jvmBenchmark), run with kotlinx-benchmark 0.5.0
(JMH 1.37).

## Budgets

| Workload | Environment | Method | Threshold |
|---|---|---|---|
| `SqiSign.generateKeyPair`, `sign` (32-byte message), `verify` (valid signature); each of `P324_3`, `P500_27`, `P664_17` | Reference host (below), JVM target, `arm64` C build | `./gradlew jvmBenchmarkBenchmark` (avgt, ms/op, 2 forks × 5 × 2 s after 3 warmups) | At most 10 % slower than the [committed baseline](baseline-jvm-macos-aarch64-m4max.json) per benchmark (Constitution P4) |

Baseline scores on the reference host (mean ± 99.9 % confidence interval, ms/op):

| Parameter set | Key generation | Signing | Verification |
|---|---|---|---|
| `P324_3` (NIST level 1) | 5.35 ± 0.09 | 16.1 ± 0.6 | 1.86 ± 0.04 |
| `P500_27` (NIST level 3) | 20.7 ± 1.5 | 59.7 ± 6.6 | 4.56 ± 0.02 |
| `P664_17` (NIST level 5) | 36.5 ± 1.7 | 109 ± 7.6 | 10.6 ± 0.02 |

Reference host: Apple M4 Max (14 cores, 36 GB), macOS 26.7.1, AC power, OpenJDK 21.0.13 (Homebrew), recorded
2026-10-21 at `f87b8f0` plus the uncommitted benchmark changes.

## When to measure

Changes to the budgeted path need a comparison against the baseline (Constitution P3). The budgeted path is:
- `src/*Main`;
- `native/`;
- the vendor C code;
- native build flags.

From `kotlin/`, on the reference host:

```sh
./gradlew jvmBenchmarkBenchmark
python3 benchmarks/compare.py benchmarks/baseline-jvm-macos-aarch64-m4max.json \
  build/reports/benchmarks/main/<timestamp>/jvmBenchmark.json
```

`compare.py` exits 1 if a benchmark is more than 10 % slower than the baseline, or if it is missing. Signing is
randomized and varies most (±11 % for `P500_27`), so rerun a failing comparison before treating it as a regression.
Attach the comparison output to the pull request.

Replacing the baseline needs a written rationale in the pull request (P4). Run the measurement profile on the reference
host and copy the report over the baseline file. Also replace it when the host, JDK, JMH or kotlinx-benchmark version
changes; toolchain drift is not a code regression.

## CI

The `JVM native (linux-x86_64)` job runs `./gradlew jvmBenchmarkSmokeBenchmark` (one 200 ms iteration, `P324_3` only).
This proves that the benchmarks build and run. Its timings are not compared with the baseline, because shared GitHub
runners are not a controlled environment.
