# ADR 0011: Checked torsion bases and hints

**Status:** Accepted
**Date:** 2026-10-18

## Context

Algorithms 3.6-3.12 require a differential basis, not merely three valid
projective coordinate pairs. Coordinate construction does not prove curve
membership, exact order, independence or a consistent difference.
Hint decoding must reject an invalid basis unless a later operation proves
the same conditions; this internal layer has no such deferred validator.
The internal basis/hint seams were approved by the user.

## Decision

Use immutable `TorsionBasis<Element>` with a checked factory. Require
`1 <= exponent <= f`, finite coordinates and membership on the supplied
nonsingular curve. For `(X:Z)`, membership is the quadratic residuosity of
`XZ * (X^2 + A*XZ + Z^2)`, where `A` is normalized; no point inversion
is needed for this check.

Check each generator's exact order by doubling to nonzero two-torsion and
then infinity. Distinct two-torsion reductions prove independence.
Verify the supplied difference against equation (8). Its two roots encode
the two relative orientations; either is valid, and the supplied root fixes
the differential basis up to global sign. These checks imply the difference
also has exact order, without a third redundant order computation.

A lower-exponent basis alone does not prove supersingularity or maximality.
At the parameters in this port, a full valid `E[2^f]` basis forces maximality
by the Hasse bound. Subsequent pairing identities must use that full-basis
premise, not the ordinary curve constructor.
Each checked basis retains its normalized Montgomery coefficient.
`requireCurve` rejects reuse as a witness for a different model while accepting
independent projective scalings of the same coefficient. This binds later
maximality and torsion premises without repeating the original point checks.

The special `canonicalE0(curve, exponent)` factory requires `A=0`. Its two
full-order x-coordinates are generated offline using Appendix B.1's scan
`x=1+i*m`, real-first y-root ordering, the point's actual odd order (not
always the full cofactor), and two-torsion independence. Ensure the second
generator lies above `(0,0)`. Ship canonical field encodings, not the C
reference's Montgomery limbs. Reduce the generators before recomputing the
reference-sign difference, then run the same checked-basis validation.

`tools/generate_basis_vectors.py` owns both the shipped coordinate pairs
and `TorsionVectors.kt`. The existing C gate independently compares twelve
bases at exponents `1`, `2`, `8` and `f`, including the reference precomputed
constants. Generated coordinates and fixtures are excluded from formatting
and static analysis only; production coverage has no exclusions.
Six retained JVM cases construct the order-two and full-order bases at every
parameter, including decoding, reduction, reference difference and all
validation. They extend the smoke matrix from 210 to 216.

Expose a typed base-field residue test through the field factories, requiring
a zero imaginary component. Extension-field residuosity cannot substitute:
`-1` is a nonresidue in these base fields but has square root `i` in the
extension. Seventy-two Python Euler-criterion records are cross-checked
against C. Canonical field roots are unchanged; the `p324_3` canonical-root
helper moves to file scope to keep the companion within its function budget.

`BasisHint` preserves every wire byte, including hint one with search index
zero; mathematical validity belongs to reconstruction, not the codec.
Generated hints use positive unsigned search indices. Direct indices below
128 pack the residue bit; every index at least 128 encodes as zero regardless
of that bit, as in algorithms 4.94-4.95. This deliberately does not reproduce
the C generator's rare QR fallback value one. The codec seam tests the
otherwise impractical large-search boundary without inventing curve fixtures.

`TorsionBasis.canonical` implements algorithm 4.94 for nonzero coefficients.
Normalize `A`, select the smallest positive search index, construct the
entangled points, clear the full odd cofactor and reduce their order.
The QR branch tests `1+h^2` in the base field. Both branches test curve
membership before clearing. The owned unsigned counter has the exact field
width; increment accumulators are at most 256. A preceding canonical decode
proves the increment cannot overflow that width. Strict decoding rejects
reaching `p`, before modular wrap, without an arbitrary production search cap.
Maximality is a generation precondition, not a consequence of a lower-order
output. Every returned basis passes the checked factory.

The optional `reconstructionExponent` is the **target order exponent** in
supporting algorithm 4.94 and C's helper, not a count of discarded bits.
Require zero (no adjustment) or `0 < target < exponent`. Select the
compatible difference using the specified projective sign expression and
unchanged reference-sign root; then permute the entangled triple so its
second generator lies above `(0,0)`.
Thirty independently validated affine bases cover both residue branches,
exponents one/intermediate/full and targets one/eight, matching C.

`TorsionBasis.fromHint` implements algorithm 4.95 with immediate mathematical
validation. Zero regenerates from index one, not C's index-128 fallback.
Hint one selects index zero and fails explicitly on a nonzero coefficient;
it remains syntactically valid. Other hints are interpreted directly,
without requiring a minimal index or separately trusting their residue bit.
Off-curve selections fail before clearing; the checked factory rejects
insufficient order, dependence and inconsistent differences. Twenty-seven
independent direct-hint records, including nonminimal indices and signed-byte
boundaries 254/255, agree with C on its common direct-hint domain.

For `A=0`, generation and every decoded hint use the special E0 generators.
An optional reconstruction target also aligns the E0 **difference**, leaving
the two generators unchanged. C's E0 shortcut ignores that target: independent
affine checks demonstrate incompatible differences, including `p664_17` at
source exponent eight/target two. Six records verify the required reduction
identity independently instead of treating that shortcut as a conformance
oracle. Plain `canonicalE0` and target-zero generation are unchanged.

The same generator owns benchmark-only `CanonicalBasisInputs.kt`, containing
public maximal coefficients and direct hints, not output bases. Thirty-six
retained cases cover both coefficient branches, two/full torsion, ordinary
generation, target-compatible generation and direct reconstruction. All
outputs are checked against independent common fixtures before timing.
The complete smoke matrix grows from 216 to 252; no timing baseline is added.
The C checker additionally pins the three `ec_params.c` cofactor definitions,
and both workflow path filters watch them.

## Alternatives

- **Treat any three same-order points as a basis:** rejected; independence
  and differential consistency are separate conditions.
- **Accept points on either quadratic twist:** rejected; the checked factory
  promises membership on the supplied model, not only x-only arithmetic.
- **Defer every validation to a future isogeny caller:** rejected at this
  seam because there is no current deferred-validation witness.
- **Validate the difference's order separately:** unnecessary once the two
  independent generators and the biquadratic identity are checked.

## Risks and migration

Validation is variable-time and may be expensive at full exponent. Retained
workloads and all-parameter evidence accompany canonical generation.
The external C adapter stays one translation unit (over 300 lines)
to exercise the reference's file-local helpers directly without modifying
the pinned sources or adding a test-only C library interface.
The checked basis is internal;
no public ABI, wire format, runtime dependency or release-version change.
No expert cryptographic review, constant-time guarantee or production
readiness is established. Matrices, pairings, complete basis changes,
canonical curve normalization and isogeny chains remain separate work.
