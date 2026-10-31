# AGENTS: Kotlin Multiplatform layer

Refines the root [`AGENTS.md`](../AGENTS.md) for owned paths. [`CONSTITUTION.md`](../CONSTITUTION.md) wins on conflict.
Notation R/X/D/O as defined there.

## PROFILE

- Product: KMP bindings for the vendor SQIsign C core (NIST round 3); cryptography stays in C. Design:
  [ADR 0001](docs/adr/0001-native-backed-kotlin-multiplatform-bridge.md), KAT seam:
  [ADR 0002](docs/adr/0002-deterministic-kat-test-library.md).
- Toolchain: pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) + Gradle wrapper; JDK 21; Android SDK
  36, NDK, CMake >= 3.13 (or `$ANDROID_HOME/cmake/<version>/bin` on PATH); Xcode for Apple targets (macOS hosts only).
- Targets: `jvm` (JNI; Linux/macOS/Windows), `android` (JNI; `arm64-v8a`, `x86_64`), `iosArm64`, `iosSimulatorArm64`,
  `macosArm64` (cinterop + static XCFramework). Table + non-goals: [`README.md#supported-platforms`](README.md#supported-platforms).
- Layout:
  - `src/commonMain`: public API (`SqiSign`, `ParameterSet`, keys); `expect fun native*`.
  - `src/jvmMain`, `src/androidMain`, `src/appleMain`: platform adapters only (C2).
  - `src/commonTest`: behavior suite, also compiled into Android device tests; `src/katTest`: seeded KAT test (JVM,
    Apple, Android device); `src/drbgTest`: its `expect` (JVM+Apple); `src/androidDeviceTest`: Android KAT entry point;
    `src/jvmTest`: JVM-only adapter tests; `src/appleTest`: Apple KAT cinterop actual.
  - `src/jvmBenchmark`: JMH benchmarks; `benchmark-android/`: AndroidX Microbenchmarks; `benchmarks/`: baseline,
    budgets, comparison script, device results.
  - `native/`: C glue + `CMakeLists.txt` (owned; vendor C in `../src`, `../include` X edit).
  - `api/`: ABI dumps; `docs/adr/`: ADRs; `swift/`: SwiftPM `SqiSignKit` layer, tests and benchmarks (ADR 0003).
- Generated (E6, X hand-edit): `KatVectors.kt` (`generateKatVectors` from `../KAT/*.rsp`), `api/*` (`updateKotlinAbi`).

## SETUP

Run from `kotlin/`. R `JAVA_HOME` = JDK 21. Gradle invokes CMake for every native task; a missing toolchain is an
external prerequisite, X stub it.

## STEPS (Kotlin change)

1. Classify: public API | platform adapter | native glue | build/CI | docs. Public API or crypto/protocol/native
   boundary => read ADRs; material decision => new/updated ADR in `docs/adr/` (O3).
2. Red: add/adjust behavior test first (T1). Placement: shared behavior => `commonTest`; JVM loader/adapter =>
   `jvmTest`; deterministic keygen/sign => `katTest`. Tests R Arrange/Act/Assert (T5); fail-closed branch => test (S5).
3. Implement smallest complete change; shared rules in `commonMain`, platform code in adapters (C2); native status codes
   => `SqiSignException`, never platform errors (C4). X secret bytes in logs/`toString`/exceptions (S1).
4. Green + gates (VERIFY below). Public API diff => `./gradlew updateKotlinAbi` on macOS (other hosts lack Apple klib
   targets), review `api/` diff, KDoc + README/`Module.md` examples for every platform (C8), semver impact (Q5/Q6).
5. Budgeted path (`src/*Main`, `native/`, vendor C, build flags) => benchmark comparison (P3).
6. Docs: README (how-to/reference), `Module.md` (API landing page), ADRs (decisions) agree with code (O1/O2).
7. Commit/push/PR only with explicit user approval (root GIT/EXTERNAL).

## VERIFY

| Gate | Command (from `kotlin/`) | CI job |
|---|---|---|
| Format | `./gradlew spotlessCheck` (fix: `spotlessApply`) | Apple |
| Static analysis | `./gradlew detekt` | Apple |
| ABI | `./gradlew checkKotlinAbi` | Apple |
| API docs | `./gradlew dokkaGenerate` (fails on warnings/undocumented) | Apple |
| JVM tests + coverage | `./gradlew jvmTest koverVerify` (100 % line+branch, commonMain+jvmMain) | JVM native (5 hosts) |
| C glue coverage | `scripts/native-coverage.sh` (Clang; all functions, >= 98 % lines of `native/src`) | Apple |
| Apple tests | `./gradlew macosArm64Test iosSimulatorArm64Test compileKotlinIosArm64` | Apple |
| XCFramework + Swift | `./gradlew assembleSqiSignReleaseXCFramework`, then in `swift/`: `./check-min-versions.sh ../build/XCFrameworks/release/SqiSign.xcframework Package.swift` and `swift test --skip SqiSignKitBenchmarks`; iOS: `xcodegen generate` + `xcodebuild test -scheme SqiSignDeviceTests` in `swift/DeviceHost` (Simulator; SwiftPM on iOS skips Keychain) | Apple |
| Android | `./gradlew bundleAndroidMainAar connectedAndroidDeviceTest` (device/emulator; includes seeded KATs); other ABI: `scripts/android-device-test-abi.sh <abi>` | Android (x86_64 emulator + arm64 translated) |
| iPhone (manual, T9) | `xcodegen generate` + `xcodebuild test` in `swift/DeviceHost` ([README](README.md#tests)) | none |
| All-platform jar | `./gradlew jvmTest jvmJar -Psqisign.jvmNativeLibs=<dir> -Psqisign.katTests=false` | JVM jar |
| Rust backend (ADR 0004) | `./gradlew jvmTest macosArm64Test iosSimulatorArm64Test assembleSqiSignReleaseXCFramework -Psqisign.backend=rust`, then `swift test --skip SqiSignKitBenchmarks` in `swift/` (rustup + targets, [`../rust/README.md`](../rust/README.md#use-it-from-the-kotlin-project)) | Rust backend (Linux `x86_64` + AAR build, Linux `aarch64`, macOS `aarch64` + Swift, macOS `x86_64`, Windows) |
| Benchmark smoke | `./gradlew jvmBenchmarkSmokeBenchmark` | JVM native (linux-x86_64) |
| Benchmark budget | [`benchmarks/README.md`](benchmarks/README.md) | manual, reference host |
| Device benchmarks (informational) | `:benchmark-android:connectedReleaseAndroidTest`; scheme `SqiSignDeviceBenchmarks` ([benchmarks](benchmarks/README.md#device-benchmarks)) | dry run / macOS smoke |
| Secret scan | `gitleaks git --redact --exit-code 1 ..` | Secret scan |
| Release assets (ADR 0005) | `./gradlew publishAllPublicationsToReleaseRepository assembleSqiSignReleaseXCFramework [-Psqisign.backend=rust]` -> `build/release-repo`; `swift/release-manifest.sh` -> tag's root `Package.swift`; release = manual run on `main` with `release`, new tag `v<version>` | Release (PR/manual dry run builds `SqiSignKit` from the manifest) |

`./gradlew check` aggregates spotless, detekt, ABI, Kover and host tests. Local hooks != CI proof (E7); CI workflow:
[`../.github/workflows/kotlin.yml`](../.github/workflows/kotlin.yml).

Coverage not measured (Kover = JVM bytecode only): `androidMain`, `appleMain`, `native/` C glue; covered behaviorally by
Android device and Apple tests. Kover excludes only the jvm `benchmark` compilation (named in CI). X add other Kover
exclusions except CI-named (T3).

## CONVENTIONS

- Kotlin type names `SqiSign*`; C/library/artifact names keep upstream `sqisign`. JNI class names are part of exported
  symbols: rename => update `native/src/*_jni.c` symbols in the same change.
- `explicitApi()`: public declarations R explicit visibility + types (Q4) + KDoc (Dokka `reportUndocumented`).
- ktlint `intellij_idea` style, max line 120 ([`.editorconfig`](.editorconfig)); detekt config [`detekt.yml`](detekt.yml);
  X suppression in production code (Q2).
- `"Apple"` (`appleMain`, CI job) = iOS + macOS per Kotlin default hierarchy.
- Commits: Conventional Commits, scope `kotlin` (e.g. `feat(kotlin): …`, `fix(kotlin): …`, `ci(kotlin): …`,
  `docs(kotlin): …`), trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`.
- PR body R Constitution Check S-Q-T-C-P-D-O-E-G (G5) with evidence or N/A reason per letter.
