# Project profile

purpose= C implementation of SQIsign, plus an experimental pure-Kotlin Multiplatform foundation.
languages/toolchains= C11 and ASM with CMake 3.13 or later; independent Kotlin build under `kotlin/`.
supported_platforms= x64 and arm64 are exercised by CI; implementation types are `ref`, `opt`, `broadwell`, and `arm64`. Do not infer support for other targets from local builds.
prerequisites= CMake and a C11-compatible compiler; CI uses Ninja. Constant-time tests additionally require Valgrind and its development header.

## C CI runners

Push-triggered CI is path-scoped: CMake builds watch `CMakeLists.txt`, `.cmake/`,
`src/`, `include/`, `apps/`, `test/`, and `scripts/`; pedantic compilation watches
the same build/source inputs except `scripts/`. Both also watch their own workflow
and the shared C-runner setup action. C formatting watches C/header files,
`.clang-format`, `.pre-commit-config.yaml`, its script, and its workflow.
Kotlin push/PR checks watch non-Markdown files under `kotlin/`, the shared `KAT/`
fixtures, the unsigned-oracle inputs (`src/mp/ref/`, `src/common/generic/include/`,
the `p324_3`, `p500_27`, and `p664_17` precomputation headers, E0 basis/cofactor constants and field sources,
the shared `fp`/`fp2` headers and implementations, `scripts/add_parameter_set.py`,
`scripts/precomp/torsion_basis.py`,
`include/sqisign_namespace.h`, `src/ec/ref/lvlx/ec.c`, `src/ec/ref/lvlx/basis.c`,
`src/ec/ref/lvlx/biextension.c`,
`src/ec/ref/include/ec.h`, `src/ec/ref/include/biextension.h`, and
`include/ct_testing.h`), and its workflow.
Additional torsion-ring oracle inputs are `src/quaternion/ref/lvlx/dim2.c`,
`src/quaternion/ref/include/quaternion.h` and
`src/quaternion/ref/lvlx/internal_quaternion_headers/internal.h`.
Kotlin-only changes do not launch C checks; C-only changes do not launch Kotlin checks unless they
change those shared fixtures or reference inputs. Root documentation/
governance-only changes launch neither.
Manual dispatches and scheduled C checks remain unfiltered; GitHub path filters
apply only to push/PR events. Required branch checks must account for skipped
workflows: GitHub can leave a path-filtered required check pending.

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
| `:` / JVM benchmark | JDK `26`; controlled host for measurements | `jvmBenchmarkSmokeBenchmark`; opt-in `jvmBenchmarkBenchmark` | Linux CI validates 348 primitive/parameter cases and JSON samples; no score gate or baseline; no JS/Native benchmark proof |
| `:` / JS | JDK `26`; plugin downloads Node | `jsNodeTest` | Node runtime; approved 600-second per-test limit accommodates measured composed curve operations; no browser or coverage proof |
| `:` / Linux x64 | x86-64 Linux; downloaded Kotlin/Native toolchain | `linuxX64Test` | Native runtime; no measured coverage |
| `:` / macOS ARM64 | Apple Silicon macOS with Xcode tools | `macosArm64Test` | Native runtime; Linux cannot supply this proof; no measured coverage |
| `:` / all target libraries | JDK `26`; downloaded Kotlin/Native toolchain | `checkKotlinAbi` | Compiled ABI against `kotlin/api/`; inference disabled; not final published-artifact validation |

Fast checks: `./gradlew spotlessCheck detektMainJvm detektBenchmarkJvm detektTestJvm jvmTest` from `kotlin/`. Authoritative CI-equivalent: `./gradlew clean check --no-build-cache --rerun-tasks` on both host runners, followed by `./gradlew jvmBenchmarkSmokeBenchmark` on Linux. `check` also verifies generated fixtures, default detekt rules, strict ABI, and JVM coverage. CI additionally scans Kotlin history/current files with Gitleaks `8.30.1`.

