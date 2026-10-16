# SQIsign v3 pure-Kotlin porting design

## Baseline and evidence

Normative baseline: [SQIsign v3.0, 2026-09-01](https://sqisign.org/spec/sqisign-20260901.pdf),
SHA-256 `843912e5eb3dee20288af9fc9f01c0e1f1055834d3ce87c4687795c13adfd73c`.
The [project website](https://sqisign.org/) identifies v3 as its current submission.
The submission's `nist-v3` tag resolves locally to
[`6d017708db403bf83977fa70770fc4f7f9e9ff21`](https://github.com/SQISign/the-sqisign/tree/6d017708db403bf83977fa70770fc4f7f9e9ff21).

The analyzed working reference is
[`f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`](https://github.com/SQISign/the-sqisign/tree/f417ebd4c2c336ad6a9feb6f9255f95fc03acc41).
It is a development revision, not the submission tag. Its inspected public
encoding, challenge-hash, PRNG, and encoded-size files are unchanged from
`nist-v3`; this is not a claim that the entire repository is identical. The
top-level CMake project still says `VERSION 2.0`; that metadata is not an algorithm
version selector.

Structural exploration used the graph indexed at `2026-10-16T14:05:52Z` and
task-directed Tier 2 coverage checks. Parser gaps in `src/sqisign.c`,
`src/signature/ref/lvlx/sign.c`, and `src/common/generic/prng.c` were inspected
directly. This was a bounded porting analysis, not an exhaustive call-graph or
security audit. Generated arithmetic includes have recorded whole-file parser
gaps and must be read directly before porting them.

## What changed in v3

Specification section 1.4 makes a v2 port the wrong starting point:

- The primes grew in response to the attack cited as `Wes26`; use the v3
  parameters, not earlier `p248`/`p384`/`p512` families.
- Quaternion ideals use the new inert representation. Integer bounds are
  explicit; the reference no longer needs GMP or floating-point arithmetic.
- Lattice reduction is integer-only and canonicalized; preserve its choices to
  reproduce byte-level outputs.
- Ideal-to-isogeny translation and dimension-two formulas changed.
- The challenge has length `lambda`, and responses have odd degree, avoiding
  shared challenge/response suffixes. This changes response packaging and the
  underlying security statements.

| Parameter | Prime | `lambda` | `e_rsp` | Field bytes | PK / SK / signature bytes |
| --- | --- | --- | --- | --- | --- |
| `p324_3` | `3 * 2^324 - 1` | `128` | `196` | `41` | `83 / 270 / 200` |
| `p500_27` | `27 * 2^500 - 1` | `192` | `302` | `64` | `129 / 417 / 306` |
| `p664_17` | `17 * 2^664 - 1` | `256` | `400` | `84` | `169 / 549 / 406` |

Sources: specification chapters 5-6 and
[`encoded_sizes.h`](../src/precomp/ref/p324_3/include/encoded_sizes.h),
with the corresponding headers for `p500_27` and `p664_17`.

## Protocol and reference mapping

| Stage | Specification | Reference at the baseline |
| --- | --- | --- |
| Public API and length handling | Sections 3.4.6-3.4.8, chapter 6 | [`src/sqisign.c:10-133`](../src/sqisign.c#L10) |
| Seed expansion | Algorithms 3.1-3.2, table 6 | [`prng.c:32-99`](../src/common/generic/prng.c#L32) |
| Challenge transcript | Algorithm 3.3 | [`common.c:15-46`](../src/verification/ref/lvlx/common.c#L15) |
| Public encodings | Chapter 6 | [`encode_public.c:104-180`](../src/verification/ref/lvlx/encode_public.c#L104) |
| Key generation | Algorithm 3.22 | [`keygen.c:19-112`](../src/signature/ref/lvlx/keygen.c#L19) |
| Signing | Algorithm 3.23 | [`sign.c:192-375`](../src/signature/ref/lvlx/sign.c#L192) |
| Verification | Algorithm 3.24 | [`verify.c:169-217`](../src/verification/ref/lvlx/verify.c#L169) |

Key generation samples an odd-prime-norm secret ideal, maps it to a curve and
torsion basis, derives the public basis hint, and normalizes the change-of-basis
matrix. Signing samples a commitment ideal, hashes the commitment and message,
constructs the challenge ideal and quaternion response, translates/combines
ideals through dimension-two isogenies, and packages the auxiliary curve, matrix,
challenge, and hints.

Verification has a smaller dependency surface than signing. The inspected
`sqisign_verify` outbound trace reaches decoding and `protocols_verify`;
the latter checks matrix representatives/normalization and curves, reconstructs
the challenge isogeny and two bases, recovers the commitment through a verified
dimension-two chain, and compares the challenge hash. It does not need the
signer's ideal sampling or ideal-to-isogeny machinery. The C verifier does use
integer helpers, so a Kotlin verifier still needs scalar/integer primitives.

## Byte-level invariants already implemented

SHAKE256 uses a `136`-byte rate, `512`-bit capacity, Keccak-f[1600]'s `24` rounds,
little-endian lanes, and the SHAKE suffix `0x1f` with final padding bit `0x80`.
Zero-length squeezing finalizes absorption. Split squeezes continue the same
stream; they must not instantiate a fresh sponge.

For a 48-byte seed, PRNG streams are exactly `SHAKE256(seed || domain)`, not
`SHAKE256(domain || seed)`. Domains are three ASCII bytes without a NUL terminator:
`key`, `com`, `res`, `def`. Each stream has independent mutable state; do not
reproduce the C reference's thread-local root context as global Kotlin state.

The challenge is exactly:

```text
SHAKE256(
    ASCII("SQI") ||
    LE(real(A_pk)) || LE(imag(A_pk)) || hint_pk ||
    LE(real(A_com)) || LE(imag(A_com)) ||
    message,
    lambda / 8 output bytes
)
```

Hash the normalized Montgomery coefficient `A_com`, **not** the j-invariant.
The C comment mentioning `j(E_com)` is misleading; its actual call is
`fp2_encode(..., &com_curve->A)`, matching specification algorithm 3.3.

Field encodings are fixed-width unsigned little-endian representatives strictly
below `p`, with real component before imaginary component. A public key appends
one hint byte. A signature concatenates auxiliary `A`, four response entries in
row-major order, challenge, auxiliary hint, then challenge hint. Response entries
have width `e_rsp + 2` bits, encoded in `25`, `38`, or `51` bytes. All `256` hint
byte values have a syntactic interpretation; whether a hint reconstructs the
required basis is a mathematical check, not a byte-decoding check.

The foundation intentionally rejects noncanonical field representatives and
unused response bits rather than reducing attacker-supplied encodings. A future
interop harness must distinguish this specified strictness from any acceptance
of aliases by the C decoder.

## Remaining implementation sequence and acceptance gates

Implement one mathematical layer at a time, starting with `p324_3`, and promote
each layer to all three parameter sets before exposing a public signature API.
Keep this a single build initially; the following are dependency boundaries, not
a request to add many Gradle subprojects.

| Milestone | Work and sources | Required gate |
| --- | --- | --- |
| 1. Integer/scalar core | Specification 4.1; `src/mp`. Fixed-width unsigned operations, bounded signed integers, multiplication, division/remainder, GCD/inversion, square root and primality helpers as needed. | Carry/borrow, overflow bounds, signed division semantics, zero/one/max boundaries, randomized C differential tests. Never silently truncate a bound violation. |
| 2. Field core | Specification 4.2; `src/gf/ref`. `Fp`, `Fp2 = Fp[i]/(i^2+1)`, reduction, inversion, square roots, canonical root/sign choices, select/swap and serialization. | All levels: algebraic identities, independent modular oracle, C differential vectors, strict decode boundaries and inverse/sqrt failures. |
| 3. Curves and bases | Specification 4.4 and algorithms 3.5-3.12; `src/ec/ref`. Montgomery/projective arithmetic, x-only ladders, canonical torsion bases, hints, pairings, 2-isogeny chains. | Point-at-infinity and exceptional-curve handling; exact hint/root behavior; torsion-order checks; C basis, pairing and chain outputs. |
| 4. Dimension-two isogenies | Specification 4.5; `src/hd/ref`, including gluing, theta chains, kernel validation and splitting. | Differential intermediate theta/curve outputs, exceptional/degenerate cases, malformed kernels and non-splitting rejection. A generic elliptic-curve library is not a substitute. |
| 5. Verifier | Algorithm 3.24; `src/verification/ref`. Matrix normalization, checked reconstruction and challenge comparison. | Every supplied KAT signature verifies at every level. Mutated messages, hints, fields, matrices, padding and lengths fail appropriately; C/Kotlin cross-verification and fuzzing. |
| 6. Quaternion and lattice core | Specification 4.3; `src/quaternion/ref`. Inert ideals, norms/intersections, integer-only lattice operations, canonical reduction and response sampling. | Differential exact outputs, proven intermediate integer bounds, reduction tie-breaking, sampling distribution and failure/retry behavior. |
| 7. Ideal/isogeny bridge and constants | Specification 4.6-4.7 and appendix B; `src/id2iso/ref`, `src/precomp/ref`, `scripts/precomp`. | Reproduce canonical mathematical constants from scripts; test endomorphism/basis action, ideal norm/degree relationships and C intermediate outputs. |
| 8. Key generation and signing | Algorithms 3.22-3.23; `src/signature/ref`. Secure entropy seam, seeds/domains, ideal sampling, normalized secret matrix, response packaging and secret-key codec. | Full deterministic KAT reproduction and two-way signing/verification at all levels, including error paths; no placeholder algorithms or fallback signature scheme. |
| 9. Hardening and release | Specification 8.5 and chapter 9; reference CT harness documentation. | Independent mathematical/security review, malformed-input fuzzing, per-runtime leakage assessment and benchmarks; explicit release security policy. |

For portable arithmetic, prefer fixed-radix `IntArray`/`LongArray` storage and
explicit per-operation bounds rather than `java.math.BigInteger` or a native
big-number dependency. A practical initial field radix is `2^15`: multiplying and
accumulating one product plus normalized carry in `Long` has ample headroom and
can be expressed exactly on every Kotlin target. Specify accumulation bounds
before batching products. Benchmark a wider radix only after differential
correctness is established; the optimal C/assembly radix is not automatically
optimal for Kotlin/JS or managed runtimes.

Keep signed quaternion integers distinct from unsigned field/scalar limbs.
Port C's mathematical semantics, not machine pointer casts, native-endian memory
copies, implementation-defined shifts, or the generated machine-specific field
representation. Wire bytes always encode canonical mathematical representatives,
not Montgomery-domain or redundant internal values.

Precomputation generators may remain offline Sage/Python tooling. The shipped
library must contain deterministic Kotlin constants and no Sage, GMP, C, or JNI
runtime requirement. Do not directly copy internal Montgomery-domain tables
without converting them for the Kotlin representation.

## Arithmetic decision 2026-10-16: first bounded unsigned slice

**Context.** Milestone 1 needs portable, explicitly bounded unsigned operations
before field or signed quaternion arithmetic. The `p324_3` field encodes into
41 bytes, even though its prime has 326 bits. No current public operation uses
the new arithmetic layer.

**Decision.** Introduce internal `ch.trancee.sqisign.arithmetic.UInt328`, representing
exactly `[0, 2^328)` with 22 normalized little-endian radix-`2^15` `Int` limbs.
The first 21 limbs have 15 bits; the last has 13. Import/export require/produce
exactly 41 unsigned little-endian bytes and never alias caller storage. The type
does not imply canonical field membership: `p` and values above it are retained.
Comparison returns unsigned order. Addition returns `value + carry * 2^328`;
subtraction returns `value - borrow * 2^328`, with flags in `{0, 1}`. Callers must
handle these flags; modular truncation is not an implicit success.

For ordinary limbs, addition including carry is at most `65535`, while subtraction
including borrow is in `[-32768, 32767]`; both fit exactly in Kotlin `Int` on every
target. The top-limb bounds are `16383` and `[-8192, 8191]`. Sign-bit extraction
produces the borrow, and masking normalizes low bits. This initial decision did
not include multiplication; its separate bounds and representation are recorded below.

**Alternatives.** A 326-bit field-shaped integer would reject the two extra wire
bits before field validation; a 330-bit whole-limb integer would need padding
conversion at every 41-byte boundary. The full-byte 328-bit bound keeps scalar
storage distinct from field membership. A configurable-width framework and
signed integers are deferred until their actual callers/bounds exist.
`BigInteger`, JNI, and native arithmetic dependencies remain excluded from production.

**Evidence and risks.** 340 public deterministic vectors use independent Python
integer arithmetic and a SHA-256-pinned C reference at
`f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`. The adapter calls the reference's raw
unsigned add/subtract/compare helpers with 384-bit C storage, extracts the carry
at bit 328, and serializes only the low 328 bits; subtraction's 384-bit borrow
equals the unsigned underflow bit at the narrower width for these bounded inputs.
Common tests include exact widths, every individual bit, ownership, aliasing of
operands, and the full differential vector set. This is correctness evidence,
not independent expert review, field conformance, constant-time proof, a numeric
performance budget, or a controlled benchmark baseline. Comparison is variable-time.

**Migration and remaining work.** Public ABI, wire parsing, and existing primitive
behavior are unchanged; no public API or release-version migration is needed.
Development `check` now requires GCC/Clang for its external C oracle, not for the
library or Kotlin runtime tests. The CI path filter includes its reference inputs.
Milestone 1 remains incomplete: wide division/remainder, additional checked wider
intermediates, signed integers, GCD/inversion and larger parameter sets still need
their own bounds, differential tests and representative performance measurements
before any public signature API is introduced.

## Arithmetic decision 2026-10-16: exact wide multiplication

**Context.** Bounded unsigned arithmetic needs full products before any quotient
or field-reduction layer. Dropping high bits at the 328-bit operand width would
destroy information needed by those later operations.

**Decision.** `UInt328.multiply(UInt328)` returns an internal immutable `UInt656`
in `[0, 2^656)` with an exact 82-byte unsigned little-endian encoding. The result
owns its bytes; import/export defensively copy, and non-exact input widths are
rejected. There is no signed interpretation, overflow flag, modular reduction,
or new public library API.

Schoolbook multiplication uses 44 temporary radix-`2^15` limbs. Every inner
multiply-add is normalized immediately: each input/output limb and incoming
carry is at most `32767`, so the accumulator is at most
`32767^2 + 32767 + 32767 = 2^30 - 1`. It fits exactly in a nonnegative Kotlin
`Int` on JVM, JS and Native; no `Long`, floating-point approximation, or unchecked
batched accumulation is required. At each row end the carry slot has not yet
been written; the preceding row's carry is consumed by that row's final
multiply-add. Operand bounds prove the final value is below `2^656`, so the
last of the 44 normalized limbs needs at most 11 bits. Encoding all 82 bytes
therefore discards no product bits.

**Alternatives.** A low-half/overflow result is insufficient for later reduction.
A generic wide-integer framework or additional wide arithmetic is not justified
by current callers. Byte-backed `UInt656` is an opaque exact result, not a
field/scalar type; it avoids exposing temporary limb ownership or assuming a
future reduction representation. Encoding and defensive ownership copies are
deliberately included in its retained JVM benchmark; no allocation/performance
claim is made.

**Evidence and risks.** The existing 340 independent Python-integer records now
include full 82-byte products, checked by the pinned C `mp_mul_raw` using twelve
64-bit output words. C storage is wider than the mathematical product; serialization
retains all 656 possible bits. Common tests cover both operand orders, every
single-bit product position from 0 through 654, the maximum square (including bit
655), operand preservation, codec widths and ownership. JVM benchmark workloads
also compare products to `BigInteger`; all six unsigned operations are retained
across five synthetic shapes. This is not expert review, field conformance,
constant-time proof, a performance budget, or a controlled benchmark baseline.

**Migration.** No public ABI, wire parser, dependency, toolchain, or release-version
change. The fixture generator owns the extended record format; existing consumers
retain their field positions and multiplication consumes the appended product.
The multiplication workload adds five cases to the JVM smoke matrix. Wide
division, signed arithmetic and field reduction remain separate work.

## Arithmetic decision 2026-10-16: bounded unsigned division

**Context.** Milestone 1 needs exact integer quotient/remainder semantics before
any field or signed division layer. Both operands are bounded by `2^328`;
products requiring wider dividends are a separate contract.

**Decision.** Internal `UInt328.divideAndRemainder(divisor)` returns independently
owned immutable `UInt328` quotient and remainder with
`dividend = quotient * divisor + remainder` and `0 <= remainder < divisor`.
Zero divisors, including `0 / 0`, fail explicitly with `IllegalArgumentException`
and no result or input mutation. The public library API remains unchanged.

Restoring binary division consumes exactly 328 dividend bits from high to low.
After each bit it conditionally subtracts the divisor, using the existing
normalized radix-`2^15` subtraction helper and its unsigned borrow. Before the
conditional subtraction the shifted remainder is bounded by the processed
dividend prefix: it is at most `2^k - 1` after `k <= 328` bits. Thus it fits the
328-bit representation even when the divisor is near the maximum; no 329th
temporary bit is discarded. Each shift accumulator is at most `65535`, and
subtraction retains its previously documented bounds. Quotient bits are set
only where subtraction succeeds. Three fixed-size arrays hold the quotient,
remainder and trial difference; remainder/difference buffers swap on success,
so no per-bit array or value allocation is required.

**Alternatives.** Knuth-style radix division can reduce iteration work but adds
normalization, quotient estimation and correction cases; there is no controlled
evidence requiring that complexity. A generic/wide-division framework would
expand the approved operand contract. Division is explicitly variable-time;
neither this choice nor a fixed iteration count promises constant-time behavior.

**Evidence and risks.** The Python generator appends quotient/remainder fields
to all 340 records, preserving existing field indices. It adds 64 deterministic
full-width dividends with odd, nonzero divisors varying across 1..316 bits to
exercise multi-limb quotient/remainder patterns. For 332 valid divisors the
pinned C `mp_divmod_schoolbook` independently checks both results; the adapter
trims divisor leading zero words and initializes six-word output buffers.
Eight zero-divisor records carry explicit invalid markers; C division is never
called for them. Common tests check both outputs, strict remainder bounds,
every power-of-two divisor, exhaustive small divisions, zero rejection,
operand preservation and same-instance division. Ordinary JVM tests validate
six retained benchmark shapes against `BigInteger`.

**Migration and limits.** No public ABI, production dependency, wire, toolchain
or release-version change. A separate valid-divisor benchmark preserves the
prior workloads; CI now requires all 53 smoke cases. Full measurements,
numeric budgets, expert cryptographic review and leakage proof remain absent.
`UInt656 / UInt328`, signed division and field reduction remain out of scope.

## Public API and randomness policy for the complete port

Do not publish `sign`/`verify` methods that throw "not implemented", return fixed
values, or use a different signature algorithm. Introduce those APIs only when
their milestone gates pass. Keep encoded public data immutable and copied at
boundaries; secret data needs explicitly owned mutable storage and lifecycle
documentation rather than ordinary data-class copying.

Expose deterministic internal operations taking an explicit 48-byte seed for
interoperability. Production key generation and signing should obtain fresh
seeds through a small, explicit, platform-provided entropy interface, propagate
entropy failures, and clear per-operation expansion state in `finally`. The
mathematical core remains common Kotlin. Never seed from timestamps, UUIDs,
`kotlin.random.Random`, or a fixed test seed. Platform entropy adapters are not
implemented here.

Use structured decode errors for malformed byte input and distinguish
verification rejection from internal invariant/entropy failures. The current
foundation uses explicit `IllegalArgumentException` and `IllegalStateException`
instead of silent defaults. A future verifier may map *specified validation
failures* to `false`, but must not hide arbitrary exceptions behind `false`.

## Interoperability, reproducibility, and security

The current common suite checks independent SHAKE/PRNG/transcript vectors and
wire round-trips for public KAT record `0` at each level. It does **not** check
the KAT signature equation, generate a secret key, or implement NIST's test DRBG.
NIST KAT `seed` initializes an AES-CTR DRBG wrapper: it is not necessarily the
48-byte seed passed directly to algorithm 3.22/3.23. Reproduce the entropy
consumption sequence or capture root seeds in a **test-only** reference harness
before requiring byte-for-byte key/signature matches.

During the port, run a pinned C reference as an external development/test oracle,
not as part of the Kotlin implementation. Export canonical input/output vectors
at each mathematical seam. Keep reproducible vector provenance and checksums,
run the same common tests on JVM, Node.js and both supported native hosts, and
archive the all-level KAT results before declaring algorithm compatibility.
Avoid treating self-consistency (`verify(sign(m))`) as sufficient evidence.

The reference itself is not fully constant-time (specification 8.5 and the root
README's CT harness qualifications). Neither pure Kotlin nor replacing large
integers makes it constant-time. JVM JIT/GC, JS engines and Kotlin/Native require
separate leakage analysis; source-level masks and overwritten arrays alone do
not establish timing resistance or erasure. Keep those limitations in public
documentation, and do not label the finished mathematical port production-ready
without an explicit threat model and independent review.
