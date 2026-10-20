# SQIsign for Kotlin Multiplatform

Kotlin Multiplatform bindings for this repository's SQIsign C implementation (NIST round 3). The cryptography stays in C.
Kotlin adds a typed API, key handling, and the platform bridges:

| Target | Bridge | Native artifact |
|---|---|---|
| `jvm` | JNI | `libsqisign_jni` for the build host, bundled as a jar resource |
| `android` (minSdk 24) | JNI | `libsqisign_jni.so` for `arm64-v8a` and `x86_64`, packaged in the AAR |
| `iosArm64`, `iosSimulatorArm64`, `macosArm64` | cinterop | `libsqisign_kmp_all.a`, embedded in the cinterop klib |

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
otherwise).

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

## Limitations

- The JVM jar contains only the build host's library. Other platforms need a multi-host build, or
  `-Dsqisign.library.path=/abs/path/to/lib` / `java.library.path`.
- Secret keys pass through the managed heap. `close()` wipes this instance's copy, but not copies made by the GC,
  `encoded`, or caller code. Use platform keystores where possible.
- The C implementation's constant-time properties have not been re-audited for these build configurations. Android
  `x86_64` uses the portable `ref` build.
- The Android `x86_64` library is built and packaged but has only been tested on arm64 devices.
- The Apple static library is embedded in the klib, so consumers cannot swap or deduplicate it.
- No Swift-facing framework or XCFramework is produced. Apple apps consume this through their own KMP module.