Initial internal unsigned arithmetic: `UInt328` in `commonMain` uses 22 radix-`2^15`
limbs and exact 41-byte encodings, with explicit addition carry/subtraction borrow.
Multiplication retains all 656 bits in an immutable `UInt656` with an owned
82-byte encoding; each radix multiply-add is bounded by `2^30 - 1`.
`divideAndRemainder` returns exact `UInt328` quotient/remainder and rejects a
zero divisor; restoring binary division is variable-time, not field reduction.
`UInt656.divideAndRemainder(UInt328)` retains a full `UInt656` quotient and a
`UInt328` remainder, including the 329-bit trial remainder before subtraction.
No field reduction or public API is added. The decision,
bounds, oracle provenance, and remaining milestone scope are in `kotlin/PORTING.md`.
`checkUnsignedReference` is part of `check`; it requires a host GCC/Clang `cc`
compiler solely for the external C test adapter. SHA-256 pins cover the C source
and project headers at `f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`.
`generate_test_vectors.py` owns the 340 Python-integer oracle records in
`TestVectors.kt`, including exact wide products and quotient/remainder (zero
divisors are explicitly marked invalid); common tests consume them on all
targets and the C gate cross-checks every record. No C runtime dependency enters
the Kotlin artifact.
The same generator owns another 322 full-width wide-division records; 314
nonzero divisors are cross-checked against C and eight are explicitly invalid.
`UInt328.gcd` reuses the exact remainder operation with Euclid's algorithm:
`gcd(0, x) = x` and `gcd(0, 0) = 0`; operands remain immutable and execution is
variable-time. A separate 441-record fixture list uses Python `math.gcd` and the
pinned C `ibz_gcd`, including shared factors and consecutive Fibonacci pairs.
`UInt328.sqrtFloor` returns the exact floor square root by integer Newton
iteration. A separate 524-record fixture list uses Python `math.isqrt` and the
pinned C `ibz_sqrt_floor`; common tests also cover exact squares, adjacent values
across the root width, and maximum input. The operation is variable-time and
does not perform field reduction.
`UInt328.modInverse` returns a canonical inverse for moduli of at least two,
rejecting smaller moduli and non-coprime inputs with explicit reasons.
Reduced Euclidean coefficients and exact wide products avoid signed arithmetic;
577 independent Python `pow`/pinned C `ibz_invmod` records cover success and failure.
Inversion is variable-time and does not establish a field or secret-input contract.
Internal `Int328` adds checked signed arithmetic in `[-2^327, 2^327 - 1]`,
with exact 41-byte two's-complement little-endian codecs and signed comparison.
Addition/subtraction return exact results or throw `ArithmeticException` on
overflow; no wrapped result is returned. Storage reuses immutable `UInt328`
bits, not a signed reinterpretation of field values. Another 314 Python/C
records independently check ordering, sum/difference, overflow markers and exact
82-byte signed products via pinned C `ibz_mul`.
Six valid operand shapes retain signed codecs/comparison/addition/subtraction
benchmarks. `Int328.multiply` returns an immutable `Int656` with an owned
82-byte two's-complement encoding; unsigned magnitudes retain minimum-value
products without narrowing. Six additional signed multiplication shapes cover
zero, signed bounds and full-width operands.
`Int328.divideAndRemainder` truncates the quotient toward zero, with the
nonzero remainder following the dividend's sign and absolute value below the
divisor's magnitude. Zero divisors and `minimum / -1` fail explicitly.
Another 570 Python/C records check both signed results/failure categories;
eight valid JVM workloads preserve both outputs. `Int656.divideAndRemainder`
accepts an exact signed 656-bit dividend and signed 328-bit divisor, returning
a full-width signed quotient and narrow remainder with the same truncation and
sign rules. It rejects zero divisors and the sole positive quotient overflow
(`minimum / -1`). `Int656` also provides signed comparison and checked addition/subtraction;
overflow throws `ArithmeticException` instead of wrapping. Separate 209-record
Python/C fixtures check wide arithmetic and wide division over boundary and
deterministic full-width inputs. Six JVM shapes preserve each compare/add/subtract
workload and are checked against `BigInteger`; eight more workloads verify wide
division. Arithmetic remains variable-time. The division rationale, alternatives,
risks and migration are in
[`kotlin/adr/0001-int656-signed-division.md`](kotlin/adr/0001-int656-signed-division.md).
`Int328.extendedGcd` returns an unsigned nonnegative GCD and signed 328-bit
Bezout coefficients, including explicit zero-operand conventions. The
implementation uses unsigned magnitudes and checked `Int656` coefficient
updates; an unused terminal coefficient for zero remainder is not narrowed.
Another 117 signed boundary/deterministic vectors are generated by Python and
cross-checked with pinned C `ibz_xgcd`, comparing the GCD and validating the
Bezout identity without requiring canonical coefficients. Seven retained JVM
workloads cover zero, signed limits, full-width values and a long Fibonacci
chain. The implementation is variable-time and establishes neither secret-
input suitability nor expert review or a controlled performance baseline.
The algorithm, coefficient-width reasoning, alternatives and migration are in
[`kotlin/adr/0003-int328-extended-gcd.md`](kotlin/adr/0003-int328-extended-gcd.md).
The internal field layer implements base fields and quadratic extensions for
all three parameter sets: `Fp324`/`Fp2P324`, `Fp500P27`/`Fp2P500`, and
`Fp664P17`/`Fp2P664`. Encodings are strict canonical little-endian residues;
operations include arithmetic, inversion, residue testing, verified canonical
roots, sign, Boolean selection, and immutable conditional swap. Python oracle
coverage comprises 113 p324 base operation pairs, 48 pairs for each wider base
field, and 64 extension operation pairs per parameter set, plus nonlinear
vectors. The pinned C checker independently cross-checks arithmetic, roots,
and both selection/swap controls across all six types. The p324 field uses
`UInt328` encoding. All fields multiply and exponentiate through the shared
16-bit-digit Montgomery `FieldModulus`
([`ADR 0016`](kotlin/adr/0016-montgomery-field-multiplication.md)). Operations and controls are variable-time and unsuitable for secret
inputs. No performance budget, constant-time guarantee or expert cryptographic
review is established. Initial representation
decisions are in
[`kotlin/adr/0004-p324_3-base-field.md`](kotlin/adr/0004-p324_3-base-field.md)
and [`kotlin/adr/0005-p324_3-extension-field.md`](kotlin/adr/0005-p324_3-extension-field.md);
the completed scope and control contract are in
[`kotlin/adr/0006-field-core-all-parameter-sets.md`](kotlin/adr/0006-field-core-all-parameter-sets.md).

