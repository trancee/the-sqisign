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
Milestone 1 remains incomplete: additional wider intermediates, extended
GCD/inversion, square root and primality helpers, and larger parameter sets
still need their own bounds, differential tests and representative performance
measurements before any public signature API is introduced.

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
The multiplication workload adds five cases to the JVM smoke matrix. Signed
arithmetic and field reduction remain separate work.

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
Wide division is recorded separately below; signed division and field reduction
remain out of scope.

## Arithmetic decision 2026-10-16: wide unsigned division

**Context.** Exact 656-bit products must retain both quotient and remainder when
divided by a 328-bit value. Even a product divided by one can exceed 328 quotient
bits; a narrow quotient or silent overflow flag would lose required information.
Unlike narrow division, a shifted trial remainder can require a 329th bit.

**Decision.** `UInt656.divideAndRemainder(UInt328)` returns a full immutable
`UInt656` quotient and immutable `UInt328` remainder, satisfying
`dividend = quotient * divisor + remainder` and `0 <= remainder < divisor`.
Zero is rejected with the same `IllegalArgumentException` reason as narrow
division before any result is produced; neither operand is changed.

The byte-backed wide value uses restoring binary division over all 656 bits.
Two 42-entry radix-256 `IntArray` buffers retain the trial remainder and difference,
and an 82-byte array retains quotient bits. Before shifting, the remainder is
below the divisor, so the shifted value plus one input bit is below
`2 * divisor <= 2^329 - 2`; its extra scratch byte is at most one.
Each shift accumulator is at most `511`; each low-byte subtraction including
borrow is in `[-256, 255]`, and the high-byte difference is in `[-1, 1]`.
All fit exactly in portable `Int`. The high-byte difference, rather than a
truncated narrow borrow, determines whether subtraction succeeds. Afterwards
the remainder is again below the divisor and its extra byte is zero.
No quotient or remainder bits are dropped when constructing the owned results.
Division is variable-time despite its fixed bit-loop count.

**Alternatives.** Extending the narrow division prefix bound to 656 bits is
incorrect: its 328-bit scratch storage can lose the trial remainder's high bit.
Rewriting the existing narrow radix kernel or exposing its private limb storage
would change an already retained workload unnecessarily. A separate byte kernel
matches the existing wide representation, keeps bounds explicit, and leaves
narrow arithmetic unchanged; its different width/representation is intentional,
not a configurable integer framework. Faster normalized radix division remains
a measurement-driven optimization, not a prerequisite for this contract.

**Evidence and risks.** The generator adds 322 full-width records independently
computed by Python integers. The pinned C `mp_divmod_schoolbook` checks all 314
nonzero divisors with twelve-word dividend/quotient storage and six-word
divisor/remainder storage; eight zero-divisor records explicitly mark invalid
division without invoking C division. The adapter's `--wide-division` mode shares
the existing codec and division path, while default mode keeps its original
record format and 340 narrow fixtures.
Common tests verify both results and strict remainder bounds, all 656 quotient
bits, a trial remainder requiring bit 328, every power-of-two divisor, zero
rejection and unchanged operands. Product/factor tests exercise integration with
multiplication for all 332 nonzero narrow fixture factors. Seven JVM workloads
are independently checked against `BigInteger`. A deliberate dropped-extra-bit
mutation fails its focused regression assertion before restoring the correct code.

**Migration and limits.** No public ABI, dependency, toolchain, wire format,
previous vector schema or release-version change. A separate generated vector
list and seven wide-division benchmark cases extend the JVM smoke matrix.
The benchmark includes divisor export and all scratch/result ownership costs.
No controlled baseline, numeric budget, speedup, expert cryptographic review,
constant-time guarantee, field reduction or signed arithmetic is established.

## Arithmetic decision 2026-10-17: unsigned greatest common divisor

**Context.** Milestone 1 needs a greatest common divisor before extended GCD,
inversion or signed integer work. Exact unsigned quotient/remainder already
provides the Euclidean reduction step with independently checked bounds.

**Decision.** Internal `UInt328.gcd(UInt328)` computes the nonnegative GCD,
with `gcd(0, x) = gcd(x, 0) = x` and `gcd(0, 0) = 0`. It repeatedly replaces
the pair `(left, right)` with `(right, left % right)` while `right` is nonzero.
The existing remainder contract ensures strict decrease of each positive right
operand; all values remain within 328 bits, no new accumulator bound is needed,
and no zero divisor is passed to division. Both operands remain unchanged.
Returning an existing immutable value in a zero-operand case is intentional;
storage is not exposed. Iteration count, division branches and zero detection
are variable-time, unsuitable for a constant-time claim.

**Alternatives.** Binary GCD would need shift/trailing-zero primitives not
otherwise needed by this slice. Lehmer's algorithm, used by the C reference,
reduces division cost but introduces coefficient bounds and correction paths.
Neither is justified without controlled evidence that the simpler reuse of
the existing remainder operation is insufficient. Extended GCD and inversion
have additional coefficient/error contracts and are specified as separate
operations.

**Evidence and risks.** The generator owns 441 independent Python `math.gcd`
records: all 340 unsigned pairs, 64 shared-factor pairs, 15 consecutive Fibonacci
pairs and 22 scaled coprime pairs. The external C adapter invokes pinned
`ibz_gcd` with a 329-bit signed bound and zero-initialized storage, preserving
unsigned magnitude and a clear sign bit. This exercises the C implementation's
own zero convention and Lehmer/Euclidean paths, not a copied Kotlin algorithm.
All original narrow/wide vector formats remain unchanged.
Common tests compare both operand orders, exhaustive small divisor search,
every power-of-two/odd-maximum pair, every fitting pair `3 * 2^k, 5 * 2^k`,
same-instance and zero identities, and unchanged operands. Seven retained JVM
benchmark shapes include the largest fitting consecutive Fibonacci pair and
are checked against `BigInteger`.

**Migration and limits.** No public ABI, dependency, toolchain, wire format or
release-version change. The separate GCD vector list and `--gcd` C adapter mode
share the existing checksum-pinned verification gate. CI requires 67 JVM smoke
cases, without a score gate. This is not expert review, a controlled performance
baseline, numeric budget, signed GCD, extended GCD or modular inversion.

## Arithmetic decision 2026-10-17: unsigned modular inversion

**Context.** Milestone 1 needs a canonical modular inverse without first exposing
signed integers or unbounded Bézout coefficients. Exact narrow division and
wide product reduction already have independently checked bounds.

**Decision.** Internal `UInt328.modInverse(modulus)` requires `modulus >= 2`
and coprime operands, returning the unique inverse in `[0, modulus)`.
Distinct stable `IllegalArgumentException` reasons reject invalid moduli before
division and non-coprime inputs after Euclidean termination. Both inputs remain
unchanged; an input above the modulus is reduced, not rejected.

