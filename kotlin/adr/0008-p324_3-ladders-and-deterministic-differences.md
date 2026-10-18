# ADR 0008: `p324_3` ladders and deterministic differences

**Status:** Accepted
**Date:** 2026-10-17

## Context

The first curve slice in ADR 0007 supplies x-only doubling, differential
addition and a single-scalar ladder. The next curves-and-bases slice needs
SQIsign v3 algorithms 4.88, 4.89 and 4.92. Deterministic differences depend
on the exact sign selected by algorithm 4.35, not the canonical smaller root
specified by ADRs 0004-0006. The internal curve/point and reference-sign root
test seams were confirmed before implementation.

## Decision

Preserve every existing canonical `squareRoot()` contract. Add the explicitly
named `Fp2P324.squareRootWithReferenceSign()`, implementing algorithm 4.35
with the algorithm 4.20 modular-power sign and verifying the final square.
It returns null for nonresidues, including when the norm has no base-field
root. A fixed `(p-3)/4` base-field power reuses the existing exponentiation
kernel. Conjugation stays beside the private extension-field components.

Add `threePointMultiply(P, Q, P-Q, scalar)` to the existing curve seam.
Algorithm 4.88 scans all 328 scalar bits low-to-high, composing the verified
doubling and differential addition operations. Require finite, nonzero
coordinates in all three operands, even for scalar zero; intermediate
undefined coordinates still fail through the existing point/addition checks.
This deliberately states a stricter initial domain than the C wrapper's
difference-only check. Inputs must be a consistent triple on one curve/twist;
the operation does not validate that precondition.

Add `biscalarMultiply(P, Q, P-Q, m, n)` using the recoding and evaluation
of algorithm 4.89 for positive coefficients, retaining all 328 bits.
Zero coefficients reduce to the existing single-scalar ladder. This avoids
the C biladder's wrapped-zero recoding, which assumes a torsion basis and
does not give the general group-law result on arbitrary curve points.
All input points must be finite, including for zero coefficients.
An operand with x-coordinate zero requires the three distinct nonzero
two-torsion points; verify this by doubling and projective inequality,
then select the result by scalar parity. For positive coefficients outside
that case, the initial sum must also have finite, nonzero coordinates.
These are arithmetic-domain checks, not general basis validation.

Add `deterministicDifference(P, Q)` using the biquadratics and normalization
of algorithm 4.92. Its scale is
`C * conjugate(C) * conjugate(Bzz)`, algebraically
`C * (conjugate(C) * conjugate(Xp*Zq-Zp*Xq))^2`.
Return the minus-root solution using the reference-selected root.
Equal x-coordinates and nonsquare discriminants fail with distinct
`IllegalArgumentException` reasons. Distinct infinity inputs and zero
discriminants retain their unique valid result. The selected affine result
is unchanged by independent projective rescaling or exchanging the inputs;
it is one of `x(P-Q)` and `x(P+Q)`, not an orientation inferred from x alone.

## Alternatives

- **Change canonical field roots:** rejected because it would break an
  established contract and still obscure the basis sign requirement.
- **Use the C test-only difference helper:** rejected because its scale and
  plus-root convention differ from the production basis helper.
- **Port raw wrapped-zero biladder recoding for all points:** rejected by
  independent affine cases; the general arithmetic seam has no proven
  torsion-order bound. The already-verified single ladder handles zero.
- **Introduce another biladder or a fused doubling/addition kernel:**
  rejected for this slice in favor of the confirmed reference algorithms and
  existing verified operations. No relative speed claim is made.
- **Silently accept exceptional triples or imply a validated basis:**
  rejected; coordinate and arithmetic-domain checks do not establish the
  same-curve, difference-consistency, supersingularity or higher-order basis
  preconditions.

## Risks and evidence

The Python oracle uses full affine points and slopes for ladder results.
Sixteen records cover independently scaled curves/points, zero coefficients,
all coefficient parities, bit 327, maximum and alternating full-width scalars.
Its deterministic-root choice is additionally checked against both affine
group-law solutions. Thirty root records check exact signs and nonresidues.
The C gate pins revision `f417ebd4c2c336ad6a9feb6f9255f95fc03acc41`,
including the production `basis.c` helper and its header. It compares positive
biladders with `ec_biscalar_mul`, zero coefficients with `ec_mul`, and
two-torsion parity results with the C one-bit basis branch across all six
basis orderings. Common tests cover the exceptional-input reasons and
projective invariance without claiming validated higher-order bases.

All operations are variable-time and unsuitable for secret inputs. Agreement
with Python/C is not expert cryptographic review, constant-time evidence or
production readiness. Retained workloads establish executability only:
numeric performance budgets and a controlled committed baseline remain absent.

## Migration and remaining scope

No public ABI, wire format, runtime dependency, toolchain or release version
changes. The existing curve generator owns the expanded `CurveVectors.kt`,
and `checkCurveReference` checks both curve and raw-sign root outputs.
CI additionally watches `basis.c`. Four operations in two scalar shapes add
eight JVM smoke cases, growing the exact matrix from 164 to 172.

Milestone 3 remains incomplete: shared differences, bases/hints,
pairings/change-of-basis, canonical curve choices and isogeny chains still
need implementation and evidence. [ADR 0009](0009-shared-typed-montgomery-arithmetic.md)
records the subsequent shared typed cutover and wider-field arithmetic.