Formatting: Spotless `8.10.4` / ktfmt `0.65`; apply via `./gradlew spotlessApply`. Static analysis: accepted detekt `2.0.0-alpha.6`, including type-aware JVM tasks, no finding baseline. ABI: explicitly accepted Kotlin experimental validator; regenerate reviewed changes with `./gradlew updateKotlinAbi`. Reassess both experimental tools on compatible stable releases.

Internal curve arithmetic: typed `MontgomeryCurve<Element>` and
`MontgomeryPoint<Element>` use the three existing extension-field representations.
They supply nonsingular `(A:C)` construction, `A/C`
normalization, projective equality/infinity, doubling, differential addition
and unsigned scalar ladders retaining all 328/512/672 parameter-width bits.
Scalars are exact-width little-endian byte arrays, rejected at other widths
without reduction or input mutation. Coordinate construction does not
prove membership, supersingularity or torsion order; all operations remain
variable-time and unsuitable for secret inputs. Three-point/biscalar ladders
and deterministic differences extend this boundary with explicit exceptional
domains. Reference-sign roots are separate from unchanged canonical roots.
Forty-eight independent affine Python records, 90 raw-sign root records and
pinned C outputs cover all three parameter sets, with 72 separate C
two-torsion basis/parity checks and 24 shared-difference records.
Shared differences implement algorithm 4.93 for consistent common translates;
rank-deficient elimination fails explicitly rather than treating `(0:0)` as infinity.
The generator is
`python3 tools/generate_curve_vectors.py`, owning `ec/CurveVectors.kt` in
common tests. `checkCurveReference`, included in `check`, requires host `cc`
and pins the C curve/basis/pairing sources plus transitive project headers. Fifty-four JVM
workloads retain these operations across all levels with short/full-width scalars.
[`ADR 0007`](kotlin/adr/0007-p324_3-montgomery-arithmetic.md) records the
contract; [ADR 0008](kotlin/adr/0008-p324_3-ladders-and-deterministic-differences.md)
records the subsequent ladders/differences, alternatives, risks and remaining scope. No public ABI,
wire format, runtime dependency, toolchain or release version changes.
The all-parameter typed cutover and removal of the superseded concrete path
are recorded in
[`ADR 0009`](kotlin/adr/0009-shared-typed-montgomery-arithmetic.md).
The shared-difference domain and approved complete basis-change strategy are in
[`ADR 0010`](kotlin/adr/0010-shared-differences-and-complete-basis-changes.md).
Internal `TorsionBasis` validates curve membership, exact order, independence
and differential consistency. Its special `canonicalE0` factory supplies
all-parameter canonical `A=0` bases. The offline owner is
`python3 tools/generate_basis_vectors.py`, generating `ec/E0BasisCoordinates.kt`
and common `ec/TorsionVectors.kt`; `checkCurveReference` checks both outputs
and twelve bases against the pinned C constants/helper.
Six retained `TorsionBasisBenchmark.canonicalE0` workloads include coordinate
decoding, order reduction, reference-sign difference and every validation.
Only generated formatting/static-analysis exclusions are added; production
coverage exclusions remain empty. The contract is in
[`ADR 0011`](kotlin/adr/0011-checked-torsion-bases-and-hints.md).
The typed factories additionally provide checked base-field residuosity,
with 72 independent Python/C records; extension residuosity is not a substitute.
Internal `TorsionBasis.canonical` and `fromHint` implement general generation
and immediate checked reconstruction. Thirty independent general bases and
27 direct hints agree with C. The `BasisHint` codec preserves all bytes and
encodes every generated search index at least 128 as zero; zero regenerates
from one. Reconstruction targets are target exponents. Six independent
E0 records additionally verify target-compatible differences where C's
unadjusted shortcut does not supply the required reduction identity.
Thirty-six `CanonicalBasisBenchmark` cases retain search, target-compatible
generation and direct reconstruction across both residue branches and
two/full torsion. Generated `benchmarks/CanonicalBasisInputs.kt` is owned by
the same basis generator and checked by `checkCurveReference`.

