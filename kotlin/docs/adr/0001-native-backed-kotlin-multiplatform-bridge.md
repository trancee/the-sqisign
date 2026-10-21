# ADR 0001: Native-backed Kotlin Multiplatform bridge

- Status: accepted
- Version: 1.0
- Date: 2026-10-21
- Constitution: O3 (crypto), S6 (approved implementation), C2/C4 (platform adapters, error model)

## Context

Android, iOS, macOS and JVM applications need SQIsign key generation, signing and verification from Kotlin.
SQIsign needs multi-hundred-bit field arithmetic, quaternion ideal arithmetic, isogeny computations and lattice
reduction. The only maintained implementation is the C reference and optimized code in this repository (NIST round 3),
which is reviewed upstream and checked against the NIST KAT files in `KAT/`.

## Decision

1. Keep all cryptography in the upstream C code, unmodified (vendor scope in [`AGENTS.md`](../../../AGENTS.md)).
   Kotlin provides only a typed API (`SqiSign`, `ParameterSet`, key classes), key-size validation and platform bridges.
2. Add a small C glue layer, `kotlin/native/`, which exposes four functions (`sizes`, `keypair`, `sign`, `verify`)
   selected by parameter-set ID. All three parameter sets are linked into one binary; upstream symbols are namespaced
   per set and hidden.
3. Platform bridges:
   - JVM and Android: JNI (`libsqisign_jni`). The JVM jar bundles one library per OS/architecture as a resource and
     loads it from a temporary file. The `sqisign.library.path` system property overrides it.
   - iOS and macOS: cinterop against a static archive embedded in the klib, plus a static `SqiSign.xcframework` for Swift.
4. Upstream build type per target: `arm64` on aarch64 (JVM, Android `arm64-v8a`, Apple). `ref` on x86_64 (JVM,
   Android `x86_64`), because the `broadwell` build needs ADX/BMI2, which not every x86_64 CPU or emulator has.
5. Error model: native status codes are mapped to `SqiSignException`. Invalid keys and sizes raise
   `IllegalArgumentException`, use of a closed secret key raises `IllegalStateException`, and a failed verification
   returns `false`. No platform error type reaches the shared API.

## Alternatives

- Pure Kotlin implementation: rejected. Many person-months of work, 10–100× slower, practically impossible to keep
  constant-time, and a new cryptographic implementation would need expert review (S6).
- JNA or the Panama FFM API on the JVM: rejected. JNA adds a runtime dependency and reflection overhead; FFM needs
  JDK 22 and is unavailable on Android.
- One library per parameter set: rejected. It multiplies artifacts and loader logic for about 3× less native code.
- `broadwell` build on all x86_64 targets: rejected, because it crashes on CPUs and emulators without ADX/BMI2.

## Risks

- Memory safety and side channels are those of the upstream C code. The `ref` build is not optimized and may have
  different timing behavior from the `arm64` build.
- Secret-key copies on managed heaps (GC moves, JNI array copies) cannot be wiped reliably; `SqiSignSecretKey.close()`
  wipes only its own buffer. This is documented in the KDoc.
- The JVM loader writes the bundled library to a temporary directory. A local attacker who can write there before
  `System.load` could replace it.
- Upstream changes to the NIST API or parameter sets require updates to the glue code and the ABI dumps.

## Migration

Initial decision; no prior design. Upstream updates are pulled into the vendor tree. The native and Kotlin tests
(including the NIST KAT signatures and seeded keygen/sign vectors) must pass before release.
