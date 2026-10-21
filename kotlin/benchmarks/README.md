# Benchmarks and performance budgets

JMH benchmarks for the public API, in [`../src/jvmBenchmark`](../src/jvmBenchmark), run with kotlinx-benchmark 0.5.0
(JMH 1.37). The same workload also runs on Android and iOS devices (see [Device benchmarks](#device-benchmarks)).

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

## Device benchmarks

Device timings are informational. Unlike the JVM baseline they have no budget or automatic comparison, because
signing and key generation are randomized and devices vary with temperature. Re-measure on the same device after a
change to the budgeted path, and attach the summary to the pull request.

| Platform | Tool | Command (from `kotlin/`) | Raw results |
|---|---|---|---|
| Android | AndroidX Microbenchmark 1.5.0 (`benchmark-android/`): release APK, AOT `speed` compilation, 50 measurements | `ANDROID_SERIAL=<serial> ./gradlew :benchmark-android:connectedReleaseAndroidTest` | [`device-android-nothing-phone-1.json`](device-android-nothing-phone-1.json) |
| iOS | XCTest `measure` (`swift/Tests/SqiSignKitBenchmarks`) through `SqiSignKit`, 20 iterations, release XCFramework | `xcodebuild test` with scheme `SqiSignDeviceBenchmarks` in `swift/DeviceHost` (see the [README](../README.md#tests)); then `python3 benchmarks/xctest_summary.py <log> --raw <out.txt>` | [`device-ios-iphone15.txt`](device-ios-iphone15.txt) |

Both use the JVM benchmarks' workload: a 32-byte message, and one key pair per signing or verification benchmark.

Results recorded 2026-10-21. Median in ms, range (min–max) in parentheses:

| Parameter set | Operation | Nothing Phone (1), Android 15 | iPhone 15, iOS 17.6.1 | JVM reference host (mean) |
|---|---|---|---|---|
| `P324_3` | Key generation | 15.5 (14.8–17.6) | 7.7 (6.6–9.8) | 5.35 |
| | Signing | 45.4 (41.1–56.0) | 20.6 (19.3–31.7) | 16.1 |
| | Verification | 5.81 (5.81–6.00) | 2.7 (2.7–2.7) | 1.86 |
| `P500_27` | Key generation | 47.1 (40.5–104) | 24.3 (17.3–101) | 20.7 |
| | Signing | 151 (112–239) | 78.4 (59.9–152) | 59.7 |
| | Verification | 15.2 (15.2–15.3) | 6.9 (6.9–7.4) | 4.56 |
| `P664_17` | Key generation | 103 (90.3–140) | 48.4 (41.1–89.1) | 36.5 |
| | Signing | 348 (258–538) | 157 (121–201) | 109 |
| | Verification | 36.1 (36.1–37.0) | 16.6 (16.6–16.8) | 10.6 |

Devices: Nothing Phone (1) (`A063`, Snapdragon 778G+, `arm64-v8a`, build `AQ3A.240929.001`,
Microbenchmark reports `cpuLocked: true`); iPhone 15 (A16, iOS 17.6.1). Commit: the change that added these
benchmarks, on top of `c59c9e8`.

Median signing time is 2.5–3.2× the reference host's mean on the Android phone and 1.3–1.4× on the iPhone. Verification
is stable on every device: the slowest run is less than 8 % above the median. Key generation and signing vary widely
because SQIsign's algorithms are randomized and retry on unsuitable samples; verification, which is deterministic, shows
that the measurement itself is stable.

## CI

The `JVM native (linux-x86_64)` job runs `./gradlew jvmBenchmarkSmokeBenchmark` (one 200 ms iteration, `P324_3` only).
This proves that the benchmarks build and run. Its timings are not compared with the baseline, because shared GitHub
runners are not a controlled environment.

The Android job also runs the Microbenchmarks once each in dry-run mode on the emulator
(`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true`), and the Apple job runs the
XCTest benchmarks on macOS (`swift test --filter SqiSignKitBenchmarks`). Both only check that the benchmarks work.
