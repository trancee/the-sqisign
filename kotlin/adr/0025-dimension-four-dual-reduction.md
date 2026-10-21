# ADR 0025: Dimension-4 dual reduction of quaternion ideals

**Status:** Accepted
**Date:** 2026-10-21

## Context

The pinned `src/quaternion/ref/lvlx/lll/lll_dim4.c` reduces an ideal lattice of
the maximal order `O0` with `quat_lll_dual_reduce_ideal`. The ideal of norm `N`
must be in the Hermite normal form produced by `quat_ideal_mul_O0`, with
denominator 2:

```text
[[2N, 0, E, Dq],
 [0, 2N, C,  B],
 [0,  0, 1,  0],
 [0,  0, 0,  1]]
```

The algorithm has four parts:

1. **Closed-form initialisation.** `fp_ldl_init_dual` writes the LDL
   decomposition of the dual Gram directly. The diagonal is
   `(4N^2, 4N^2, p, p) / 2^FP_D_SHIFT`, and below it sit the ratios `-x / 2N`,
   computed with a Newton reciprocal. The result is then size-reduced.
2. **Block tours.** `ct_lll_tour4` reduces blocks 1, 0 and 2 with the Gram
   kernel of [ADR 0024](0024-dimension-two-gram-kernel.md).
   `ct_lll_update_block` propagates each block transform through the
   fixed-point `D`, `L` and the accumulated inverse transform, without
   recomputing the decomposition. `CT_LLL_TOURS` tours run, a public
   function of the level.
3. **Minkowski step.** `ct_minkowski_reduce` builds the Gram and evaluates 27
   short candidates:
   - `(1, 0, 0, 0)`;
   - for each of the 13 sign classes of `{-1, 0, 1}^3`, the floor and the
     ceiling of the size-reduced first coordinate.

   It sorts the candidates with a 32-slot bitonic network, selects the first
   four independent ones, orients them against the first, and composes their
   inverse transpose with the accumulated transform.
4. **Ideal level.** `quat_lll_dual_reduce_ideal` permutes and reverses the
   columns. It returns the primal basis `hnf * Aout`, the transform `Aout`, and
   the dual Gram diagonal scaled back by `2^FP_D_SHIFT`.

Every bound comes from `lll_config.h` as a public function of the security
parameter and the prime size. The C code states its scope as honest inputs,
failing below `2^-lambda`. As in ADR 0023 and ADR 0024, the result is C's
specific reduction, not just any reduced basis.

## Decision

Port the whole of `lll_dim4.c` faithfully and variable-time:

| Kotlin | Reference |
| --- | --- |
| `LllConfig.diagonalShift`, `lowerBits`, `tourCount`, `hnfBits`, `inverseBits`, `basisBits`, `normBits`, `coefficientBits` | `FP_D_SHIFT`, `FP_L_BITS`, `CT_LLL_TOURS`, `QUAT_HNF_BITS`, `QUAT_AINV_BITS`, `QUAT_BASIS_BITS`, `QUAT_GRAM_BITS`, `MK_T0_BITS` |
| `IdealQuadruple`, `DualLdl` | the `qlapoty` quadruple and `fp_ldl_t` |
| `LllConfig.initDual`, `sizeReduceFrom` | `fp_ldl_init_dual`, `fp_size_reduce_from` |
| `LllConfig.tours`, private `reduceBlock` | `ct_lll_tour4` repeated `CT_LLL_TOURS` times, `ct_reduce_block` |
| `LllConfig.updateBlock` | `ct_lll_update_block`, steps 1 to 5 |
| `LllConfig.minkowski`, `form` | `ct_minkowski_reduce`, `ct_mk_eval_inner` and `ct_mk_eval_norm` |
| private `buildGram`, `sortedCandidates`, `firstCoordinate`, `bitonicSort` | `ct_mk_build_gram`, `gen_candidates`, `ct_mk_t0_floor`, `bitonic_sort32` |
| `selectIndependent`, `inverseTranspose` | `select_basis`, `ct_mk_invert_transpose_int` |
| `LllConfig.dualReduceIdeal(hnf, p)` returning `DualReduction` | `quat_lll_dual_reduce_ideal` |

