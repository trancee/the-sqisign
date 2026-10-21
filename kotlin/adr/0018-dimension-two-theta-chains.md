# ADR 0018: Dimension-two (2,2)-isogeny theta chains

**Status:** Accepted
**Date:** 2026-10-19

## Context

Milestone 4 is specification §4.5 and pinned `src/hd/ref`. Verification needs
algorithm 4.133: a `(2^n, 2^n)`-isogeny chain `E1 x E2 -> E3 x E4`. Its kernel
is `[4]<T1, T2>`, where `T1` and `T2` are couple points of order `2^(n+2)`. The
chain glues the product into a level-two theta structure, evaluates `n-1`
generic (2,2)-steps under a halving strategy, and splits the codomain back
into two normalized Montgomery curves. The specification's algorithms
4.117-4.127 describe the pieces. The C reference (`theta_chain_compute_impl`,
`gluing_start_chain`, `splitting.c`) fixes the strategy, the factor order and
every diagnostic.

## Decision

Implement internal, variable-time `thetaChain(length, curves, kernel, points,
mode)` in package `ch.trancee.sqisign.hd`. It returns a sealed outcome: either
`ThetaChainCodomain`, with the requested factors and point images, or
`ThetaChainRejected`, carrying the first reference diagnostic. The internal
`ThetaChainRejection` exception is control flow, caught only by
`ThetaChainOutcome.of`. `ThetaChainMode` mirrors the C `mode`: both factors,
first, second, or verify (first factor, with every reference check).

- **Order of operations.** Port the reference strategy and check order
  exactly. Gluing ladders halve while at least 16 steps remain. Generic steps
  halve the remaining doublings. Gluing checks torsion (verify only), then the
  theta `t = 0` condition, then the nonzero constants. Generic checks run only
  in verify mode and skip the last step. Splitting checks zero, the product
  condition, point products, `x^4 != y^4` (verify only), then each requested
  model. After the change of coordinates `(x+z, y+t, y-t, x-z)`, the first
  factor is `(x : z)` and the second is `(x : y)`, as in C.
- **Preconditions.** These are `require` failures, not diagnostics.
  - `length` must be in `2..f-2`. The reference leaves its doubling data unset
    for one-step chains, and every caller uses longer chains.
  - Every kernel component must be finite and not `(0 : 1)`. Such a point makes
    the gluing ladder produce `(0 : 0)`, which the typed point API rejects.
  - Every pushed couple point must have a component at infinity, as required
    by the special gluing evaluation.
- **Departures from C, both stricter:**
  - `gluing_start_chain` returns `0` on failure. Its caller cannot distinguish
    that from success, so it continues on uninitialized data. Kotlin rejects
    immediately with the gluing diagnostic.
  - If a pushed point's split factor is `(0 : 0)`, C reports success with a
    degenerate image. Kotlin rejects it with `degenerate-image`.
- **Omitted check.** Two splitting `invalid` sub-checks, that the second
  factor's coordinates are zero, are unreachable after the zero check and are
  omitted.
- **Defensive checks kept.** On the Kummer line, every couple point's squared
  theta coordinate `t` vanishes under a genuine gluing matrix. So
  `gluing-evaluation` cannot fail from real inputs; it is kept as in C and
  covered with a synthetic gluing. `gluing-zero` and the generic zero check
  are covered the same way.

`MontgomeryIsogeny.kt`'s three-element batched inversion and fixed
imaginary unit become `internal`, so the theta-to-Montgomery model (algorithm
4.124 order) reuses them.

## Independent evidence

- **Python oracle.** `tools/hd_oracle.py` is independent of the production
  code. It uses affine multiples rather than ladders, explicit (re)doubling
  rather than stored ladder levels, and a direct isomorphism search.
- **C adapter.** `tools/hd_reference.c` compiles pinned `src/hd/ref` and the
  verifier per level. It replays every public KAT signature through
  `protocols_verify` and captures each verification chain.
  `tools/hd_kernels.txt` keeps the captured kernels for p324 KATs 0-1 and
  p500/p664 KAT 0. `checkHdReference` also requires all 300 replays to verify.
- **Generated vectors.** `tools/generate_hd_vectors.py` owns common
  `hd/ThetaChainVectors.kt` and benchmark-only `ThetaChainInputs.kt`.
  - p324 chain records: every captured kernel in all four modes, plus seven
    malformed kernels (shorter, longer, duplicate, swapped, mixed, halved,
    degenerate). Between them they cover gluing-theta, gluing-torsion,
    generic, splitting-product and degenerate-image.
  - p500 and p664 chain records: their KAT kernel in all four modes.
  - Theta-to-Montgomery models: all six candidate indices plus singular
    inputs.
  - Gluing matrices: all four symmetric-element cases.
  - Splitting: every diagnostic and mode.
- **C cross-check.** `checkHdReference` checksum-pins the C sources and
  cross-checks every chain record through the C chain entry points. That
  covers codomains, images and the first rejection diagnostic. It also checks
  that the vectors are current. The model, matrix and split records are
  oracle-only unit evidence; their C counterparts are exercised through the
  chains.

## Alternatives

- **Port the C failure-continuation of `gluing_start_chain`:** rejected. Its
  output is unspecified, so no test or caller can depend on it.
- **Accept `(0 : 0)` images:** rejected. The typed point API cannot represent
  them, and the specification treats them as invalid.
- **Carry diagnostics as exceptions to callers:** rejected. The sealed outcome
  keeps rejection part of the contract, and the exception stays internal.

## Consequences

Milestone 4 is functionally complete for all three parameter sets. Retained
`ThetaChainBenchmark.verify` cases time the captured KAT verification chain at
each level and extend the smoke inventory to 360. All arithmetic remains
variable-time, and no constant-time, production or expert-review claims are
made. Milestone 5 (the verifier) consumes `thetaChain` in verify mode.