Internal `TorsionScalar` owns strict canonical residues modulo `2^e` for
`1 <= e <= 664`, with modular arithmetic, odd inversion and checked
zero-extension to ordinary ladder widths. `TorsionMatrix` provides row-major
coefficients, determinant/inversion and one global lexicographic sign.
`tools/generate_torsion_ring_vectors.py` owns common `ec/TorsionRingVectors.kt`;
`checkTorsionRingReference`, included in `check`, compares all 462 scalar and
276 matrix records against pinned C. The adapter explicitly reduces C's
noncanonical negated-zero matrix entries. Thirty-six retained JVM cases
exercise six operations with independent workload checks. See
[`ADR 0012`](kotlin/adr/0012-torsion-scalars-and-basis-matrices.md).

Internal `PowerOfTwoDiscreteLog` prepares a verified primitive generator
and reuses inverse powers for balanced algorithm 4.37. Leaf checks reject
nonmembers explicitly; complete scalar assembly retains all 664 bits.
`tools/generate_pairing_vectors.py` owns 57 common `ec/PairingVectors.kt`
records and benchmark-only `DiscreteLogInputs.kt`; `checkCurveReference`
verifies both outputs and every logarithm against pinned C. Twenty-four
retained JVM cases separately time reused preparation and fresh
preparation-plus-solving. See
[`ADR 0013`](kotlin/adr/0013-prepared-power-of-two-discrete-logs.md).

