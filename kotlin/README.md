# SQIsign for Kotlin Multiplatform

Kotlin Multiplatform bindings for this repository's SQIsign C implementation (NIST round 3). The cryptography stays in C.
Kotlin adds a typed API, key handling, and the platform bridges:

| Target | Bridge | Native artifact |
|---|---|---|
| `jvm` | JNI | `libsqisign_jni` per OS/architecture, bundled as jar resources (CI bundles all five platforms) |
| `android` (minSdk 24) | JNI | `libsqisign_jni.so` for `arm64-v8a` and `x86_64`, packaged in the AAR |
| `iosArm64`, `iosSimulatorArm64`, `macosArm64` | cinterop | `libsqisign_kmp_all.a`, embedded in the cinterop klib and in the static `SqiSign.xcframework` |

All three parameter sets (`P324_3`, `P500_27`, `P664_17`; NIST levels 1, 3 and 5) are linked into one binary.

### Supported platforms

| Platform | Kotlin target | Architectures | Tested |
|---|---|---|---|
| Linux, macOS, Windows (JVM 21) | `jvm` | Linux `x86_64`, `aarch64`; macOS `x86_64`, `aarch64`; Windows `x86_64` | CI, every platform |
| Android 7.0+ (API 24) | `android` | `arm64-v8a`, `x86_64` | CI: `x86_64` emulator, plus the `arm64-v8a` libraries through its ARM translation; locally: Nothing Phone (1) (`arm64-v8a`, Android 15) and an `arm64-v8a` emulator |
| iOS 14+ devices | `iosArm64` | `arm64` | locally: Swift tests on an iPhone 15 (iOS 17.6.1), iPhone X (iOS 16.7.16) and iPhone SE (2016, iOS 15.8.8) |
| iOS Simulator (Apple Silicon) | `iosSimulatorArm64` | `arm64` | CI: Kotlin and Swift tests |
| macOS 11+ (Apple Silicon) | `macosArm64` | `arm64` | CI: Kotlin and Swift tests |

"Apple" in source-set and CI names (`appleMain`, `appleTest`, the `Apple` CI job) means all three iOS and macOS
targets, following Kotlin's default source-set hierarchy. It is not iOS only.

Not supported:

- Kotlin/Native for Linux and Windows (`linuxX64`, `linuxArm64`, `mingwX64`). Linux and Windows are supported through
  the `jvm` target.
- Intel Apple targets (`iosX64`, `macosX64`). Intel Macs are supported through the `jvm` target.
- 32-bit Android ABIs (`armeabi-v7a`, `x86`).
- JS and Wasm. These would need the C core compiled to WebAssembly.

## Usage

```kotlin
val keyPair = SqiSign.generateKeyPair(ParameterSet.P324_3)
val signature = SqiSign.sign(message, keyPair.secretKey)
check(SqiSign.verify(message, signature, keyPair.publicKey))
keyPair.secretKey.close() // overwrites the key bytes held by this instance
```

Keys round-trip through `encoded` and the `SqiSignPublicKey` / `SqiSignSecretKey` constructors. `verify` returns `false`
for any invalid input of the right types. `generateKeyPair` and `sign` throw `SqiSignException` if the C core fails.

Every call blocks and is thread-safe. Keygen and signing take tens to hundreds of milliseconds, so keep them off the
main thread.

### Swift

`./gradlew assembleSqiSignReleaseXCFramework` writes a static `build/XCFrameworks/release/SqiSign.xcframework`
(`ios-arm64`, `ios-arm64-simulator`, `macos-arm64`). The SwiftPM package in `swift/` adds `SqiSignKit`, a Swift layer
over the framework with `Data`, `async`/`await` and CryptoKit-style names
([ADR 0003](docs/adr/0003-sqisignkit-swift-layer.md)). Failures surface as Swift errors:

```swift
import SqiSignKit

let keyPair = try await SqiSignKeyPair.generate(.level1)
let signature = try await keyPair.secretKey.signature(for: message)   // Data
let valid = keyPair.publicKey.isValidSignature(signature, for: message)
let publicKey = try SqiSignPublicKey(parameterSet: .level1, rawRepresentation: keyPair.publicKey.rawRepresentation)
```