Euclid starts with remainders `(modulus, value % modulus)` and residue coefficients
`(0, 1)`. Each quotient updates the coefficient to
`(previousCoefficient - quotient * coefficient) mod modulus`.
The invariant is `coefficient * value == remainder (mod modulus)`.
All coefficients remain below the modulus; quotient and coefficient are below
`2^328`, so their exact product fits `UInt656` without truncation. Wide division
reduces it before subtraction. Ordered subtraction either directly yields a
nonnegative difference or subtracts the positive difference from the modulus;
these bounds prove every discarded subtraction borrow is zero. Remainders
strictly decrease until zero. A terminal GCD of one establishes the inverse.

**Alternatives.** Reusing signed extended GCD would not cover arbitrary
328-bit moduli with coefficients constrained to signed `Int328`, and would
require modular coefficient correction. Binary inversion can exploit odd
moduli, but would change the approved contract for even moduli and add shifts.
Lehmer or field-specific inversion remains separate measurement-driven work.

**Evidence and risks.** Common tests use 577 independent Python `pow` records
cross-checked by pinned C `ibz_invmod`, including invalid moduli and nonunits.
The C adapter uses nonnegative 329-bit inputs and classifies moduli below two
before invoking C; a zero return marks non-coprime input rather than trusting
its output. Successful records require exact canonical results, inverse identity
and unchanged operands. Exhaustive small brute-force tests and full-width odd/
even boundaries supplement those records. Five JVM benchmark workloads compare
against independent `BigInteger.modInverse` before any timing.

**Migration and limits.** No public ABI, dependency, toolchain, wire schema or
release-version change. Separate inverse vectors and the `--inverse` adapter
mode preserve all existing fixture formats. CI requires 72 JVM smoke cases,
without score gates. Inversion is variable-time and allocation-heavy; this is
not field inversion, extended GCD, expert crypto review, leakage proof, a
controlled performance baseline, or a numeric resource budget.

## Arithmetic decision 2026-10-17: checked bounded signed integers

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

**Context.** Milestone 1 still needs signed integer semantics for later
quaternion/lattice work. This first bounded slice establishes sign, codec,
comparison and exact addition/subtraction without guessing larger algorithmic
bounds or exposing a public signature API.

**Decision.** Internal immutable `Int328` represents `[-2^327, 2^327 - 1]`
with an exact 41-byte two's-complement little-endian codec. All bit patterns
are valid and zero is unique; import/export preserve defensive ownership.
This is an internal integer encoding, not an SQIsign wire format.
Private storage reuses `UInt328` bits plus their derived sign. Comparison orders
different signs directly and same-sign operands by unsigned bit order.

Addition/subtraction reuse the unsigned low result bits, intentionally ignoring
the unsigned carry/borrow only within this explicit two's-complement contract.
For addition, signed overflow occurs exactly when equal-sign inputs produce
the opposite sign. For subtraction, it occurs exactly when opposite-sign inputs
produce a sign different from the left input. These checks distinguish signed
overflow from unsigned carry/borrow and throw operation-specific
`ArithmeticException` reasons without exposing a wrapped result. Direct
subtraction does not negate the minimum signed value, so `minimum - minimum`
correctly returns zero. All limb bounds remain those of the existing unsigned
kernel; no new multiply accumulator or signed machine overflow is introduced.

**Alternatives.** Sign/magnitude with a separate sign and full 328-bit magnitude
would produce a different symmetric range and require a distinct codec/zero
canonicalization policy. A generic arbitrary-width signed framework is not
justified before callers establish bounds. The approved fixed-width,
two's-complement contract instead preserves conventional signed semantics and
explicitly rejects mathematical results outside its range.

**Evidence and risks.** The generator owns 314 records independently computed
with Python signed integers: boundary pairs, radix transitions and SHAKE-derived
full-width operands. Pinned C `ibz_cmp`, `ibz_add` and `ibz_sub` check the results
in a separate `--signed` adapter mode. Imports sign-extend the 328-bit input into
six 64-bit words with a 329-bit signed bound; sums/differences fit 329 signed
bits, and the C operations retain a wider declared bound. Overflow detection
requires bits 327 through 383 to agree before serializing 41 result bytes;
otherwise an explicit marker is emitted. The check is independent of Kotlin's
input/result sign conditions. Common tests cover all sign pairs, both overflow
directions and unchanged inputs, exhaustive small exact arithmetic, codec bits,
sign bytes, invalid widths and defensive ownership. Thirty JVM codec/comparison/
arithmetic workloads across six valid shapes use independent `BigInteger`
expectations before timing.

**Migration and limits.** No public ABI, dependency, toolchain, wire schema or
release-version change. Existing vectors and unsigned operations are unchanged;
a separate signed list/mode extends the same pinned external oracle gate.
CI requires all 102 JVM smoke cases without score thresholds. Execution remains
variable-time, with no expert cryptographic review, leakage proof, numeric
budget or controlled performance baseline. This fixed bound does not establish
all later quaternion/lattice intermediate bounds; larger signed types,
multiplication, division and conversions remain separate. Signed extended GCD
has its own contract below.

## Arithmetic decision 2026-10-17: exact wide signed multiplication

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

**Context.** The signed core needs exact products before any signed division or
quaternion layer. Returning a checked or wrapped `Int328` would not preserve
products required by later operations, notably `minimum * -1`.

**Decision.** Internal `Int328.multiply(Int328)` returns immutable `Int656`,
with exact owned 82-byte two's-complement little-endian import/export.
`Int656` represents `[-2^655, 2^655 - 1]`; all exact-width patterns are valid,
but this slice adds no wide signed operations beyond the codec.
Each input magnitude is at most `2^327`, so every product magnitude is at most
`2^654`, attained by `minimum * minimum`; all products fit without narrowing.

Negative inputs use unsigned modular negation `0 - bits (mod 2^328)` to recover
their exact magnitude. This intentional low-bit conversion handles magnitude
`2^327` without attempting an invalid signed negation. The existing exact
unsigned multiplication kernel retains every magnitude-product bit and its
previously proven `2^30 - 1` multiply-add bound. Different input signs convert
the entire 82-byte result to two's complement, bytewise complement plus carry.
The accumulator is at most `255 + 1 = 256`, exactly representable in `Int`;
the final carry is discarded only by this explicit modulo-`2^656` encoding
conversion (it is one only when negating zero). Zero therefore has a single
encoding even for opposite-sign inputs. Both operands remain unchanged.

