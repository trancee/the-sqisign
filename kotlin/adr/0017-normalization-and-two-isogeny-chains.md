# ADR 0017: Canonical normalization and curve-only two-isogeny chains

**Status:** Accepted
**Date:** 2026-10-19

## Context

Milestone 3 ends with the 2-isogeny chains named in `PORTING.md`. The
specification uses them only through algorithm 4.99 `TwoIsogenyChain`. It
evaluates `e/2` four-isogenies (algorithms 4.97 and 4.98) under a halving
strategy, then applies algorithm 4.96 `NormalizeMontgomery` to the remaining
order-four point. Algorithm 3.5 `CurveToMontgomeryA` is the existing `A/C`
coefficient.

Algorithm 4.99 accepts a list of points to evaluate. Those points are mapped
only through the four-isogenies, not through the final isomorphism, so their
images lie on the unnormalized model rather than the returned curve. Every
caller passes an empty list: the specification's verifier and signer, and C
`verify.c` through `iso_isogeny_2chain` (pinned `src/ec/ref/lvlx/isog.c`).

## Decision

Implement three internal, variable-time operations:

- `MontgomeryCurve.normalize(fourTorsion)`: algorithm 4.96. It computes the
  theta coordinates `(X+Z, X-Z)`, three Montgomery coefficients `2(a^4+b^4)` and
  `b^4-a^4` with one batched inversion, and their negations. It returns the
  maximum under the Definition 4.2.1 order (imaginary part, then real part).
- `FourIsogeny.from(curve, kernel)`: algorithm 4.97 codomain and constants,
  and `evaluate(point)`: algorithm 4.98 including infinity and the kernel.
- `MontgomeryCurve.twoIsogenyChain(kernel, exponent)`: algorithm 4.99 with an
  empty point list. It returns only the normalized codomain.

Every kernel is checked to have exact order four and not to lie above `(0:1)`.
The chain therefore checks every step. For a valid seed only the first step
can fail, matching the specification's first-step check and the C return
value `-1`. The chain length must be positive, even and at most the torsion
exponent. Validation is in `FourIsogeny.from`, so the chain adds no
unreachable failure branches.

Point images are not part of the chain API, because exposing them would
return points on a different curve from the normalized result. A future
caller that needs images must introduce an explicit model-aware contract.

## Independent evidence

`tools/isogeny_oracle.py` is an affine oracle independent of the projective
production formulas:

- Normalization enumerates the six origin-preserving isomorphisms directly.
  These are translations by `0`, `u` or `1/u`, with scaling by `1`, `s` or
  `i*s/u` and negation, where `u` is the order-four point's coordinate. The
  oracle picks the specification maximum.
- Four-isogenies use full affine Vélu with both kernel points, followed by an
  explicit model change. The oracle compares every image, including the
  basis, `(0,0)`, infinity, the kernel and its double.
- The chain uses `sqrt(e)` uniform checkpoints, not production's halving
  strategy. During development it was byte-identical to a quadratic linear
  oracle.

`tools/generate_isogeny_vectors.py` owns common `ec/IsogenyVectors.kt` and
benchmark-only `IsogenyInputs.kt`. For each parameter set it generates
12 normalization records, 42 four-isogeny images and 15 chains. Chain lengths
are `2`, `8` and `lambda` on every curve, plus the response length and `f-2`
on `E0`. Rejections cover singular kernels, orders two and eight, infinity,
seeds of too low and too high order, and invalid lengths.

`checkCurveReference` pins `isog.c`, `normalize.c` and `isog.h` (commit
`f417ebd`). It cross-checks every accepted record through
`ec_normalize_montgomery`, `iso_xisog_4`/`iso_xeval_4` and
`iso_isogeny_2chain`. Every rejection and malformed input must fail. The C
reference's assert-enabled `debug_print` warnings are removed before
requiring empty output.

Twenty-seven common tests (nine per parameter set) and four benchmark result
tests pass on JVM. Nine retained `IsogenyBenchmark` cases (normalize,
four-isogeny and a `lambda`-length chain at each level) extend the smoke
inventory to 357.

## Alternatives

- **Return point images from the chain:** rejected. No caller needs them,
  and the images do not lie on the normalized result.
- **Validate only the first step, like the C:** equivalent for valid
  inputs, but it would need a separate unchecked four-isogeny constructor.
- **Linear chain oracle in the gate:** rejected for runtime. It took about
  90 seconds to generate, versus about 19 seconds with checkpoints, and gave
  identical output.

## Consequences

Milestone 3 is functionally complete for all three parameter sets. All
arithmetic remains variable-time, and no constant-time, production or
expert-review claims are made.