Each operation also has a synchronous variant, which blocks the calling thread. The `async` variants run on a detached
task. They check cancellation before starting but cannot interrupt the native call. Don't close a secret key while it
is signing. `SqiSignKit` re-exports `SqiSign`, so the Kotlin-generated API (`SqiSign.shared`, `KotlinByteArray`)
remains available.

## Install

Releases are [GitHub Releases](https://github.com/trancee/the-sqisign/releases)
([ADR 0005](docs/adr/0005-github-release-distribution.md)); nothing is on Maven Central. Each carries, for the C
backend (`sqisign`) and the opt-in Rust backend (`sqisign-rust`), a zipped Maven repository and a zipped
`SqiSign.xcframework` with its SwiftPM checksum.

Unzip `sqisign-<version>-maven.zip` (into `sqisign-repo/` here) and declare it as a repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("sqisign-repo") }
        mavenCentral()
    }
}

// build.gradle.kts, in a Kotlin Multiplatform commonMain or a JVM/Android project
dependencies {
    implementation("io.github.trancee:sqisign:0.1.0") // or io.github.trancee:sqisign-rust:0.1.0
}
```

Gradle picks the JVM, Android or Apple artifact from the module metadata. In Swift, point a binary target at the
XCFramework zip:

```swift
.binaryTarget(
    name: "SqiSign",
    url: "https://github.com/trancee/the-sqisign/releases/download/v0.1.0/sqisign-0.1.0.xcframework.zip",
    checksum: "<contents of sqisign-0.1.0.xcframework.zip.checksum>")