**Alternatives.** Narrow checked multiplication would reject valid full products
and require a later wide contract. Multiplying sign-extended full-width operands
would duplicate the unsigned kernel and its bounds. A configurable signed-width
framework is unnecessary for the two concrete existing widths. Reusing unsigned
magnitudes and an opaque byte-backed signed result is the smallest exact slice.

**Evidence and risks.** The existing 314 signed Python-integer records append
full 82-byte signed products; earlier field positions remain unchanged.
Pinned C `ibz_mul` independently checks them in the existing `--signed` mode,
with sign-extended 329-bit inputs and a 657-bit declared output bound.
Common tests check both operand orders, exhaustive small products, every possible
product bit 0..654, minimum-square and minimum-times-negative-one cases,
canonical zero, operand preservation and exported-buffer ownership.
Separate wide-codec tests include bit 655, invalid widths and defensive copies.
Six retained multiplication workloads compare their complete signed values with
JVM `BigInteger`. Operations and sign conversion are variable-time.

**Migration and limits.** No public ABI, dependency, toolchain, SQIsign wire
format or release-version change. Generated fixtures are regenerated by their
Python owner; no reference checksums change. The appended signed-product field
and six new benchmark cases extend the exact CI smoke matrix from 102 to 108,
preserving existing workloads. This is not signed division, wider operand
support, field arithmetic, expert review, leakage proof, a numeric performance
budget or a controlled comparison baseline.

## Arithmetic decision 2026-10-17: truncating signed division

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

**Context.** The signed integer slice needs quotient/remainder semantics before
later integer algorithms. Unsigned division already handles the full magnitude
of the signed minimum; signed division must separately define rounding, remainder
sign and the single unrepresentable narrow quotient.

**Decision.** Internal `Int328.divideAndRemainder(Int328)` returns immutable
`Division(quotient, remainder)`, with quotient truncated toward zero,
`dividend = quotient * divisor + remainder`, and `abs(remainder) < abs(divisor)`.
A nonzero remainder has the dividend's sign; zero has its single canonical
encoding. A zero divisor throws `IllegalArgumentException` before magnitude
division, and `minimum / -1` throws `ArithmeticException` before any result is
returned. Both operands remain unchanged. All paths are variable-time.

Unsigned modular negation obtains exact magnitudes, including `2^327` for the
minimum, reusing multiplication's existing helper. Exact unsigned division
produces quotient magnitude at most `2^327`; a nonnegative quotient of that
magnitude can occur only for `minimum / -1` and is rejected. Negative quotients
allow that magnitude, preserving `minimum / 1`. Remainder magnitude is strictly
below the divisor magnitude, hence below or equal to `2^327 - 1`. Modular
negation restores the quotient sign from the input-sign difference and the
remainder sign from the dividend; it cannot overflow these bounds or create a
second zero representation. No new limb accumulator bounds are introduced.

**Alternatives.** Euclidean division with nonnegative remainder or Python-style
floor division would require quotient/remainder correction and a different
consumer contract. The approved truncation-toward-zero convention matches
Kotlin/JVM signed operators and the inspected pinned C `ibz_div` behavior
despite that source's "Euclidean division" description. Treating `minimum / -1`
as wrapped success would violate the checked signed contract; a wide quotient
belongs to a separate API.

**Evidence and risks.** A separate 570-record list uses Python integer magnitude
division/sign restoration and pinned C `ibz_div`, retaining all prior schemas.
The C adapter imports sign-extended 329-bit values and can preserve the positive
overflow quotient before classifying its 328-bit range. It rejects nine zero
divisors without calling C division; the single overflow record carries an
overflow quotient and mathematical zero remainder. Kotlin rejects the entire
overflow operation, not just one field. Cases include the 314 prior signed
pairs and all four sign combinations for 64 varying-width odd divisors.
Common tests verify exact outputs, explicit rejection reasons, exhaustive small
signed arithmetic, remainder signs, signed-minimum boundaries, same-instance
division and operand/result ownership. Eight retained JVM benchmark shapes
compare both results, reconstruction and strict remainder bounds against
independent `BigInteger` arithmetic.

**Migration and limits.** No public ABI, dependency, toolchain, SQIsign wire
format or release-version change. Existing fixture records and benchmark inputs
are unchanged. CI requires 116 complete JVM smoke cases without score gates.
This is not wide signed division, field reduction, extended GCD, larger operand
bounds, expert review, leakage proof, a numeric performance budget or a
controlled comparison baseline.

## Arithmetic decision 2026-10-17: full-width signed division

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

The approved detailed record is
[`adr/0001-int656-signed-division.md`](adr/0001-int656-signed-division.md).
Internal `Int656.divideAndRemainder(Int328)` reuses the exact unsigned
`UInt656 / UInt328` kernel over unsigned magnitudes and returns an `Int656`
quotient with an `Int328` remainder. It truncates toward zero, the remainder
follows the dividend's sign, and `dividend = quotient * divisor + remainder`.
Zero divisors and `minimum / -1` fail explicitly. The magnitude path preserves
`-2^655` and `-2^327` without signed negation; results remain immutable and
division is variable-time.

The separate 209-record fixture set uses Python integer arithmetic and pinned
C `ibz_div`, with sign-extended 657-bit input capacity. Common tests run it in
four interleaved batches and assert exact quotient/remainder bytes, errors,
and operand preservation. Eight retained JVM shapes compare both results,
reconstruction and strict remainder bounds against `BigInteger`. The CI smoke
matrix grows from 116 to 124 cases. No public ABI, dependency, wire format,
toolchain or release-version change; this does not establish constant-time
behavior, expert review, numeric performance budgets or a controlled baseline.

## Arithmetic decision 2026-10-17: checked wide signed arithmetic

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

The detailed rationale and migration record is
[`adr/0002-int656-checked-arithmetic.md`](adr/0002-int656-checked-arithmetic.md).
`Int656` comparison returns `-1`, `0` or `1`; checked addition and subtraction
return exact immutable values or throw operation-specific `ArithmeticException`
on signed overflow. The implementation uses exact-width two's-complement bytes,
unsigned byte carries/borrows and standard signed overflow conditions; it neither
narrows nor mutates operands. All operations are variable-time.

A separate 209-record list uses Python integers and pinned C `ibz_cmp`,
`ibz_add` and `ibz_sub`, preserving all earlier fixture schemas. The adapter
sign-extends 657-bit values in the reference's 1,920-bit storage and validates
the returned 656-bit bounds. Common tests assert exact results, both overflow
directions, signed ordering and operand/result ownership. Six retained JVM
shapes per operation verify behavior against `BigInteger`; the smoke matrix
grows from 124 to 142 cases. No public ABI, dependency, wire format, toolchain
or release-version changes; this is not constant-time evidence, expert review,
a numeric performance budget or a controlled comparison baseline.

