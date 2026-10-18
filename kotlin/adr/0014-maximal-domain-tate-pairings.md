# ADR 0014: Curve-bound maximal-domain Tate pairings

**Status:** Accepted
**Date:** 2026-10-18

## Context

Algorithm 4.104 needs cubical representatives, not arbitrary projective scales.
The ordinary curve constructor proves nonsingularity only. In particular,
self-pairing and zero-two-torsion identities must not be applied on an
unproved maximal curve. A checked full basis forces maximality at these
parameters by the Hasse bound, as recorded in ADR 0011.

## Decision

Bind each checked basis to its normalized Montgomery coefficient. Accept
independent projective scalings, but reject use on another model.
Prepare internal `TatePairing` only from a curve-bound full `E[2^f]` basis.
Accept positive exponents through `f`, points on that model, a first operand
killed by `2^e`, and a consistent sum/difference triple. Validate these
premises before taking any exceptional identity.

Reuse the witness's already-proved membership and full-order facts when
an operand is projectively equal to one of its three points. This is proof
reuse, not an assumption about arbitrary coordinate construction. Unknown
operands retain membership checks; a smaller exponent always requires its
own order check. Stop a divisibility-order check once infinity is reached.

Normalize finite inputs, then preserve the exact cubical scales in algorithms
4.100-4.104. Reuse cubical doubling for divisibility-order validation; do not
substitute ordinary projective doubling inside the pairing calculation.
Exponent one has no cubical loop or second-x inversion. Final reduction uses
conjugation to compute the `p-1` power, followed by the odd cofactor and the
remaining `f-e` squarings.

On this witnessed maximal domain, infinity and coincident/opposite operands
pair to one. For first operand `T0=(0,0)`, its Miller function is
`x^(2^(e-1))`, giving the extension-field quadratic character of the second
x-coordinate. For second operand `T0`, the result is one when `e<f`; at full
exponent alternation gives the first x-coordinate's quadratic character.
Compute that character from `X*Z`, avoiding inversion and preserving scale.
These identities do not imply that all lower-torsion restrictions are trivial:
at `e=f-1`, the restriction has exact order `2^(f-2)`.

## Alternatives

- Copy C's unconditional normalization: rejected because it inverts zero
  for valid zero-two-torsion inputs.
- Use ordinary projective doubling: rejected because arbitrary factors
  alter the cubical monodromy.
- Trust any lower-order basis or arbitrary curve: rejected because neither
  supplies the maximality premise for exceptional identities.
- Raise Node's existing timeout: rejected. Repeatedly proving facts already
  supplied by the witness and aggregating unrelated expensive cases were
  addressed instead; the 30-second limit was unchanged for this slice.
  ADR 0015 records the subsequent user-approved budget for complete composed
  basis operations, which is a separate measured workload.

## Evidence and risks

The independent Python oracle uses affine tangent/vertical Miller functions
on a shifted degree-zero divisor, not cubical formulas. Only a typed
evaluation-intersection error selects another bounded offline shift.
The owner generates 180 pairing records over E0 and both nonzero coefficient
branches at every parameter. Records include exponent one, intermediate and
full exponents, the nontrivial `f-1` restriction, projective scaling,
zero-two-torsion, infinity, self/opposite, smaller first order, arbitrary
second points and inversion from replacing a sum with a difference.

Fifty-seven generic-domain records also match pinned C exactly, not just up to
inversion. C's partial exceptional domain is not the oracle for the remaining
cases. The external adapter checks canonical inputs, nonsingularity,
membership, divisibility order and the differential relation before entering
assertion-based C arithmetic. Its pairing/logarithm modes are grouped in
`tools/pairing_reference.h`, still compiled in the same translation unit,
keeping each maintained source below the mandatory 500-line limit.

Twelve retained JVM cases cover two/full torsion and every parameter,
separating reused preparation from fresh pairing preparation. Input decoding
and full-basis validation are outside both timed boundaries; fresh preparation
still checks witness binding and prepares the normalized cubical coefficient.
This is variable-time experimental arithmetic, without expert review,
constant-time evidence, numeric performance budgets or a controlled baseline.

## Migration

No public ABI, wire encoding, runtime dependency, toolchain or version change.
`tools/generate_pairing_vectors.py` owns common pairing/logarithm fixtures and
both benchmark-input files; `checkCurveReference` checks all three outputs.
Only generated formatting/static-analysis exclusions are added. The exact
smoke matrix grows from 312 to 324 cases. Complete basis actions/changes,
canonical normalization and isogeny chains remain subsequent work.