```

## Build

Requirements: JDK 21 and the Android SDK with NDK `sqisign.ndkVersion` and CMake `sqisign.cmakeVersion` (see
`gradle.properties`). Apple targets also need macOS with Xcode.

```sh
./gradlew jvmTest macosArm64Test iosSimulatorArm64Test   # host tests
./gradlew bundleAndroidMainAar                            # AAR with both ABIs
./gradlew connectedAndroidDeviceTest                      # common tests on a device/emulator
```

Each `buildNative*` task configures `native/CMakeLists.txt` with CMake. That file wraps the repository's CMake build.
The CMake used is, in order of preference: `sqisign.cmakeBinDir`, the Android SDK's CMake (with Ninja), then `cmake`
from `PATH`. On the host, `sqisign.hostBuildType` overrides the optimized build (`arm64` on aarch64 hosts, `ref`
otherwise). `sqisign.hostCMakeArgs` passes extra CMake arguments to the host build.

### Agent skills

[`skills-lock.json`](../skills-lock.json) pins the coding-agent skills used in this repository (source and hash). The
installed copies under `.agents/skills/` are not committed. After cloning, from the repository root and with Node.js:

```sh
npx skills experimental_install          # restore every skill in skills-lock.json
npx skills add trancee/skills --skill X  # add skill X; commit the updated skills-lock.json
npx skills update                        # update installed skills to their latest versions
```

### Rust backend (opt-in, unreviewed)

`-Psqisign.backend=rust` builds every native library from the Rust port in `../rust` instead of the vendor C code
([ADR 0004](docs/adr/0004-rust-port-opt-in-backend.md)); `c` is the default. Releases ship both, the Rust build under
separate coordinates (`sqisign-rust`, [Install](#install)). The Kotlin
API, the JNI and cinterop glue and the tests are the same. It needs rustup and, for cross builds, the Rust targets
listed in [`../rust/README.md`](../rust/README.md#use-it-from-the-kotlin-project).

### JVM jar for several platforms

The jar loads `/org/sqisign/native/<os>-<arch>/` (`linux`, `macos`, `windows` × `x86_64`, `aarch64`) for the running
JVM. A local build bundles only the host's library. To bundle prebuilt libraries instead, point
`sqisign.jvmNativeLibs` at a directory with that layout:

```sh
./gradlew jvmJar -Psqisign.jvmNativeLibs=build/jvm-natives
```

The `Kotlin` workflow (`.github/workflows/kotlin.yml`) builds each library on its own runner, then tests and uploads the
combined jar. MSVC cannot compile the C core (`__int128`), so Windows uses MinGW clang (MSYS2 `CLANG64`), passed with
`-Psqisign.hostCMakeArgs="-G Ninja -DCMAKE_C_COMPILER=clang -DCMAKE_ASM_COMPILER=clang"`.

### Native layer

- `native/include/sqisign_kmp.h`: a stable C API whose function names do not depend on the build type.
- Every variant is compiled with `SQISIGN_NAMESPACE_GENERIC_AS_VARIANT`. Without it, the level-dependent generic
  symbols (`sqisign_gen_*`) collide when several variants are linked into one binary.
- The JNI library exports only the four `Java_org_sqisign_SqiSignJni_*` symbols.

### API and code checks

`./gradlew check` runs these besides the tests:

| Check | Task | Configuration |
|---|---|---|
| Binary compatibility (KGP built-in ABI validation) | `checkKotlinAbi`; accept a reviewed change with `updateKotlinAbi` | dumps in `api/` (JVM, Android, Apple klib) |
| Formatting (Spotless 8.10.4 + ktlint 1.8.0, `intellij_idea` style) | `spotlessCheck`; fix with `spotlessApply` | `.editorconfig` |
| Static analysis (detekt 1.23.8, default rules) | `detekt` | `detekt.yml` |
| Coverage (Kover 0.9.11): 100 % lines and branches of `commonMain` + `jvmMain` | `koverVerify`; report with `koverHtmlReport` | `kover {}` in `build.gradle.kts` |

Run `updateKotlinAbi` on macOS: other hosts cannot compile the Apple targets and only infer their part of
`api/sqisign-kotlin.klib.api`. CI checks all of the above, plus `dokkaGenerate`, on macOS.

API reference: `./gradlew dokkaGenerate` writes HTML to `build/dokka/html` (Dokka 2.2.0, module page from `Module.md`).
Undocumented public declarations fail the build. CI uploads the HTML as the `api-docs` artifact.

Kover measures JVM bytecode only. `androidMain` and `appleMain` are not measured, but the Android and Apple jobs test
them. Three more checks run outside `check`:
- **C glue coverage:** `scripts/native-coverage.sh` builds the host JNI libraries with Clang source-based coverage
  (`-Psqisign.nativeCoverage=true`, CMake `SQISIGN_KMP_COVERAGE`), runs `jvmTest`, and reports `native/src/*.c` with
  `llvm-cov` in `build/native-coverage/`. It requires every function to run and at least 98 % of lines; the missed
  lines are `malloc` and upstream signing failures that tests cannot trigger. The Apple CI job runs it and uploads
  the `native-coverage` artifact. The upstream C library is not instrumented.
- **Benchmarks:** JMH benchmarks are in `src/jvmBenchmark`, AndroidX Microbenchmarks in `benchmark-android/`, and
  XCTest benchmarks in `swift/Tests/SqiSignKitBenchmarks`. Budgets, the baseline, the comparison script and device
  results are in [`benchmarks/README.md`](benchmarks/README.md).
- **Secret scan:** the `Secret scan` workflow runs gitleaks over the full git history on every push.

Design decisions are recorded in [`docs/adr/`](docs/adr/). Contributor and agent workflow: [`AGENTS.md`](AGENTS.md).

## Tests

`commonTest` runs on every target:
- sizes match the C core;
- keygen/sign/verify round trips for every parameter set;
- tampered messages, signatures and keys are rejected;
- empty messages work;
- key encodings round-trip, and closing a secret key wipes it;
- the first three NIST KAT signatures per set verify (generated from `../KAT/*.rsp` by `generateKatVectors`).
- key equality, `toString` redaction, key pairs whose keys use different parameter sets, and the mapping of native
  failure codes to `SqiSignException`;
- the native bridge rejects an unknown parameter set (`NativeBridgeTest`, which calls the bridge directly).

`jvmTest` adds the JVM loader: platform directory names, the `sqisign.library.path` override and the fallback to
`java.library.path`; and the KAT JNI bridge's seed-length check.

The seeded KAT test (`src/katTest`) reproduces the same KATs from their seeds on JVM, Apple and Android device tests:
public key, secret key and signature must match byte for byte. It links a test-only native build
(`-DSQISIGN_KMP_KAT=ON`) that replaces the system RNG with the NIST CTR-DRBG, seeded through
`sqisign_kmp_kat_keypair_sign` (`native/include/sqisign_kmp_kat.h`). JVM and Apple implement the `drbgTest` entry point:

- JVM: `buildNativeJvmKat` builds `sqisign_kat_jni`, a separate library exporting only
  `Java_org_sqisign_SqiSignKatJni_keypairSign`, loaded from the `jvmTest` resources (`org/sqisign/katnative/`).
- Apple: `buildNative<Target>Kat` builds `libsqisign_kmp_kat.a`, prelinked so that it exports only
  `sqisign_kmp_kat_keypair_sign`. It is linked into the test binaries through the `sqisignKat` test cinterop.
- Android: `buildNativeAndroid<Abi>Kat` builds `libsqisign_kat_jni.so` per ABI, packaged only into the device-test APK.

None of them ends up in the published jar, AAR, klibs or XCFramework.
`-Psqisign.katTests=false` skips them, for example when testing prebuilt libraries on a host without a C toolchain.

CI runs these on the five JVM platforms, an `x86_64` Android emulator, macOS and the iOS Simulator, plus the Swift
tests on macOS and the iOS Simulator. GitHub-hosted runners cannot run an accelerated `arm64-v8a` emulator, so the
Android job also installs the device-test APK with its `arm64-v8a` libraries (`scripts/android-device-test-abi.sh
arm64-v8a`) and runs them through the `x86_64` image's ARM translation. That checks the optimized `arm64` build for
correctness, not speed. The emulator job runs once per backend, `c` and `rust`.

To run the Swift tests locally (after `assembleSqiSignReleaseXCFramework`):

```sh
cd swift
swift test --skip SqiSignKitBenchmarks                              # macOS
xcodebuild test -scheme SqiSignKit -destination "platform=iOS Simulator,name=iPhone 17" \
  -skip-testing:SqiSignKitBenchmarks
```

SwiftPM cannot run package tests on a physical iPhone, so `swift/DeviceHost` generates a small host app with
[XcodeGen](https://github.com/yonaskolb/XcodeGen) that runs the same tests. Connect the iPhone (unlocked, Developer Mode
on), find its id with `xcrun xctrace list devices`, and sign with your team (Xcode → Settings → Accounts):

```sh
cd swift/DeviceHost
xcodegen generate
xcodebuild test -project SqiSignDeviceHost.xcodeproj -scheme SqiSignDeviceTests -destination "id=<udid>" \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<team-id>
```

On first launch, trust the developer certificate on the iPhone (Settings → General → VPN & Device Management). The
iOS 15 and 16 phones above ran with Xcode 16.4; when Xcode reports such a phone as not connected although it is
paired, `xcrun xcdevice list --timeout=15` waits long enough to find it. The
`SqiSignDeviceBenchmarks` scheme runs the device benchmarks; see [`benchmarks/README.md`](benchmarks/README.md).

## Limitations

- A locally built JVM jar contains only the build host's library (see above for the multi-platform jar). The
  `-Dsqisign.library.path=/abs/path/to/lib` system property and `java.library.path` also work.
- The Linux JNI libraries link glibc dynamically (musl is not supported). The CI log prints the highest `GLIBC_*`
  symbol version they need.
- Secret keys pass through the managed heap. `close()` wipes this instance's copy, but not copies made by the GC,
  `encoded`, or caller code. Use platform keystores where possible.
- The C implementation's constant-time properties have not been re-audited for these build configurations. Android
  `x86_64` uses the portable `ref` build.
- Physical-device runs (iPhone, Android phone) are manual, not part of CI. Android `x86_64` is tested only on the CI
  emulator.
- The Apple static library is embedded in the klib, so consumers cannot swap or deduplicate it.
- Without `SqiSignKit`, Swift sees `KotlinByteArray` rather than `Data`, and Kotlin default arguments are not
  exported. `SqiSignKit`'s binary target points at the local build output; distributing it needs a released
  XCFramework URL and checksum.
- `Data` copies of secret keys (`rawRepresentation`) cannot be reliably wiped.