## Arithmetic decision 2026-10-17: unsigned floor square root

`UInt328.sqrtFloor` returns the exact nonnegative integer floor square root
without field reduction or floating-point operations. It starts above the root
at `2^ceil(bitLength/2)` and applies integer Newton updates
`floor((estimate + value / estimate) / 2)` until the estimate no longer
decreases. Zero returns zero directly. The estimate and quotient remain bounded
by `2^164`; their sum fits 328 bits, and a possible addition carry is preserved
by the exact 329-bit halving step. Division and iteration count are variable-time.

A separate 524-record fixture set uses Python `math.isqrt` and pinned C
`ibz_sqrt_floor`: small values, spaced power-of-two boundaries, near-squares
around 64 deterministic 164-bit roots, and 128 SHAKE-derived full-width values.
Common tests additionally check all even power-of-two squares and neighbors
across the root width, the largest fitting root, and JVM benchmark shapes against
`BigInteger.sqrt()`. The C adapter imports the input under a 329-bit positive
bound; the root fits the exact 41-byte output. No public ABI, dependency, wire
format, toolchain or release-version change. This is not modular/field square
root, a constant-time claim, expert review, numeric performance budget, or a
controlled comparison baseline. The smoke matrix grows from 142 to 149 cases.

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

## Arithmetic decision 2026-10-17: signed extended GCD

**Superseded 2026-10-21** by [ADR 0020](adr/0020-bounded-quaternion-integers.md):
`Int328` and `Int656` were removed in favor of the bounded `Ibz`.

The detailed rationale and migration record is
[`adr/0003-int328-extended-gcd.md`](adr/0003-int328-extended-gcd.md).
Internal `Int328.extendedGcd` returns an unsigned nonnegative `UInt328` GCD and
signed `Int328` coefficients satisfying Bezout's identity. It operates on
unsigned magnitudes so `abs(Int328.minimum) = 2^327` is representable. The
`(0, 0)` result has all zero fields; for exactly one zero input, the coefficient
for the nonzero input is its sign. General coefficient selection is not
canonical. Euclidean coefficient updates use exact signed `Int656` products
and checked subtraction before a checked narrow; the final zero-remainder
coefficient is not computed because it is unused and may exceed signed 328 bits.
The result coefficients are sign-adjusted only after the nonnegative-magnitude
algorithm completes.

The generator owns 117 Python integer records: signed boundary pairs, 32
deterministic full-width pairs and a largest-fitting Fibonacci chain. Common
tests compare each GCD with the oracle and verify Bezout identities both for
the generated Python coefficients and returned Kotlin coefficients. The
pinned C `ibz_xgcd` adapter is checked for the same GCD and its returned
coefficients independently satisfy the identity; exact coefficient equality
is intentionally not required because Lehmer batching may choose another valid
pair. Seven JVM benchmark shapes check GCD and identity against `BigInteger`;
the smoke matrix grows from 149 to 156 cases.

No public ABI, dependency, wire format, toolchain or release-version change.
The operation is variable-time and is not intended for secret inputs. It is
not expert cryptographic review, constant-time evidence, a numeric performance
budget or a controlled comparison baseline.

## Arithmetic decision 2026-10-17: prime fields for all parameter sets

The detailed rationale and migration record is
[`adr/0006-field-core-all-parameter-sets.md`](adr/0006-field-core-all-parameter-sets.md).
The internal `Fp324`, `Fp500P27`, and `Fp664P17` types represent canonical
residues modulo the corresponding `p324_3`, `p500_27`, and `p664_17` primes.
All reject byte encodings at or above the modulus and provide addition,
subtraction, negation, multiplication, squaring, inversion, residue testing,
and a verified canonical square root. The `p324_3` implementation reuses
`UInt328`/`UInt656` for encoding and ordering. All three fields multiply and
exponentiate through the shared 16-bit-digit Montgomery `FieldModulus`
([`ADR 0016`](adr/0016-montgomery-field-multiplication.md)).
These are internal operations, not public wire APIs or optimized performance
claims.

Python modular-integer vectors cover 113 `p324_3` operation pairs and 48 pairs
for each wider base field. Six `p324_3` and five per wider prime field nonlinear
vectors exercise inversion, residues and roots. The checker compiles the
reference field implementations and verifies the operations against those
independent records.

## Arithmetic decision 2026-10-17: quadratic fields and canonical controls

`Fp2P324`, `Fp2P500`, and `Fp2P664` implement
`Fp[i]/(i^2 + 1)` with strict real-then-imaginary encodings, arithmetic,
inversion, residue testing, and verified canonical square roots. Roots are
ordered by the encoded value, which compares the imaginary component first,
then the real component. The initial `p324_3` implementation and its
representational choices are recorded in
[`adr/0004-p324_3-base-field.md`](adr/0004-p324_3-base-field.md) and
[`adr/0005-p324_3-extension-field.md`](adr/0005-p324_3-extension-field.md);
ADR 0006 records the completed cross-parameter scope and controls.

The generator owns 64 Python-integer operation pairs and ten nonlinear records
for each extension field, including a deterministic non-residue. The pinned C
adapter cross-checks those outputs. Base and extension field controls define
`isNegative(x)` as `x > -x` in canonical ordering, making zero nonnegative;
`select(first, second, true)` returns the second value; and conditional swap
returns an immutable pair in original or reversed order. Both Boolean values
for these controls are differentially checked against pinned C operations and
Python integer ordering for all six types.

All field arithmetic, ordering, root selection, and Boolean controls are
variable-time and unsuitable for secret inputs. These tests do not establish
constant-time behavior, performance suitability, or independent expert
cryptographic review. The Linux checks cover JVM, JS and Linux Native; the
macOS ARM64 runtime test still requires its supported CI host.

## Curve decision 2026-10-17: initial `p324_3` x-only arithmetic

The next milestone is curves and bases, as specified in section 4.4 and
algorithms 3.5-3.12. The first internal slice implements algorithm 3.5 and the
underlying projective operations in algorithms 4.84, 4.85 and 4.87.
[`adr/0007-p324_3-montgomery-arithmetic.md`](adr/0007-p324_3-montgomery-arithmetic.md)
records the representation, exceptional-input contract, alternatives and risks.

`MontgomeryCurveP324` accepts nonsingular projective coefficients `(A:C)` and
normalizes to `A/C`. `MontgomeryPointP324` rejects `(0:0)`, compares by
projective equality and represents infinity explicitly, without inversion of
zero. Doubling, differential addition and the full unsigned 328-bit scalar
ladder preserve input ownership. Differential addition requires a consistent
finite, nonzero-x difference; membership, supersingularity and torsion order
are not implied by coordinate construction.

