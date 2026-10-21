# ADR 0002: Test-only deterministic KAT library

- Status: accepted
- Version: 1.0
- Date: 2026-10-21
- Constitution: O3 (crypto), S4 (no security downgrade), S7/T10 (conformance vectors)

## Context

The NIST KAT files (`KAT/PQCsignKAT_*.rsp`) fix public keys, secret keys and signatures for given 48-byte seeds of the
NIST CTR-DRBG. Verifying the KAT signatures only proves verification. Reproducing key generation and signing requires
the C core to draw randomness from that DRBG instead of the operating system. The production library must never accept
a caller-supplied seed: a predictable seed exposes the secret key.

## Decision

1. Build a separate, test-only native library with `-DSQISIGN_KMP_KAT=ON`. It links upstream's
   `sqisign_common_test` (seedable `randombytes_init`) instead of `sqisign_common_sys` (OS randomness). Its only
   export is `sqisign_kmp_kat_keypair_sign(set, seed, message, ...)`: on the JVM, the JNI symbol
   `Java_org_sqisign_SqiSignKatJni_keypairSign`; on Apple platforms, a prelinked archive.
2. The library is built by `buildNativeJvmKat` and `buildNative<Target>Kat`, and is used only by the `drbgTest` source
   set (`DeterministicKatTest`, on JVM, macOS and iOS Simulator). It is never packaged into the jar, AAR, klib or
   XCFramework.
3. The production API has no seed or RNG parameter.
4. `-Psqisign.katTests=false` skips the KAT build where the native toolchain for it is unavailable (the CI job that
   tests the all-platform jar).

## Alternatives

- Injectable RNG or seed in the public API: rejected. A security downgrade reachable from production code (S4).
- Verify-only KATs: rejected as the only check. They don't detect key-generation or signing regressions in the bridge.
- Overriding `randombytes` through weak symbols in the production library: rejected. The override could be active in
  shipped binaries.

## Risks

- The KAT library is built from the same sources and flags as production, apart from the RNG. A divergence in build
  flags could hide a production-only defect. The production library is still covered by the round-trip and
  KAT-verification tests.
- Android device tests don't run `drbgTest`, so seeded reproduction is not proven on Android ABIs.

## Migration

Initial decision; no prior design.
