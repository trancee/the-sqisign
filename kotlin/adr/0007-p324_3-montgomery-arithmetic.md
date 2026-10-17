# ADR 0007: Initial `p324_3` Montgomery arithmetic

**Status:** Accepted
**Date:** 2026-10-17

## Context

The field-core milestone is complete. The next boundary in `PORTING.md` is
curves and bases: SQIsign v3 section 4.4 and algorithms 3.5-3.12. This first
slice supplies the projective arithmetic underlying those operations, not
the whole milestone. The test seams are the internal curve/point contracts,
confirmed before implementation; no public curve or signature API is added.

## Decision

Use immutable `MontgomeryCurveP324` over `Fp2P324` for `(A:C)`, requiring
`C != 0` and `A^2 != 4C^2`. `montgomeryCoefficient()` returns `A/C`, as in
algorithm 3.5. These checks establish nonsingularity, not supersingularity.

Use immutable `MontgomeryPointP324` for `(X:Z)`, rejecting `(0:0)`.
Every nonzero `(X:0)` represents infinity. Equality is projective cross-product
equality; `affineX()` explicitly returns null for infinity rather than
inverting zero. Coordinates alone establish neither curve membership nor order.

Implement doubling and differential addition using section 4.4.1,
algorithms 4.84-4.85. Addition requires points on the same curve/twist with a
consistent difference; it explicitly rejects differences at infinity and
`(0:1)`. It is not a general point-addition or basis-validation operation.
Any undefined `(0:0)` output is rejected by the point constructor.

The unsigned `UInt328` scalar ladder follows algorithm 4.87 with separate
doubling/addition operations. It accepts zero as infinity and retains all
328 scalar bits, including leading zero bytes. Infinity and `(0:1)` are
handled by scalar parity because differential addition excludes those
differences. All other two-torsion points use the ordinary ladder.
Curve doubling constants are prepared once without normalizing or changing
the caller's coefficient representation. Operations preserve their inputs.

## Alternatives

- **Affine production arithmetic:** rejected because each group operation
  would require inversion and abandon the reference's x-only boundary.
- **A generic cross-parameter curve framework:** deferred until promoting
  this slice establishes the concrete shared requirements. No wider-field
  adapter or speculative abstraction is introduced.
- **A fused `xDBLADD` kernel:** deferred; composing the two verified
  operations keeps this first ladder small. No relative speed claim is made.
- **Treat every coordinate pair as a validated curve point:** rejected;
  x-only coordinates do not prove membership, torsion order, or a valid basis.
- **C/JNI production interop:** rejected; the common implementation remains
  dependency-free, with C solely an external development oracle.

## Risks and evidence

Fourteen deterministic records use independent full affine Python group-law
calculations with y-coordinates and arbitrary-precision modular integers.
The common tests compare normalized coefficients, doubling, differential
addition and scalar multiplication, including independently scaled inputs,
zero/byte-boundary scalars, bit 327, maximum and alternating 328-bit scalars.
The C adapter checks the same results against revision
`f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`, with source and transitive project
headers SHA-256-pinned. It normalizes the C ladder's `A24` before use.
Adapter checks reject malformed, noncanonical, truncated, singular and
exceptional inputs. Common boundary tests additionally cover infinity,
all two-torsion points of `A=0`, order-four points and explicit failure reasons.

Everything is variable-time and unsuitable for secret inputs. Agreement
between Python, C and Kotlin is not expert cryptographic review or a proof of
constant-time execution. Retained JVM workloads measure executability only:
no numeric performance budget or committed controlled-measurement baseline
exists. JS/Native timing and leakage remain unmeasured.

## Migration and remaining scope

No public ABI, wire format, runtime dependency, toolchain or release version
changes. `generate_curve_vectors.py` owns `CurveVectors.kt`; it is excluded
only from formatting/static analysis, not execution or fixture checks.
`checkCurveReference` joins `check`, using the host C compiler and a temporary
external executable. CI watches its new reference inputs. Eight additional
JVM smoke cases retain coefficient normalization, doubling, addition and
short/full-width multiplication, growing the matrix from 156 to 164.

This is only the `p324_3` arithmetic slice. Three-point/biscalar ladders,
deterministic differences, bases/hints, pairings/change-of-basis, normalized
curve choices, isogeny chains and wider parameter sets remain unimplemented.
There are no placeholder implementations for those contracts.