Internal `TatePairing` requires a curve-bound full-basis maximality witness,
validates raw operands and preserves cubical scales in algorithm 4.104.
Already-proved witness facts are reused; unknown points retain mathematical
checks. Complete infinity/self/zero-two-torsion behavior does not assume
all lower-order restrictions are trivial. The same generator owns 180
independent shifted-Miller records and `TatePairingInputs.kt`; the C gate
cross-checks 57 generic-domain pairings and retains explicit malformed checks.
Twelve JVM cases separately retain reused and fresh pairing preparation.
See [`ADR 0014`](kotlin/adr/0014-maximal-domain-tate-pairings.md).

Internal `TorsionBasis.applyMatrix` and `BasisChange` implement
invertible column action and complete affine cross-differences around the
existing cubical Tate/logarithm seams. The owner
`tools/generate_basis_change_vectors.py` generates independent affine
coordinates, normalized known matrices and common runtime declarations;
`checkCurveReference` checks their content, all 369 action coordinates and
51 generic C basis changes. All 246 transformation cases and ten boundary
tests pass on JVM, with static/compiled ABI checks. Full-width `p664_17`
examples exceeded Node's former 30-second limit; the user approved a
measured-budget approach starting at 300 seconds; both cold tests now pass.
The complete cold `p664_17` correspondence subsequently exceeded that limit
(`314.877s` before timeout). The user rejected recurring long gates, and
Montgomery field multiplication
([`ADR 0016`](kotlin/adr/0016-montgomery-field-multiplication.md)) fixed the
cause. Node's per-test limit is back to 30 seconds. Native test executables
are optimized. No checks or cases are removed.
Algorithm 3.9's `setChangeOfBasisMatrix` passes twelve independent
affine/integer cases and four boundaries on JVM, with three generic
composed C checks. It returns the normalized correspondence and both
target-compatible hints, inheriting canonical generation's maximality premise.
Twenty-four retained cases cover action, prepared/fresh change and complete
correspondence at two/full torsion across all parameters. The same generator
owns benchmark-only `BasisTransformInputs.kt`, not expected results.
Complete cross-runtime and fresh smoke gates remain pending. See
[`ADR 0015`](kotlin/adr/0015-complete-basis-actions-and-changes.md).

Generated fixture owner: `python3 tools/generate_test_vectors.py`; output `src/commonTest/kotlin/ch/trancee/sqisign/TestVectors.kt`; `checkTestVectors` checks exact content from Python hashlib and the parent public KATs. This generated test file is excluded from Spotless/detekt, not from execution. No generated API docs or publication pipeline is configured.

Benchmark owner: `kotlin/src/jvmBenchmark/`, with workload correctness tests in `kotlin/src/jvmTest/`. `kotlin/README.md` defines timed boundaries, workloads, profiles, and comparison controls. CI validates 348 JVM smoke cases and retains raw reports under `build/reports/benchmarks/smoke/`, logs, and commit/host/runtime metadata in uploaded artifacts; these are executability evidence only. Generated harnesses and benchmark sources are outside production coverage; production exclusions remain empty. JMH's deprecated Unsafe warnings on JDK `26` require revalidation on future JDK upgrades.

Limitations: passing Kotlin gates is not full constitutional compliance. Crypto expert review, constant-time guarantees, numeric performance budgets, and committed controlled-measurement baselines are not established. Retained JVM workloads do not establish performance or portability on other targets. JVM coverage does not imply coverage on JS/Native. C coverage remains measurement-only as documented in `DEVELOPERS.md`; this Kotlin-only change does not alter C gates.
