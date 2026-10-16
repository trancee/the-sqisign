# SQIsign Kotlin foundation

**Experimental foundation, not a signature implementation.** There is no key
generation, signing, or signature verification yet. Do not use this project to
protect real keys or authenticate messages. Parsing a signature does not verify it.

This independent Kotlin Multiplatform build targets the
[SQIsign v3.0 specification, September 1, 2026](https://sqisign.org/spec/sqisign-20260901.pdf).
All library code is in `commonMain`, with no Java APIs, JNI, C interop, native
cryptographic providers, or external runtime libraries beyond the Kotlin standard
library. The existing C build is unchanged.

## Implemented

| Component | Scope |
| --- | --- |
| `ParameterSet` | All three v3 parameter sets, primes, and encoded sizes |
| `Shake256` | Portable Keccak-f[1600], incremental absorption and squeezing, FIPS 202 padding |
| `SqisignPrng` | 48-byte seed expansion using `key`, `com`, `res`, and `def` domains |
| `WireFormat` | Exact public-key/signature lengths, canonical field encodings, response scalar bit bounds, defensive byte copies |
| `ChallengeHash` | Exact v3 Fiat-Shamir transcript using the commitment's Montgomery coefficient |

The wire types expose byte encodings, not mathematical field/curve objects.
They do not check curve validity, supersingularity, basis-hint validity, matrix
normalization/invertibility, or any signature equation. Secret-key parsing is
deliberately deferred until the ideal and integer layers exist.

```kotlin
import org.sqisign.Shake256

val output = Shake256.digest("abc".encodeToByteArray(), outputLength = 64)
```

## Toolchain and execution

Stable versions verified on **2026-10-16**, pinned rather than dynamically resolved:

| Tool | Version | Source |
| --- | --- | --- |
| Kotlin Multiplatform and `kotlin-test` | `2.4.21` | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| Gradle wrapper | `9.7.0` | [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) |
| JDK / JVM bytecode | `26` / `26` | [Kotlin JVM targets](https://kotlinlang.org/docs/gradle-compiler-options.html) |
| Node.js, downloaded by Kotlin's Gradle plugin | `26.12.0` | [Node releases](https://nodejs.org/dist/index.json) |

Gradle `9.7.0` is within JetBrains' fully supported range for Kotlin `2.4.21`.
The build/test JDK and Kotlin JVM bytecode target are both `26`.
The JS target uses ES2015, supported by the selected compiler, with portable
Kotlin `Long` arithmetic rather than requiring JavaScript `BigInt`.
Build-time test-runner dependencies are managed by the Kotlin plugin; the JS
lockfile pins their resolved tree. There are no separately chosen application
dependencies.

Install JDK `26` and select it with `JAVA_HOME`. From this directory:

```sh
./gradlew jvmTest jsNodeTest
./gradlew macosArm64Test     # Apple Silicon macOS
./gradlew linuxX64Test       # x86-64 Linux
```

## Quality and compatibility checks

```sh
./gradlew spotlessApply     # Format owned Kotlin sources and Gradle scripts
./gradlew check             # Tests, formatting, static analysis, ABI, fixtures, coverage
./gradlew clean check --no-build-cache --rerun-tasks  # CI-equivalent, on each native host
```

[Kotlin CI](../.github/workflows/kotlin.yml) runs on pushes and pull requests,
using x86-64 Linux and Apple Silicon macOS. Each host runs JVM and Node.js tests
and its own Native tests; an explicit result check rejects a missing Native run.
It also scans Kotlin history and current files with redacted Gitleaks output.

Spotless `8.10.4` with ktfmt `0.65` in Kotlin conventions style owns formatting.
Generated `TestVectors.kt` is excluded from formatting and static analysis; its
owning Python generator is checked byte-for-byte by `checkTestVectors`.
detekt `2.0.0-alpha.6` runs default rules plus public API documentation checks,
including JVM type-aware analysis (`detektMainJvm`, `detektTestJvm`), without a
finding baseline or source suppressions.

The initial public ABI is tracked in [`api/`](api) using Kotlin's experimental
ABI validator. `checkKotlinAbi` disables unsupported-target inference: all target
libraries must compile, even when their runtime tests require another host.
After reviewing an intentional API change and its release impact, regenerate
with `./gradlew updateKotlinAbi`; do not update dumps merely to silence a failure.
The API is unchanged by this tooling adoption.

JaCoCo `0.8.15` enforces **100% line and branch coverage** of all JVM-compiled
`commonMain` code, with no production exclusions. HTML/XML reports are under
`build/reports/jacoco/jvmCoverageReport/`. This measures JVM execution only;
JavaScript and Native have runtime tests, not measured coverage.

The detekt prerelease and experimental ABI format were explicitly accepted for
this build. Replace/review them when compatible stable successors are available.
These checks do not establish full constitutional compliance: expert crypto
review, constant-time evidence, and performance budgets/baselines remain absent.
No production security or performance claim follows from passing CI.

Native tests must run on their corresponding hosts; a skipped foreign-host test
is not evidence of compatibility. Gradle downloads Kotlin/Native and Node.js when
needed. The wrapper verifies the Gradle distribution against its pinned SHA-256.
No C compiler or C libraries are required for these commands.

Common tests cover independent SHAKE256 outputs (including rate boundaries and
multi-block output), fragmented input/output, invalid state transitions, PRNG
domain separation, the exact challenge transcript, malformed encodings, aliasing,
and public-key/signature wire round-trips for upstream KAT record `0` at each level.
These round-trips **do not** constitute complete SQIsign KAT validation.

The generated common test fixtures can be reproduced from the parent repository
with Python 3's standard library only:

```sh
python3 tools/generate_test_vectors.py --check
# To intentionally regenerate:
python3 tools/generate_test_vectors.py
```

SHAKE/transcript fixtures use CPython's `hashlib` implementation as an independent
oracle. Public wire fixtures come from the parent `KAT/*.rsp` files; secret keys
and NIST DRBG seed material are not copied into this project.

## Security boundary

No constant-time claim is made on JVM, JavaScript, or Native. Even fixed-control-flow
Kotlin can be transformed by compilers, JITs, and garbage collectors. Encoding
checks are variable-time and intended for public inputs only. `clear()` performs
best-effort overwrites, not guaranteed secure erasure; returned output arrays and
caller-supplied seeds remain the caller's responsibility.

`SqisignPrng` is deterministic expansion, not an operating-system CSPRNG. It never
falls back to `kotlin.random.Random`. A future key-generation/signing layer needs
an explicit, failure-reporting platform entropy interface supplying a fresh
48-byte seed for each operation. The mutable sponge and PRNG are not thread-safe.

See [PORTING.md](PORTING.md) for specification analysis, reference mappings,
implementation order, interoperability gates, and unresolved security work.
The parent repository's [Apache-2.0 license](../LICENSE) and [NOTICE](../NOTICE)
apply; preserve them when redistributing this directory separately.
