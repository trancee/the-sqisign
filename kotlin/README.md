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
| `SqiSignPrng` | 48-byte seed expansion using `key`, `com`, `res`, and `def` domains |
| `WireFormat` | Exact public-key/signature lengths, canonical field encodings, response scalar bit bounds, defensive byte copies |
| `ChallengeHash` | Exact v3 Fiat-Shamir transcript using the commitment's Montgomery coefficient |

The internal arithmetic package now contains `UInt328`: the full 41-byte unsigned
width needed to hold `p324_3` encodings, using 22 radix-`2^15` limbs (13 bits in
the top limb). It supplies comparison, exact-width little-endian import/export,
addition with an explicit carry, and subtraction with an explicit borrow.
Instances own their storage and operations preserve both operands.
The range is `[0, 2^328)`, **not** the field range `[0, p)`: importing `p` or larger
does not reduce or validate a field element. This internal slice is not yet used
by the public wire parser and does not supply multiplication, division, signed
integers, field reduction, or the larger parameter-set widths.

The wire types expose byte encodings, not mathematical field/curve objects.
They do not check curve validity, supersingularity, basis-hint validity, matrix
normalization/invertibility, or any signature equation. Secret-key parsing is
deliberately deferred until the ideal and integer layers exist.

```kotlin
import ch.trancee.sqisign.Shake256

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
./gradlew checkUnsignedReference  # Python/C cross-check of public integer vectors
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
Owned JVM benchmark sources also undergo type-aware analysis (`detektBenchmarkJvm`).

The initial public ABI is tracked in [`api/`](api) using Kotlin's experimental
ABI validator. `checkKotlinAbi` disables unsupported-target inference: all target
libraries must compile, even when their runtime tests require another host.
After reviewing an intentional API change and its release impact, regenerate
with `./gradlew updateKotlinAbi`; do not update dumps merely to silence a failure.
The unreleased `0.1.0-SNAPSHOT` API uses `SqiSignPrng` (formerly `SqisignPrng`).
This casing rename changes source and binary compatibility on every target:
update callers and recompile; no old-name alias is retained.
The Kotlin package and Gradle group have also moved from `org.sqisign` to
`ch.trancee.sqisign` across all targets. Update imports to the new namespace;
this relocation changes source/binary compatibility and the Native library identity.
Byte encodings, domain separators, and runtime behavior are unchanged.

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
The Kotlin library and its runtime tests do not require C interop or C libraries.
The development-only `checkUnsignedReference` task, included in `check`, requires
a host `cc` compiler (GCC or Clang) and the parent C reference sources/headers.
Both hosted CI images provide it; no C code enters the library artifact.

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

Unsigned arithmetic fixtures additionally cover 276 deterministic public operand
pairs: zero/one/maximum, values around the `p324_3` prime, alternating bytes,
radix-boundary carry/borrow chains, and SHAKE-derived full-width inputs.
Python's arbitrary-precision integers provide the independent expected values.
`checkUnsignedReference` also compares every result to the C reference's
`mp_add_raw`, `mp_sub_raw`, and `mp_compare` at revision
`f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`; source and transitive project headers
are SHA-256-pinned in `tools/check_unsigned_reference.py`. It fails if those
inputs drift: review a reference rebase and its fixture impact rather than
silently updating checksums. The C adapter is an external test tool only.
Common tests run the same vectors on each Kotlin target, including explicit
carry/borrow and input/output ownership checks. No timing or arithmetic
performance claim follows from these correctness checks.

## JVM benchmarks

The explicitly accepted Alpha [`kotlinx-benchmark 0.5.0`](https://github.com/Kotlin/kotlinx-benchmark/releases/tag/v0.5.0)
plugin/runtime share one pin in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
JMH is pinned to `1.37`. The tools supply JVM benchmark generation, annotations,
and measurement harnesses unavailable in the standard library. `kotlinx-benchmark`
is Apache-2.0; [JMH is GPLv2 with the Classpath Exception](https://github.com/openjdk/jmh/blob/1.37/LICENSE).
The runtime belongs only to the dedicated `jvmBenchmark` compilation (and its
correctness tests); neither it nor JMH enters the production library artifact.
JMH brings `jopt-simple 5.0.4` and `commons-math3 3.6.1` into the benchmark
classpath only; production dependency and artifact size are unchanged. Use only
synthetic public inputs, and review the transitive tree when upgrading tooling.
Review/replace the Alpha dependency when a compatible stable release is available.
There are no JS or Native benchmark targets.

**Question:** how much average JVM time does one existing primitive operation
take for these deterministic public workloads? The metric is `avgt`, `ns/op`,
one thread, including operation-owned allocation and best-effort clearing.
Input preparation and parameter-name resolution occur outside timing.

| Benchmark method | Parameters | Timed boundary |
| --- | --- | --- |
| `Shake256Benchmark.digest` | Input bytes `0`, `136`, `4096`; output `64` | Fresh sponge, absorption, output allocation/squeezing, clearing |
| `PrngBenchmark.expand` | Output bytes `64`, `4096` | Fresh 48-byte seeded key-domain PRNG, expansion, clearing; no accumulated stream state |
| `ChallengeHashBenchmark.compute` | All parameter sets; message bytes `32`, `4096` | Commitment encoding checks and challenge transcript hashing, including allocation/clearing |
| `WireFormatBenchmark.parsePublicKey` / `parseSignature` | All parameter sets | Canonical encoding checks and defensive copies, not curve/signature verification |
| `UInt328Benchmark.decode` / `encode` | Five operand shapes below | Exact 41-byte import/export with owned result allocation |
| `UInt328Benchmark.compare` | Five operand shapes below | Unsigned variable-time comparison of prepared operands |
| `UInt328Benchmark.add` / `subtract` | Five operand shapes below | Fixed-width arithmetic with the complete allocated value/flag result retained; no encoding or field reduction |

The unsigned shapes are `ZERO_EQUAL` (`0`, `0`), `MAX_EQUAL` (`2^328 - 1`,
`2^328 - 1`), `CARRY_CHAIN` (`2^328 - 1`, `1`), `BORROW_CHAIN` (`0`, `1`),
and `FULL_WIDTH` (41 bytes of `0x55`, 41 bytes of `0xaa`). Codecs use the left
operand. Together these cover zero/dense encodings, full carry/borrow propagation,
equal comparison, and unequal comparison at the most/least significant limb.
They are fixed synthetic scenarios, not a distribution of real signing inputs.

Fixtures use synthetic public bytes, not secrets or real signatures. Ordinary
JVM tests verify all workload cases against independent existing SHAKE vectors,
explicit domain/transcript construction, and expected canonical wire bytes.
Unsigned workload tests check every shape against explicit encodings and JVM
`BigInteger` arithmetic, including carry/borrow flags. Benchmark methods return
outputs so the JMH harness retains their work; internal unsigned values and
arithmetic result wrappers return as `Any` only at the public harness boundary.
The internal production types and public library API are unchanged.

```sh
./gradlew jvmTest                       # Check workload correctness before timing
./gradlew jvmBenchmarkSmokeBenchmark    # Discovery/execution only: 42 cases
./gradlew jvmBenchmarkBenchmarkJar      # Build the JMH harness before measurement
./gradlew jvmBenchmarkBenchmark         # Opt-in full profile on a controlled host
```

The `smoke` profile uses one fork, one 50 ms warmup, and one 50 ms measurement
per case. Linux CI requires the complete 42-case parameter matrix, the expected
mode/unit/profile, and finite positive samples. It uploads JSON reports,
execution logs, and commit/host/JDK/Gradle metadata with the other Kotlin reports.
The `main` measurement profile uses three forks, three 1 s warmups, and five 1 s
measurements per case. Reports are timestamped under
`build/reports/benchmarks/{smoke,main}/`; generated harnesses stay in `build/`.

**Smoke scores are not performance evidence or regression baselines.** No
baseline/candidate comparison, practical effect-size threshold, numeric budget,
or speed claim is established here. Full comparisons require the same controlled
host/toolchain, stable power/thermal/load conditions, interleaved baseline and
candidate runs, uncertainty analysis, and retained raw reports/environment
metadata outside transient build output. Shared CI never gates score changes.
JMH `1.37` currently emits deprecated `sun.misc.Unsafe` warnings on JDK `26`;
these do not prevent execution, but future JDK upgrades require revalidation.

## Security boundary

No constant-time claim is made on JVM, JavaScript, or Native. Even fixed-control-flow
Kotlin can be transformed by compilers, JITs, and garbage collectors. Encoding
checks are variable-time and intended for public inputs only. `clear()` performs
best-effort overwrites, not guaranteed secure erasure; returned output arrays and
caller-supplied seeds remain the caller's responsibility.

`SqiSignPrng` is deterministic expansion, not an operating-system CSPRNG. It never
falls back to `kotlin.random.Random`. A future key-generation/signing layer needs
an explicit, failure-reporting platform entropy interface supplying a fresh
48-byte seed for each operation. The mutable sponge and PRNG are not thread-safe.

See [PORTING.md](PORTING.md) for specification analysis, reference mappings,
implementation order, interoperability gates, and unresolved security work.
The parent repository's [Apache-2.0 license](../LICENSE) and [NOTICE](../NOTICE)
apply; preserve them when redistributing this directory separately.
