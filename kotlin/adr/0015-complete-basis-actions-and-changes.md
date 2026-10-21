# ADR 0015: Complete basis actions and changes

**Status:** Accepted mathematical strategy; implementation under verification
**Date:** 2026-10-18

## Context

Algorithms 3.7-3.9 require basis matrices, including coincident generators,
exponent one and one global sign. ADR 0010 records the user-approved complete
affine cross-difference strategy, rather than the C reference's partial
resultant path. ADRs 0011-0014 establish checked bases, bounded coefficients,
prepared logarithms and the witnessed maximal pairing domain.

## Decision

`TorsionBasis.applyMatrix` implements algorithm 3.7's column action:
`R=aP+cQ`, `S=bP+dQ`, and the differential coordinate represents
`(a-b)P+(c-d)Q`. Require curve binding, the same coefficient exponent and
an odd determinant, in that order. A matching invertible matrix preserves
the checked basis invariants; retain that proof instead of rechecking the
entire output. Ordinary ladders remain x-only and retain their existing
unsigned scalar contract.

`BasisChange.prepare` requires a matching checked full basis through
`TatePairing.prepare`. It retains immutable reference lifts and the full
pairing generator. `between` requires a checked target on the same model
and returns the single-global-sign-normalized matrix of algorithm 4.106.
Neither preparation nor solving changes the input bases.

Lift each finite differential basis consistently. At exponent one both
y-coordinates are zero. Otherwise choose a verified reference-sign root for
the first y-coordinate and derive the second from the supplied difference.
For distinct x-coordinates, use the affine group-law difference. Equal
x-coordinates with equal y give infinity; opposite y give the existing
projective double. Do not retry signs, reinterpret `(0:0)`, or special-case
an identity matrix.

Evaluate four Tate coordinates with these complete cross-differences.
The inverse second-row pairings and the reference difference use the same
orientation. Reduce the full root to the target exponent, then prepare the
Kotlin logarithm with the **forward** generator. The specification passes
its inverse to `NormalizedDlog` because that routine has a different
parameter convention. Normalize only the final matrix's global sign.

`setChangeOfBasisMatrix` implements algorithm 3.9 with two curve-bound
bases of the same exponent. Both curves inherit the canonical generator's
maximality precondition. Generate target-compatible full canonical bases,
recover the first matrix, act on the second supplied basis by its inverse,
and recover the second matrix. Return that normalized matrix and both
canonical hints. For full-order targets use reconstruction target zero:
the generator's nonzero reconstruction target must be strictly smaller.
The independent integer expectation is `N*M^-1`, not `M^-1*N`.

## Alternatives

- Unguarded C shared differences cannot cover identical bases or exponent
  one; they remain a valid-domain differential oracle, not the definition
  of exceptional behavior.
- Independently normalizing column signs would lose the supplied
  differential orientation. Reflecting only the second generator must
  remain distinguishable from a global negation.
- Revalidating every output basis would repeat invariants already proved
  by invertible ring action. Raw pairing operands still use the existing
  checked Tate boundary; no unchecked pairing entry point is added.
- Replacing cubical pairings or ordinary ladders with affine arithmetic
  would exceed the approved helper's scope.

## Evidence and limits

The offline complete-affine prototype verified 243 cross-difference and
Miller-coordinate cases over E0 and both nonzero coefficient branches,
at exponents one, eight, `f-1` and `f`, for every parameter.
This is mathematical preparation, not a production platform gate.

`tools/generate_basis_change_vectors.py` owns common affine expected
coordinates, independently normalized known matrices and generated
one-action-per-test runtime declarations. Cases include all six binary
invertible matrices, identity/global negation, swap, shears, dense matrices,
high-bit negative coefficients and single-column reflection.
`checkCurveReference` verifies their generated content and compares all
369 action coordinates plus 51 generic-domain basis changes with pinned C.
Its owned adapter validates canonical fields/scalars, membership, exact
basis orders and differential consistency. It checks the reference's
cross-difference domain before entering unconditional C inversions;
exponent-one and coincident full bases remain independent-affine cases.
Malformed/truncated/noncanonical inputs and excluded C domains fail
explicitly. No pinned reference source or checksum changes.

All 123 action cases and 123 change cases pass on JVM, together with ten
boundary tests. Formatting, default/type-aware static analysis and strict
compiled ABI checks pass. Focused coverage of `BasisChange.kt` is all
75 lines and eight branches; this is not a new whole-production coverage
claim. Twelve additional affine/integer correspondence records cover
order two, both coefficient branches, response widths and full order.
All twelve and four boundary tests pass on JVM. Three generic composed C
checks independently obtain canonical references and hints, recover the
first matrix, evaluate the inverse action, and recover the final matrix.
The inverse/composition bookkeeping in that development checker uses
Python integers, not the C high-level signing function. E0 target adjustment
and partial C pairing domains retain the independent affine oracle.

`BasisTransformBenchmark` retains action, prepared change and fresh
preparation-plus-change. `BasisCorrespondenceBenchmark` retains the complete
hint/matrix orchestration, including both canonical searches. Each operation
has two/full torsion cases at every parameter, adding 24 cases to the exact
smoke inventory, now 348. Input decoding and checked input-basis construction
are outside timing. Prepared changes also exclude reference lifts/root
preparation; fresh changes include it. Results are owned basis/matrix/hint
objects, with no extra output normalization or encoding inside timing.
The owning generator emits benchmark-only inputs, not expected outputs.
Fresh smoke and complete platform gates remain pending.

The complete `p664_17` full-dense cases pass on JVM. Cold Node tests
including checked-input preparation take `43.283s` for action and `133.869s`
for change, exceeding the then-current `30s` limit. Each test has one focal
operation; these are not aggregated scenario timeouts. The verification
budget versus arithmetic-optimization tradeoff was presented to the user.
The user selected "Continue correctness-first with measured gate budgets":
start with a `300s` Node per-test limit, and make subsequent bounded Node/CI
budget adjustments only after recorded valid-operation overruns. Retain
every case and mathematical check; do not claim a speedup or introduce
unmeasured arithmetic changes. This changes verification resources, not
public behavior. No completed basis-change slice is claimed.
Both cold full-width tests subsequently pass under the approved limit.
The complete cold `p664_17` correspondence subsequently exceeds the `300s`
limit (`314.877s` reported before timeout). Under that same approved policy,
the Node per-test limit was temporarily `600s`.
[`ADR 0016`](0016-montgomery-field-multiplication.md) supersedes this
measured-budget policy. Montgomery field multiplication restores the
original `30s` limit, and timeout increases are no longer an accepted remedy.

The complete JVM gate now passes 965 tests with all 1,860 production lines
and 1,005 branches covered. Formatting, default/type-aware static analysis,
strict compiled ABI and pinned C gates pass. Its final isolated Node probe
fails only the recorded correspondence timeout. The full JVM coverage and
test reports, plus that exact failing Node report, are archived before reruns.

## Migration and risks

Only generated common vectors and runtime declarations are excluded from
formatting/static analysis. No production coverage exclusions, public ABI,
wire encodings, runtime dependencies, toolchain or release version change.
The workflow requires every one of the 348 retained smoke cases; there is
no score gate or controlled comparison claim.

All operations are variable-time and unsuitable for secret inputs. Expert
cryptographic review, constant-time guarantees, numeric production budgets
and controlled performance baselines remain absent. Canonical normalization
and isogeny chains remain unimplemented; milestone 3 is incomplete.