Fourteen independent affine Python records, cross-checked by the checksum-
pinned C reference, cover normalized coefficients and the three operations
with projectively scaled inputs and scalar boundaries. The fixture owner is
`python3 tools/generate_curve_vectors.py`; `checkCurveReference` verifies it
and the C outputs as part of `check`. Eight retained JVM workloads extend the
smoke matrix from 156 to 164 cases. Public ABI, wire formats and production
dependencies are unchanged; all operations remain variable-time and unsuitable
for secret inputs.

Milestone 3 is not complete. The subsequent slice below adds three-point/biscalar
ladders and deterministic point differences, before bases/hints, pairings and
change-of-basis, canonical curve normalization and 2-isogeny chains. Promote
the layer to `p500_27` and `p664_17` before declaring the milestone complete.
Expert review, constant-time evidence, numeric performance budgets and a
controlled committed benchmark baseline remain absent.

## Curve decision 2026-10-17: `p324_3` ladders and deterministic differences

[`ADR 0008`](adr/0008-p324_3-ladders-and-deterministic-differences.md)
records algorithms 4.88-4.89 and 4.92, including their exceptional-input
domains. The three-point ladder returns `P+[m]Q`; the biscalar ladder returns
`[m]P+[n]Q`, retaining all unsigned 328-bit coefficients. Zero coefficients
reduce to the single-scalar ladder, while distinct two-torsion bases use the
verified parity rule. Coordinate construction still does not validate a
higher-order basis or a consistent differential triple.

Deterministic differences use the production basis helper's normalization
and minus-root choice. The separate `Fp2P324.squareRootWithReferenceSign()`
implements the exact algorithm 4.35 sign and returns null for nonresidues;
existing canonical field-root contracts are unchanged. Equal x-coordinates
and nonsquare discriminants fail explicitly. Common tests check independent
projective scales, exchanged inputs, distinct infinity inputs and zero
discriminants.

The existing fixture owner now emits 16 affine curve records and 30 raw-sign
root records. The pinned C gate uses the production `basis.c` helper,
positive-coefficient biladder and single-scalar zero-coefficient reductions;
it separately checks two-torsion parity for all six basis orderings. Eight
additional retained JVM cases grow the exact smoke matrix from 164 to 172.
Public ABI, wire formats, dependencies and toolchain are unchanged.

The next curve work includes shared differences and deterministic bases/hints,
then pairings/change-of-basis, canonical curve normalization and 2-isogeny
chains. The shared typed promotion is recorded below. All new operations
are variable-time and unsuitable for secret inputs; expert review,
constant-time evidence and controlled performance budgets/baselines remain
unestablished.

## Curve decision 2026-10-17: shared typed arithmetic for all parameters

[`ADR 0009`](adr/0009-shared-typed-montgomery-arithmetic.md) records the
approved replacement of the concrete curve/point path with typed
`MontgomeryCurve<Element>` and `MontgomeryPoint<Element>`. The existing
`Fp2P324`, `Fp2P500` and `Fp2P664` types and their companions implement
the shared field contracts without changing representation or canonical
root behavior. Wider reference-sign roots verify algorithm 4.35 candidates.

All previous arithmetic and exceptional-domain semantics remain. Scalars
are exact-width unsigned little-endian byte arrays of 41/64/84 bytes:
all 328/512/672 bits are retained, without torsion reduction. Invalid widths
fail explicitly before evaluation; input buffers are neither retained nor
mutated. Existing internal consumers migrate rather than retaining a
concrete alias or a parallel implementation.

The owning generator now emits 48 independent affine curve records and
90 exact-sign root records. The pinned C gate checks every parameter set,
including 72 two-torsion basis/parity cases and width-specific malformed
inputs. Common tests exercise wider arithmetic and scalar bounds.
`MontgomeryBenchmark` replaces the concrete retained harness and adds a
parameter-set dimension. Its 48 curve/root cases grow the exact JVM smoke
matrix from 172 to 204; timing includes typed workload dispatch and already
encoded scalars, not the old scalar export.
No public ABI, wire format, runtime dependency, toolchain or release-version
changes. This remains variable-time experimental arithmetic, without
expert review or controlled performance evidence.

Milestone 3 remains incomplete: bases/hints, pairings,
change-of-basis, canonical curve normalization and isogeny chains need their
own implementation and all-parameter evidence.

## Curve decision 2026-10-18: shared differences

[`ADR 0010`](adr/0010-shared-differences-and-complete-basis-changes.md)
records algorithm 4.93 at the existing internal curve seam. Consistent
common translates determine the desired difference by common-root
elimination. The operation preserves projective scales and supports valid
infinity results, but rejects rank-deficient `(0:0)` output explicitly.
It does not establish membership, consistency or a complete basis-change
domain. The existing deterministic difference reuses the same equation (8)
coefficients without changing its exact root choice.

Twenty-four independent full-affine Python records are cross-checked against
the pinned C `shared_difference_point`, including infinity and degeneracy.
Six retained JVM cases extend the smoke matrix from 204 to 210. The checker
pins `biextension.c`, and CI watches it. Public ABI, wire formats, dependencies
and toolchain remain unchanged; all operations are variable-time.

The user approved a complete affine cross-difference helper specifically for
the subsequent basis-change layer, rather than copying the C path's undefined
coincident-basis/exponent-one intermediates. Ordinary ladders remain x-only.
That helper and the remaining bases/pairings/normalization/chains still need
implementation; no production-readiness claim follows from this slice.

## Curve decision 2026-10-18: checked bases and the special E0 basis

[`ADR 0011`](adr/0011-checked-torsion-bases-and-hints.md) records the
`TorsionBasis` validation boundary: finite membership, exact generator order,
distinct two-torsion reductions and a consistent differential triple.
Only a full basis supplies the maximality premise needed by later pairing
identities.

The special `canonicalE0(curve, exponent)` factory requires `A=0`.
`tools/generate_basis_vectors.py` independently reconstructs Appendix B.1's
generators using real-first y-root ordering and actual odd point order,
without blindly multiplying cofactor 27. It owns shipped canonical
coordinate encodings and twelve common oracle records. The C gate pins all
three reference E0 constant files and the original precomputation algorithm.
Generators are reduced before their reference-sign difference is computed.
Generated production coordinates remain covered, with no production exclusions.
Six retained JVM cases include complete E0 construction/validation at order
two and full order, extending the smoke matrix from 210 to 216.

The field factories provide a distinct base-field residue operation, rejecting
nonzero imaginary components; 72 Euler-criterion/C records cover the adapter.
The hint codec preserves every byte syntactically and implements the normative
zero fallback for all generated indices at least 128, including square `A`.
It does not inherit the C generator's rare QR fallback value one. No canonical
field-root behavior changes.

