# SQIsign for Kotlin Multiplatform

Kotlin Multiplatform bindings for this repository's SQIsign C implementation (NIST round 3). The cryptography stays in C.
Kotlin adds a typed API, key handling, and the platform bridges:

| Target | Bridge | Native artifact |
|---|---|---|
| `jvm` | JNI | `libsqisign_jni` per OS/architecture, bundled as jar resources (CI bundles all five platforms) |
| `android` (minSdk 24) | JNI | `libsqisign_jni.so` for `arm64-v8a` and `x86_64`, packaged in the AAR |
| `iosArm64`, `iosSimulatorArm64`, `macosArm64` | cinterop | `libsqisign_kmp_all.a`, embedded in the cinterop klib and in the static `SqiSign.xcframework` |

All three parameter sets (`P324_3`, `P500_27`, `P664_17`; NIST levels 1, 3 and 5) are linked into one binary.

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
- The JNI library exports only the four `Java_org_sqisign_SqisignJni_*` symbols.

## Tests

`commonTest` runs on every target:
- sizes match the C core;
- keygen/sign/verify round trips for every parameter set;
- tampered messages, signatures and keys are rejected;
- empty messages work;
- key encodings round-trip, and closing a secret key wipes it;
- the first three NIST KAT signatures per set verify (generated from `../KAT/*.rsp` by `generateKatVectors`).

Keygen and signing KATs are not reproduced, because they need the NIST DRBG build.

CI runs these on the five JVM platforms, an `x86_64` Android emulator, macOS and the iOS Simulator, plus the Swift
tests on macOS and the iOS Simulator.

To run the Swift tests locally (after `assembleSqiSignReleaseXCFramework`):

```sh
cd swift
swift test                                                          # macOS
xcodebuild test -scheme SqiSignSwiftTests-Package -destination "platform=iOS Simulator,name=iPhone 17"
```

On a physical iPhone (connected, unlocked, Developer Mode on), find its id with `xcrun xctrace list devices` and sign
with your team:

```sh
xcodebuild test -scheme SqiSignSwiftTests-Package -destination "id=<udid>" \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<team-id>
```

## Limitations

- A locally built JVM jar contains only the build host's library (see above for the multi-platform jar). The
  `-Dsqisign.library.path=/abs/path/to/lib` system property and `java.library.path` also work.
- The Linux JNI libraries link glibc dynamically (musl is not supported). The CI log prints the highest `GLIBC_*`
  symbol version they need.
- Secret keys pass through the managed heap. `close()` wipes this instance's copy, but not copies made by the GC,
  `encoded`, or caller code. Use platform keystores where possible.
- The C implementation's constant-time properties have not been re-audited for these build configurations. Android
  `x86_64` uses the portable `ref` build.
- Android `x86_64` is tested only on the CI emulator. The Swift tests have not yet run on a physical iPhone.
- The Apple static library is embedded in the klib, so consumers cannot swap or deduplicate it.
- Swift sees `KotlinByteArray` rather than `Data`, and Kotlin default arguments are not exported, so Swift callers
  always pass a parameter set.
