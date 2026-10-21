# ADR 0024: Dimension-2 Gram kernel of the dimension-4 reduction

**Status:** Accepted
**Date:** 2026-10-20

## Context

The pinned dimension-4 lattice reduction, `src/quaternion/ref/lvlx/lll/lll_dim4.c`,
reduces neighbouring basis vectors in pairs. `ct_reduce_block` hands each pair
to `src/quaternion/ref/lvlx/lll/lll_lg2.c`:

- `quat_lll_materialise_block` builds the binary form
  `(D_k, mu D_k, D_{k+1} + mu^2 D_k)` from the fixed-point LDL state. It
  truncates the three entries by a common `2^-e` so that they fit
  `GRAM_TOT_BITS`.
- `quat_lll_gram_lehmer` runs `lg_outer_its_for_round(round)` Lehmer
  Lagrange-Gauss passes on that form. Each pass reduces 61-bit leading windows
  with `partial_gram_msb` and applies the resulting `int32` transform to the
  full form and to the accumulated transform `U`.

When the two diagonal sizes differ by at least 29 bits, the windows of `a` and
`b` are pre-scaled by `4^extra` and `2^extra`, and swaps are disabled. Every
bound (`FP_U_BITS`, `FP_P_BITS`, `FP_E_BITS`, `GRAM_TOT_BITS`,
`GRAM_WORK_BITS`, `LG_OUTER_ITS`) comes from `lll_config.h` as a public
function of the security parameter and the prime size. As in the Lehmer port
of [ADR 0023](0023-dimension-two-lattice-reduction.md), the stopping point
depends on the windows and on `int32` wrap-around. The result is therefore
C's specific reduced form, not just any reduced form.

## Decision

Port both functions faithfully and variable-time:

| Kotlin | Reference |
| --- | --- |
| `LllConfig(parameterSet)` | `lll_config.h` level constants |
| `LllConfig.outerIterations(round)` | `lg_outer_its_for_round` |
| `LllConfig.materialiseBlock(dk, dk1, mu, precision)` | `quat_lll_materialise_block` |
| `LllConfig.gramLehmer(form, iterations)` | `quat_lll_gram_lehmer` |
| private `pass` and `applyTransform` | the loop body, `apply_matrix_2x2_shift` and `apply_matrix_to_U_shift` |
| internal `partialGramMsb` | `partial_gram_msb` and `fast_log2_ratio` |

`LllConfig` derives the prime size from the parameter set as
`torsionExponent + bitLength(cofactor)`, which gives `QUAT_P_BITS` = 326, 505
and 669. The shared `window` helper of ADR 0023 also models
`ibz_extract_u64` and `ibz_extract_i64` for negative offsets.

Machine-word semantics match C:

- unsigned 64-bit window compares and wrapping updates;
- wrapping `int32` transform coefficients;
- a negation that is kept once taken in the step where the transform stops.

Narrowing goes through `timesIntWithBound`, `plusWithBound`, `shiftSigned`,
`highProduct` and `withBound` from
[ADR 0022](0022-fixed-point-lattice-integers.md).

C asserts that the materialised diagonal entries are positive; Kotlin throws
`IllegalArgumentException` instead. For positive-definite windows the step shift `k`
stays near half the 32-bit window gap, far below 32. Kotlin's masked shift
counts and C's undefined over-wide shifts therefore never meet.

The dimension-4 driver, `quat_lll_dual_reduce_ideal` and the Gaussian
reduction are separate slices.

## Independent evidence

- `tools/generate_gram_vectors.py` models materialisation and the Lehmer
  kernel on Python integers with explicit 64-bit and 32-bit word semantics.
  Every reduction is checked to be unimodular and to transform the input form
  exactly. It owns 171 records in `quaternion/GramVectors.kt` across all three
  levels:
  - outer pass counts for rounds 0 to 39 at level I, and 0 to 7 at
    levels III and V;
  - machine-word steps for zero windows, a disabled swap, the `INT64_MIN`
    middle window, precision loss and multi-step reductions;
  - materialised blocks for unit, tiny, skewed, maximal and near-`GRAM_TOT`
    diagonals, and a `|mu| = 1/2` tie;
  - Lagrange-Gauss reductions of those blocks and of skewed bases whose
    transform fits `FP_U_BITS`, at every distinct outer count of the sampled
    rounds.

  Together they exercise 959 large-gap and 599 swap-enabled small-gap
  windows, 250 of which yield non-identity transforms. Inputs whose transform would exceed
  `FP_U_BITS` are outside C's domain, because C wraps `U` there, and are not
  generated.
- `checkIbzReference` replays them with the earlier `Ibz` records (2539 in
  total) against the checksum-pinned C, with C assertions enabled. The checker
  pins `lll_lg2.c`, `lll_lg2.h` and every level's `quaternion_constants.h`.
- `GramReductionTest` covers each non-positive diagonal precondition, which
  brings JVM line and branch coverage to 100%.
- Six retained `GramBenchmark` cases are checked against `BigInteger`. For a
  Lovász-violating block, the test checks:
  - the truncated form and its scale;
  - a unimodular transform;
  - an exactly transformed, Lagrange-reduced result.

## Alternatives

- **Textbook Lagrange-Gauss on the full form:** rejected. It reduces the form,
  but not to C's transform. The dimension-4 driver, and every ideal reduced
  through it, would then diverge from the reference.
- **Port the dimension-4 driver in the same slice:** rejected. The kernel has
  a narrow, separately testable contract, and `lll_dim4.c` adds the LDL state,
  size reduction and Minkowski selection.

## Consequences

The dimension-4 driver can reduce blocks with results identical to C. The
smoke benchmark inventory grows from 328 to 334 cases. There are no public
ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