General-curve `TorsionBasis.canonical` and `fromHint` implement algorithms
4.94-4.95 with immediate checked-basis validation. Thirty affine generation
records and 27 direct reconstructions agree with C at all parameters, including
both coefficient-residue branches, target exponents one/eight, nonminimal
indices and signed-byte boundaries. The optional reconstruction exponent names
the target order, not a discarded-bit count. Owned canonical-width search bytes
avoid `Int` overflow; strict field decoding rejects reaching `p` before wrap.
Zero hints regenerate from one, and hint one is mathematically invalid on
nonzero coefficients. No claimed residue bit replaces basis validation.

The E0 path preserves its fixed generators and ordinary difference contract.
For a requested reconstruction target it also aligns the full difference;
six independent affine records verify the reduction identity. C's E0 shortcut
ignores this target and is not a conformance oracle for that adjustment.
The generator also owns benchmark-only canonical inputs. Thirty-six retained
generation, reconstruction-target and direct-hint cases cover both residue
branches and two/full order at every parameter; the exact smoke matrix is
now 252 cases. C cofactor sources are pinned and watched by CI.

Pairings/basis changes, normalization and isogeny chains remain incomplete. Public ABI, wire formats,
dependencies and toolchain are unchanged; operations remain variable-time,
without expert review or controlled performance evidence.

## Curve decision 2026-10-18: bounded torsion scalars and matrices

[`ADR 0012`](adr/0012-torsion-scalars-and-basis-matrices.md) records the
internal `TorsionScalar` ring modulo `2^e`, distinct from unreduced ordinary
ladder scalars. Exact-width decoding rejects unused bits; arithmetic
explicitly reduces at the declared bit boundary. Bounded byte carries and
Newton odd inversion retain every bit up to exponent 664. Mixed exponents,
even inverses and ladder exports above a parameter's torsion fail explicitly.

`TorsionMatrix` retains the specification's row-major/column-action convention,
computes determinants and inverses, and implements algorithm 3.10's single
global sign, including zero and self-negative entries. The independent Python
owner supplies 462 scalar and 276 matrix records, all cross-checked against
pinned C by `checkTorsionRingReference`. The C adapter explicitly reduces
the reference normalization's negated-zero aliases before comparing ring
residues; Kotlin outputs are canonical. Common malformed and boundary tests
retain strict ownership and failure reasons.

Thirty-six retained cases add six operations at two/full torsion for each
parameter, with independent JVM workload checks; the smoke matrix is now 288.
Only generated fixture formatting/static-analysis exclusions are added.
Production coverage exclusions, ordinary ladder behavior, public ABI, wire
formats, toolchain and dependencies are unchanged. Operations remain
variable-time without expert review or controlled performance evidence.
Pairings, complete basis actions/changes, canonical normalization and isogeny
chains remain to be implemented.

## Curve decision 2026-10-18: prepared power-of-two logarithms

[`ADR 0013`](adr/0013-prepared-power-of-two-discrete-logs.md) records
algorithm 4.37's balanced decomposition at the positive-exponent basis seam.
`PowerOfTwoDiscreteLog.prepare` validates the exact generator order and
precomputes inverse powers using conjugation on this norm-one subgroup.
`solve` checks membership at its leaves and returns a strict `TorsionScalar`,
with no narrowing of high bits or mutable state shared between solutions.

The independent Python owner `tools/generate_pairing_vectors.py` generates
57 field-power records and benchmark-only public roots. `checkCurveReference`
checks both outputs and every logarithm against pinned C; the external
adapter validates malformed input, exact generator order and membership before
calling C's assertion-based routine. Twenty-four retained JVM cases include
zero/full-width coefficients and two/full torsion across all parameters,
with separate prepared and fresh-preparation timed boundaries. The complete
smoke matrix is now 312 cases.

Public ABI, wire formats, field representations, dependencies and toolchain
are unchanged. All arithmetic remains variable-time and lacks expert review
or controlled performance evidence. Tate pairings, complete basis actions/
changes, canonical curve normalization and isogeny chains remain incomplete.

## Curve decision 2026-10-18: witnessed Tate pairings

[`ADR 0014`](adr/0014-maximal-domain-tate-pairings.md) records algorithm
4.104's checked maximal-curve domain and complete exceptional behavior.
`TorsionBasis` retains its normalized coefficient; `TatePairing.prepare`
requires a matching full basis. Evaluation validates membership, divisibility
order and a consistent differential triple before exceptional identities.
It reuses the witness's already-proved facts without trusting unknown points.

Finite inputs are normalized before exact cubical arithmetic, with no
second-x inversion at exponent one. Infinity/self and zero-two-torsion
identities are restricted to the witnessed domain. First `T0=(0,0)` gives
the second x-coordinate's quadratic character. Second `T0` gives one below
full exponent, or the first x-coordinate's character at full exponent.
This does not erase the nontrivial `e=f-1` torsion restriction.

The independent affine shifted-Miller oracle owns 180 all-parameter records,
including both nonzero coefficient branches and exceptional cases. Fifty-seven
generic-domain outputs also agree with C exactly. The external adapter groups
pairing/logarithm modes in one owned header to preserve single-translation-unit
access to pinned file-local helpers without exceeding source budgets.
Twelve retained JVM cases cover reused and fresh pairing preparation,
growing the exact smoke matrix from 312 to 324.

Public ABI, wire formats, runtime dependencies, toolchain and the Node test
timeout are unchanged. Arithmetic remains variable-time, without expert
review or controlled performance evidence. Complete basis actions/changes,
canonical normalization and isogeny chains remain incomplete.

## Curve decision 2026-10-18: complete basis actions and changes

[`ADR 0015`](adr/0015-complete-basis-actions-and-changes.md) records
invertible column action and the approved affine cross-difference helper
around cubical pairings. `TorsionBasis.applyMatrix` retains the supplied
differential orientation, including the difference of its output columns.
`BasisChange` uses a curve-bound full reference and a checked target,
recovers all four coefficients using one prepared logarithm, and normalizes
only one global matrix sign. Ordinary ladders are unchanged.