Narrowing reuses the bounded `Ibz` operations of
[ADR 0022](0022-fixed-point-lattice-integers.md). This change adds
`minusWithBound` and `timesWithBound`, the `ibz_sub` and `ibz_mul` analogues of
`plusWithBound`. Every `ibz_set_bound_ct`, `ibz_ct_highmul_p`,
`ibz_ct_highmul_s`, `ibz_ct_shift`, `ibz_ct_fp_recip` and
`ibz_ct_round_shift_limb` call keeps C's bound, sign-truncation and order.
The masked selections become plain branches; the bitonic network keeps C's
strict-less swap rule, so equal keys order as in C.

The selection tests each candidate with the maximal minors of the stacked
coordinates. This is the same independence predicate as C's 2×2, 3×3 and
4×4 minor tests. The inverse transpose uses cofactor expansion in `Long`.

`dualReduceIdeal` takes the numerator matrix of the lattice. The denominator
is fixed at 2 and is left to the quaternion lattice slice. The function ports
the input checks of C's public entry point and throws
`IllegalArgumentException` when one fails:

- `2N` must be positive;
- the second diagonal entry must equal `2N`;
- `2N`, `E`, `Dq`, `C` and `B` must each have fewer than `QUAT_HNF_BITS` bits.

C's internal assertions are not ported. They check the reduction itself:

- the `±1` block determinant;
- the size-reduced coefficient and `eta` ranges;
- positive diagonals;
- the `int8` first coordinate and the key headroom;
- the unimodular `T`.

Honest inputs never trigger them, and the independent model asserts each one
on every record.

The Gaussian reduction (`gaussian_xgcd.c`) and the `O0`-ideal reduction
built on it are a separate slice.

## Independent evidence

- `tools/generate_dual_vectors.py` models the whole algorithm on Python
  integers with C's bit-length semantics. Its inputs are honest products of
  two inert `O0`-ideals, built like `quat_ideal_mul_O0` with Bézout
  coefficients from the `ibz_xgcd` model:
  - level I has six factor-size shapes, from a 20-bit factor up to factors of
    half the prime size plus 15 bits;
  - levels III and V have two shapes each.

  The model asserts every internal C assertion and the absence of capacity
  overflow. It also checks each result:
  - the transform is unimodular;
  - the basis equals `hnf * Aout` exactly;
  - each returned diagonal entry is within `2^-lambda` of the exact dual Gram
    diagonal `N^2 p (G^-1)_ii`;
  - the diagonal is non-increasing;
  - the dual Hadamard ratio is at most 4.

  It owns 30 records in `quaternion/DualVectors.kt`. For each ideal there is
  one record after initialisation, one after the tours and one for the full
  reduction.
- `checkIbzReference` replays all 2569 records against the checksum-pinned C
  with C assertions enabled. This includes `fp_ldl_init_dual` and
  `ct_lll_tour4` through the adapter's `ldl` and `tour` operations, and adds
  the pin for `lll_dim4.c`.
- `DualReductionTest` covers:
  - each input precondition;
  - the inverse transpose identity;
  - skipping dependent candidates;
  - non-increasing norms.

  With these, JVM line and branch coverage is 100%.
- Three retained `DualBenchmark` cases are checked against `BigInteger`. They
  check unimodularity, the exact basis product, the `2^-lambda` accuracy of the
  dual Gram diagonal, and its order.

## Alternatives

- **Textbook LLL plus enumeration on exact integers:** rejected. It reduces
  the lattice, but not to C's basis. Every later ideal, isogeny and signature
  computation would diverge from the reference.
- **Port only the ideal-level entry point:** rejected. The `ldl` and `tour`
  records localise any divergence in the initialisation or a block update.
  Without them, a failure would surface only as a different final basis.

## Consequences

Quaternion lattices and ideals can reduce `O0`-ideals with results identical
to C. The smoke benchmark inventory grows from 334 to 337 cases. There are no
public ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
