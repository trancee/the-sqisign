# Project profile

purpose= C implementation of SQIsign, plus an experimental pure-Kotlin Multiplatform foundation.
languages/toolchains= C11 and ASM with CMake 3.13 or later; independent Kotlin build under `kotlin/`.
supported_platforms= x64 and arm64 are exercised by CI; implementation types are `ref`, `opt`, `broadwell`, and `arm64`. Do not infer support for other targets from local builds.
prerequisites= CMake and a C11-compatible compiler; CI uses Ninja. Constant-time tests additionally require Valgrind and its development header.

## C CI runners

C workflows use GitHub-hosted Ubuntu `24.04`: `ubuntu-24.04` for x64 and
`ubuntu-24.04-arm` for native ARM64 execution. No registered self-hosted runner
or persistent workstation checkout is required. The shared provisioning action
[`.github/actions/setup-c-runner/action.yml`](.github/actions/setup-c-runner/action.yml)
installs build tools and each job's declared Ubuntu packages. Apt package versions
follow Ubuntu's maintained repositories; analyzer/coverage jobs log actual tool versions.
The x86 32-bit jobs additionally install multilib and Clang's 32-bit sanitizer runtime.
Formatting retains the exact clang-format pin from `.pre-commit-config.yaml` in a
fresh per-job virtual environment.

The pqm4 job checks out `SQISign/the-sqisign-pqm4` at
`5dceca0448b3dec6cfa64929bcb89c03a2dd5293`, including pinned submodules,
and installs the ARM bare-metal toolchain, newlib, QEMU, and upstream Python
requirements. Its `mps2-an386` tests/benchmarks execute under emulation, not on
physical Cortex-M4 hardware; emulator cycle counts are not hardware performance evidence.
Shared-host C benchmark timings likewise cannot be compared directly to former
dedicated-host baselines or used as hard regression gates.

## Setup and validation

Configure and build a reference implementation:

```sh
cmake -S . -B build -DSQISIGN_BUILD_TYPE=ref
cmake --build build
ctest --test-dir build --output-on-failure
```

Use `ctest --test-dir build -R '<test-regex>' --output-on-failure` for targeted tests. The default suite includes KATs; CI's main CMake test step excludes tests whose names end in `KAT`.

Formatting is checked with `scripts/format.sh --check` (clang-format 18.1.6, pinned in `.pre-commit-config.yaml`). Static analysis entry points are `scripts/cppcheck.sh` and `scripts/clang-tidy.sh`; coverage is measured with `scripts/coverage.sh`. CI also runs build/test configurations including Release, shared-library, ASan, LSan, UBSan, and dedicated Valgrind constant-time checks. See `.github/workflows/` for the authoritative platform and configuration matrix.

## Generated artifacts

The constants under `src/precomp` are generated from scripts in `scripts/precomp`; regeneration is not required to build. Their generators require the `Theta-SageMath` version of `two-isogenies` and `deuring-2D`. The `scripts/gen_pqm4_sources.sh` script generates sources for the pqm4 integration.

## Kotlin operational profile

Owner: [`kotlin/build.gradle.kts`](kotlin/build.gradle.kts), with repositories in `kotlin/settings.gradle.kts`, the wrapper in `kotlin/gradle/wrapper/`, and CI in [`.github/workflows/kotlin.yml`](.github/workflows/kotlin.yml).

Tuple: Kotlin `2.4.21`, Gradle `9.7.0`, daemon/compiler/test JDK `26`, JVM bytecode `26`, Node.js `26.12.0`; no Android target. CI pins Temurin `26.0.2+101` and Python `3.14.0`. No runtime dependencies beyond Kotlin stdlib; `kotlin-test` is test-only.
Benchmark-only tooling: explicitly accepted Alpha `kotlinx-benchmark` plugin/runtime `0.5.0`, one version owner in `kotlin/gradle/libs.versions.toml`, JMH `1.37`. Runtime is isolated to the custom JVM benchmark compilation and its tests, not the production library. Reassess the Alpha dependency when a compatible stable release is available.

| Module / target | Host / prerequisites | Task from `kotlin/` | Proof / limitations |
| --- | --- | --- | --- |
| `:` / JVM | JDK `26` | `jvmTest jvmCoverageVerification` | Runtime; JaCoCo line/branch coverage of JVM-compiled `commonMain`, both thresholds `100%`, no production exclusions |
| `:` / JVM benchmark | JDK `26`; controlled host for measurements | `jvmBenchmarkSmokeBenchmark`; opt-in `jvmBenchmarkBenchmark` | Linux CI validates 17 primitive/parameter cases and JSON samples; no score gate or baseline; no JS/Native benchmark proof |
| `:` / JS | JDK `26`; plugin downloads Node | `jsNodeTest` | Node runtime; no browser or coverage proof |
| `:` / Linux x64 | x86-64 Linux; downloaded Kotlin/Native toolchain | `linuxX64Test` | Native runtime; no measured coverage |
| `:` / macOS ARM64 | Apple Silicon macOS with Xcode tools | `macosArm64Test` | Native runtime; Linux cannot supply this proof; no measured coverage |
| `:` / all target libraries | JDK `26`; downloaded Kotlin/Native toolchain | `checkKotlinAbi` | Compiled ABI against `kotlin/api/`; inference disabled; not final published-artifact validation |

Fast checks: `./gradlew spotlessCheck detektMainJvm detektBenchmarkJvm detektTestJvm jvmTest` from `kotlin/`. Authoritative CI-equivalent: `./gradlew clean check --no-build-cache --rerun-tasks` on both host runners, followed by `./gradlew jvmBenchmarkSmokeBenchmark` on Linux. `check` also verifies generated fixtures, default detekt rules, strict ABI, and JVM coverage. CI additionally scans Kotlin history/current files with Gitleaks `8.30.1`.

Formatting: Spotless `8.10.4` / ktfmt `0.65`; apply via `./gradlew spotlessApply`. Static analysis: accepted detekt `2.0.0-alpha.6`, including type-aware JVM tasks, no finding baseline. ABI: explicitly accepted Kotlin experimental validator; regenerate reviewed changes with `./gradlew updateKotlinAbi`. Reassess both experimental tools on compatible stable releases.

Generated fixture owner: `python3 tools/generate_test_vectors.py`; output `src/commonTest/kotlin/org/sqisign/TestVectors.kt`; `checkTestVectors` checks exact content from Python hashlib and the parent public KATs. This generated test file is excluded from Spotless/detekt, not from execution. No generated API docs or publication pipeline is configured.

Benchmark owner: `kotlin/src/jvmBenchmark/`, with workload correctness tests in `kotlin/src/jvmTest/`. `kotlin/README.md` defines timed boundaries, workloads, profiles, and comparison controls. CI retains raw smoke reports under `build/reports/benchmarks/smoke/`, logs, and commit/host/runtime metadata in uploaded artifacts; these are executability evidence only. Generated harnesses and benchmark sources are outside production coverage; production exclusions remain empty. JMH's deprecated Unsafe warnings on JDK `26` require revalidation on future JDK upgrades.

Limitations: passing Kotlin gates is not full constitutional compliance. Crypto expert review, constant-time guarantees, numeric performance budgets, and committed controlled-measurement baselines are not established. Retained JVM workloads do not establish performance or portability on other targets. JVM coverage does not imply coverage on JS/Native. C coverage remains measurement-only as documented in `DEVELOPERS.md`; this Kotlin-only change does not alter C gates.