The independent owner `tools/generate_basis_change_vectors.py` generates
affine expected points, normalized known matrices and one-operation common
runtime declarations. The external curve gate checks their content, all 369
action coordinates and 51 generic C basis changes. Algorithm 3.9's
`setChangeOfBasisMatrix` additionally returns the composed normalized matrix
and both target-compatible hints. Twelve independent affine/integer cases
and four boundary tests pass on JVM; three generic composed C checks use
the C canonical references, changes and inverse-basis actions with Python
integer bookkeeping. All 246 transformation
cases and ten boundary tests pass on JVM, with static/compiled ABI checks.
Twenty-four new retained action, prepared/fresh change and correspondence
cases extend the required smoke inventory to 348; fresh smoke is pending.
Full-width `p664_17` cases agree on
JVM, but single-operation cold Node tests exceeded the former 30-second
limit. Both subsequently pass under the approved budget. The user approved
continuing correctness-first with measured gate
budgets, initially 300 seconds per Node test; no cases are skipped.
The full cold `p664_17` correspondence later exceeded that bound
(`314.877s` before timeout), and the limit temporarily rose to 600 seconds.
The user then rejected recurring long gates. Montgomery field multiplication
([`ADR 0016`](adr/0016-montgomery-field-multiplication.md)) removed the root
cause, and Node returns to the original 30-second limit with every check
retained.
This layer remains under verification; no all-parameter platform completion
is claimed.

Canonical curve normalization and isogeny chains were completed
subsequently (below).
No public ABI, wire format, dependency, toolchain,
production coverage exclusion, constant-time guarantee or controlled
performance baseline changes.

## Curve decision 2026-10-19: normalization and two-isogeny chains

[`ADR 0017`](adr/0017-normalization-and-two-isogeny-chains.md) completes
milestone 3. Internal `MontgomeryCurve.normalize` implements algorithm 4.96,
`FourIsogeny` algorithms 4.97-4.98, and `MontgomeryCurve.twoIsogenyChain`
algorithm 4.99 with the empty point list that every specification and C caller
uses. The chain returns only the normalized codomain; point images would lie
on the unnormalized model and are not exposed. Every four-isogeny kernel must
have exact order four and must not lie above `(0:1)`; lengths must be
positive, even and at most `f`. Algorithm 3.5 is the existing `A/C`
coefficient.

`tools/isogeny_oracle.py` is an independent affine oracle (explicit
isomorphism enumeration, full Vélu, `sqrt(e)`-checkpoint chains), and
`tools/generate_isogeny_vectors.py` owns `ec/IsogenyVectors.kt` and
benchmark-only `IsogenyInputs.kt`. Per parameter set, 12 normalizations,
42 four-isogeny images and 15 chains (lengths `2`, `8`, `lambda`, and on `E0`
also the response length and `f-2`) pass on every runtime and against pinned C
`isog.c`/`normalize.c`, with rejections and malformed C inputs. Nine retained
`IsogenyBenchmark` cases extend the smoke inventory to 357.

Milestone 3 is complete for `p324_3`, `p500_27` and `p664_17`. Everything
remains variable-time and without expert review; the 521-line
`tools/check_unsigned_reference.py` still exceeds the preferred file size.
No public ABI, wire format, dependency or toolchain changes.

## Isogeny decision 2026-10-19: dimension-two theta chains

[`ADR 0018`](adr/0018-dimension-two-theta-chains.md) completes milestone 4.
Internal `thetaChain(length, curves, kernel, points, mode)` implements
algorithm 4.133 and its gluing (4.117-4.119), generic-step and splitting
(4.124-4.127) pieces, following the pinned `src/hd/ref` strategy, check order,
`(x : z)`/`(x : y)` factor order and diagnostics in all four C modes. It
returns a sealed codomain or rejection outcome. Lengths must be in `2..f-2`,
kernel components must be finite and not `(0 : 1)`, and pushed couple points
need a component at infinity. Kotlin rejects a failed gluing immediately where
C continues on uninitialized state, and rejects `(0 : 0)` split images where C
reports success.

`tools/hd_oracle.py` is an independent Python oracle; `tools/hd_reference.c`
captures every public KAT verification chain through pinned C
`protocols_verify` (`tools/hd_kernels.txt` keeps the selected kernels);
`tools/generate_hd_vectors.py` owns `hd/ThetaChainVectors.kt` and
benchmark-only `ThetaChainInputs.kt`. `checkHdReference` checks the capture,
vector currency and every chain record against C, including the gluing-theta,
gluing-torsion, generic, splitting-product and degenerate-image rejections of
seven malformed p324 kernels. Three retained `ThetaChainBenchmark.verify` cases
extend the smoke inventory to 360.

Milestone 4 is complete for `p324_3`, `p500_27` and `p664_17`. Everything
remains variable-time and without expert review. No public ABI, wire format,
dependency or toolchain changes.

## Verification decision 2026-10-20: public signature verification

[`ADR 0019`](adr/0019-public-signature-verification.md) completes milestone 5.
The first public cryptographic API is `SqiSign.verify(publicKey, message,
signature)`. It implements algorithm 3.24 in the pinned `protocols_verify`
order and returns `false` for every invalid key or signature. It throws only
when the key and signature belong to different parameter sets. Canonical
lengths, field representatives and response padding are rejected earlier, by
`WireFormat`. The hint and matrix-action departures from C are inherited from
ADRs 0011 and 0015 and change no observed verdict.

The JVM-only `jvmKatTest` task verifies all 300 public KAT signatures. It also
replays `tools/verify_mutations.txt`: 168 canonical patches of KAT records 0-3
per level, covering messages, challenges, hints, matrices and curves. The C
verdict for each patch comes from `tools/check_verify_reference.py`
(`checkVerifyReference`), which runs the checksum-pinned `protocols_verify`,
and every Kotlin verdict must match it. Common tests on every runtime verify
KAT 0 at each level and reach every verifier rejection path. A deterministic
32-case p324 bit-flip fuzz must return `false` without throwing. Three
retained `VerifyBenchmark.verify` cases extend the smoke inventory to 363.

Milestone 5 is complete for `p324_3`, `p500_27` and `p664_17`. Everything
remains variable-time and without expert review. The public ABI gains
`SqiSign`. The wire format, dependencies and toolchain are unchanged.

## Quaternion decision 2026-10-21: bounded `ibz_t` integers

[`ADR 0020`](adr/0020-bounded-quaternion-integers.md) begins milestone 6 with
its first slice. Internal immutable `Ibz` ports the pinned `ibz_t` from
`src/mp/ref/lvlx/mp.c`. Values carry C's bit length and two's-complement
words, and the per-level capacity is 1920, 2560 or 3520 bits. Bit-length
rules, truncating division, logical right shift and bounded truncation follow
C. Checked addition, subtraction, multiplication and left shift throw
`ArithmeticException` where C wraps at capacity. `parse` returns `null` where
`ibz_set_from_str` would wrap. `Int328`, `Int656` and their tests, benchmarks,
oracle modes and fixtures are deleted, which supersedes ADRs 0001-0003.

`tools/generate_ibz_vectors.py` independently models 944 records over all
three capacities. `checkIbzReference` (`tools/check_ibz_reference.py` and
`tools/ibz_reference.c`) requires the checksum-pinned C to reproduce each
record, including its wrapped value for overflow records. Common tests reach
every Knuth D branch, and coverage is 100%. Eighteen retained `IbzBenchmark`
cases replace 77 legacy signed cases, so the smoke inventory changes from 363
to 304.

