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
| Android 7.0+ (API 24) | `android` | `arm64-v8a`, `x86_64` | CI: `x86_64` emulator; locally: `arm64-v8a` emulator |
| iOS 14+ devices | `iosArm64` | `arm64` | locally: Swift tests on an iPhone 15 (iOS 17.6.1) |
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
(`ios-arm64`, `ios-arm64-simulator`, `macos-arm64`). Failures surface as Swift errors:

```swift
import SqiSign

let keyPair = try SqiSign.shared.generateKeyPair(parameterSet: .p3243)
let signature = try SqiSign.shared.sign(message: message, secretKey: keyPair.secretKey) // KotlinByteArray
let valid = SqiSign.shared.verify(message: message, signature: signature, publicKey: keyPair.publicKey)
```

`swift/` is a SwiftPM package that tests the framework from Swift; `swift/Tests/SqiSignTests/SqiSignTests.swift` also
shows `Data` ⇄ `KotlinByteArray` conversion.

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

Kover measures JVM bytecode only. `androidMain`, `appleMain` and the C glue in `native/` are not measured, but the
Android and Apple jobs test them. Two more checks run outside `check`:
- **Benchmarks:** JMH benchmarks are in `src/jvmBenchmark`. Budgets, the baseline and the comparison script are in
  [`benchmarks/README.md`](benchmarks/README.md).
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
  failure codes to `SqiSignException`.

`jvmTest` adds the JVM loader: platform directory names, the `sqisign.library.path` override and the fallback to
`java.library.path`.

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
tests on macOS and the iOS Simulator.

To run the Swift tests locally (after `assembleSqiSignReleaseXCFramework`):

```sh
cd swift
swift test                                                          # macOS
xcodebuild test -scheme SqiSignSwiftTests-Package -destination "platform=iOS Simulator,name=iPhone 17"
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

On first launch, trust the developer certificate on the iPhone (Settings → General → VPN & Device Management).

## Limitations

- A locally built JVM jar contains only the build host's library (see above for the multi-platform jar). The
  `-Dsqisign.library.path=/abs/path/to/lib` system property and `java.library.path` also work.
- The Linux JNI libraries link glibc dynamically (musl is not supported). The CI log prints the highest `GLIBC_*`
  symbol version they need.
- Secret keys pass through the managed heap. `close()` wipes this instance's copy, but not copies made by the GC,
  `encoded`, or caller code. Use platform keystores where possible.
- The C implementation's constant-time properties have not been re-audited for these build configurations. Android
  `x86_64` uses the portable `ref` build.
- Android `x86_64` is tested only on the CI emulator. Physical-device runs (iPhone) are manual, not part of CI.
- The Apple static library is embedded in the klib, so consumers cannot swap or deduplicate it.
- Swift sees `KotlinByteArray` rather than `Data`, and Kotlin default arguments are not exported, so Swift callers
  always pass a parameter set.