Milestone 6 remaining slices, in order:

1. number theory (GCD/XGCD, CRT, Legendre, modular inverse and square root,
   primality);
2. integer-only LLL;
3. quaternion algebra elements and lattices;
4. ideals.

Everything remains variable-time and without expert review. No public ABI,
wire format, dependency or toolchain changes.

## Quaternion decision 2026-10-19: number theory

[`ADR 0021`](adr/0021-quaternion-number-theory.md) completes the second slice
of milestone 6. Internal `Ibz` extensions port `ibz_gcd`, `ibz_xgcd`,
`ibz_invmod`, `ibz_crt`, `ibz_legendre`, `ibz_invmat`, `ibz_sqrt_floor`,
`ibz_pow_mod`, `ibz_sqrt_mod_p`, `ibz_sqrt_m1_mod`,
`ibz_sqrt_m1_mod_verified` and `ibz_probab_prime`, on unbounded natural words
with C's result bit lengths and random-byte order. Classical Euclid replaces
Lehmer's algorithm and gives the same cofactors. Odd-modulus residues stay in
Montgomery form and multiply over 16-bit `Int` digits, whose multiply-adds stay
below `2^32`, so Kotlin/JS needs no boxed `Long` arithmetic. `ibz_sqrt_m1_mod`'s
unconverted Montgomery base `x / R` is reproduced explicitly. Inputs on which
C asserts, loops or wraps a full-width quotient throw
`IllegalArgumentException`.

`tools/generate_number_theory_vectors.py` independently models 711 records,
including the exact random bytes each randomized record consumes.
`checkIbzReference` now checks 1655 records against pinned C, which includes
the pinned `modqx.c`. Precondition and composite tests bring coverage to
100%. Twelve retained `IbzNumberTheoryBenchmark` cases are checked against
`BigInteger`, so the smoke inventory changes from 304 to 316.

Remaining milestone 6 slices: integer-only LLL, quaternion algebra elements
and lattices, then ideals. Everything remains variable-time and without
expert review. No public ABI, wire format, dependency or toolchain changes.

## Quaternion decision 2026-10-19: fixed-point lattice integers

[`ADR 0022`](adr/0022-fixed-point-lattice-integers.md) starts the LLL slice
with the `ibz_ct_*` fixed-point layer of the pinned `mp.c`. Internal `Ibz`
extensions `shiftSigned`, `highProduct`, `scaledProduct`,
`fixedPointReciprocal` and `roundShiftWords` port `ibz_ct_shift`/`_shl`,
`ibz_ct_highmul_p`/`_s`, `ibz_ct_fp_recip` and `ibz_ct_round_shift_limb`. Each
computes the exact value from unbounded words and then narrows as C does. The
mask helpers (`ibz_ct_lt_mask`, `ibz_cneg`, `ibz_cswap`, `ibz_set_bound_ct`,
`ibz_bitsize_ct`, `ibz_mul_by_int_and_set_bound`, `ibz_cmp_int32`) map to
existing operations.

`tools/generate_fixed_point_vectors.py` independently models 422 records at
each level's `FP_P_BITS` (640, 832 and 1152), and `checkIbzReference` now
checks 2077 records against pinned C. Precondition tests bring coverage to
100%. Six retained `IbzFixedPointBenchmark` cases are checked against
`BigInteger`, so the smoke inventory changes from 316 to 322.

Remaining milestone 6 work: the dimension-2, dimension-4 and Gaussian lattice
reductions, quaternion algebra elements and lattices, then ideals. Everything
remains variable-time and without expert review. No public ABI, wire format,
dependency or toolchain changes.

## Quaternion decision 2026-10-19: dimension-2 lattice reduction

[`ADR 0023`](adr/0023-dimension-two-lattice-reduction.md) ports the
production dimension-2 path of the pinned `lehmer_xgcd.c` and `integers.c`:
`partial_xgcd_msb`, `ct_apply_matrix` and `partial_xgcd_lehmer` behind
`sumOfSquares` (`quat_lll_dim2_sumofsquares`), together with
`ibz_cornacchia_prime`, `ibz_rounded_div` and `ibz_sum_two_squares`. The
machine-word windows, unsigned compares and `int32` transforms are
reproduced exactly, so Cornacchia returns C's vector and not just some
representation. The test-only `quat_lll_dim2_short_basis`,
`sizered_lehmer`, `sizered_msb` and `ibz_generate_random_prime` are not
ported.

`tools/generate_lattice_vectors.py` independently models 291 records, and
`checkIbzReference` now checks 2368 records against pinned C, which includes
the pinned `lehmer_xgcd.c`, `integers.c`, `lll_config.h` and `lll.h`.
Precondition, zero-window and wrong-root tests bring coverage to 100%. Six
retained `CornacchiaBenchmark` cases are checked against `BigInteger`, so the
smoke inventory changes from 322 to 328.

Remaining milestone 6 work: the dimension-4 and Gaussian lattice reductions,
quaternion algebra elements and lattices, then ideals. Everything remains
variable-time and without expert review. No public ABI, wire format,
dependency or toolchain changes.

## Quaternion decision 2026-10-20: dimension-2 Gram kernel

[`ADR 0024`](adr/0024-dimension-two-gram-kernel.md) ports the pinned
`lll_lg2.c`, the binary-form kernel of the dimension-4 reduction:

- `quat_lll_materialise_block` truncates the LDL block
  `(D_k, mu D_k, D_{k+1} + mu^2 D_k)` to `GRAM_TOT_BITS`;
- `quat_lll_gram_lehmer` runs Lehmer Lagrange-Gauss passes, including the
  large-gap pre-scaling, through `partial_gram_msb` and `fast_log2_ratio`.

`LllConfig` derives the `lll_config.h` bounds and `lg_outer_its_for_round`
for each parameter set. The 64-bit windows, unsigned compares and wrapping
`int32` coefficients are reproduced exactly.

`tools/generate_gram_vectors.py` independently models 171 records and checks
every reduction for unimodularity and exactness. `checkIbzReference` now
checks 2539 records against pinned C, adding the pinned `lll_lg2.c`,
`lll_lg2.h` and `quaternion_constants.h`. Precondition tests bring coverage to
100%. Six retained `GramBenchmark` cases are checked against `BigInteger`, so
the smoke inventory changes from 328 to 334.

Remaining milestone 6 work: the dimension-4 driver
(`quat_lll_dual_reduce_ideal`), the Gaussian reduction, quaternion algebra
elements and lattices, then ideals. Everything remains variable-time and
without expert review. No public ABI, wire format, dependency or toolchain
changes.
