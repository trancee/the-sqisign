# ADR 0026: Gaussian reduction of `O0`-ideals

**Status:** Accepted
**Date:** 2026-10-22

## Context

The pinned `src/quaternion/ref/lvlx/lll/gaussian_xgcd.c` reduces an ideal
lattice of the maximal order `O0` with `quat_lll_reduce_O0_ideal`. Its only
caller, `quat_ideal_reduce_basis` in `lll_applications.c`, passes the
lattice numerator in the Hermite normal form of an `O0`-ideal of norm `N`,
with denominator 2, and the bound `ibz_get_bound(N) + 2`:

```text
[[2N, 0, E, Dq],
 [0, 2N, C,  B],
 [0,  0, 1,  0],
 [0,  0, 0,  1]]
```

A left `O0`-ideal is a `Z[i]`-module of rank 2: column 1 is `i` times
column 0, and column 3 is `i` times column 2. Writing a column as
`(z1, z2) = (row0 + i row1, row2 + i row3)`, the reduced norm is
`|z1|^2 + p |z2|^2`. C therefore runs a Lagrange-Gauss reduction over `Z[i]`
on columns 0 and 2:

1. **Scaling.** `z2` is multiplied by `s = floor(sqrt(p))`, a level constant
   `QUAT_SQRT_P_HEX`, so the norm becomes the Euclidean `|z1|^2 + |s z2|^2`.
   The approximation weight is `s^2`, not `p`.
2. **Lehmer passes.** `lehmer_gaussian` runs `DIM2I_OUTER_ITS(in)` outer
   passes. Each takes signed 64-bit windows of the leading bits and runs
   `LG_gaussian_inner` for `DIM2I_INNER_ITS` steps, which accumulates an
   `int32` complex transform. `ct_apply_matrix_gaussian` applies the
   transform to the full values, including the tracked unscaled `z2`.
3. **Orientation.** `orient_canonical` multiplies each vector by the unit
   that moves its key into the closed first quadrant. The key of `v1` is its
   first coordinate. The key of `v2` is the exact Hermitian product
   `H(v1, v2) = conj(z1) w1 + p conj(z2) w2`.
4. **Output.** The columns are `v1, i v1, v2, i v2` at
   `max(bound, QUAT_SQRT_P_BITS + 1) + 1` bits.

All bounds come from `lll_config.h` as public functions of the level and the
bound. The windows see only the leading bits. A residual size-reduction step
that would lower the second norm by less than the window precision is
invisible to them. C stops there, so its `v2` is reduced only to about 64
bits of relative precision. `v1` is exactly shortest. As in ADRs 0023 to
0025, the result is C's specific reduction, not just any reduced basis.

## Decision

Port the whole of `gaussian_xgcd.c` faithfully and variable-time:

| Kotlin | Reference |
| --- | --- |
| `GAUSSIAN_THRESHOLD`, `GAUSSIAN_WINDOW`, private `UP_SHIFT`, `INNER_STEPS`, `STOP_SHIFT` | `DIM2I_THRESHOLD`, `DIM2I_W`, `DIM2I_UP_SHIFT`, `DIM2I_INNER_ITS`, `DIM2I_THRESHOLD + 64 - DIM2I_UP_SHIFT - DIM2I_W` |
| private `GaussianWidths` | `DIM2_VAL_BITS`, `DIM2_WORK_BITS`, `DIM2I_HERM_BITS`, `DIM2I_SHIFT_MAX`, `DIM2I_OUTER_ITS` |
| `ComplexWord`, `ComplexInt`, `GaussianWindows`, `GaussianTransform` | the 64-bit window pairs and the `int32` complex matrix |
| `gaussianInner` | `LG_gaussian_inner` |
| `mulHigh`, private `norm`, `ceilLog2Diff`, `roundRatio`, `shiftRightSaturated` | `ct_mul_high`, `ct_norm64_approx`, `ceil_log2_diff_u64`, `round_ratio_62bit_ct`, `ct_shr_sat64` |
| private `outerPass`, `applyTransform` | one pass of `lehmer_gaussian`, `ct_apply_matrix_gaussian` |
| `Complex`, `orient` | `ct_complex_t`, `orient_canonical` |
| `LllConfig.reduceO0Ideal(hnf, p, bound)` | `quat_lll_reduce_O0_ideal` |

`LllConfig` now exposes its `ParameterSet` and `primeBits` (`QUAT_PRIME_BITS`).
The level constants `floor(sqrt(p))` are stored as little-endian words
copied from `QUAT_SQRT_P_HEX`. Narrowing reuses the bounded `Ibz` operations
of [ADR 0022](0022-fixed-point-lattice-integers.md) with C's bounds and order.
Masked selections become plain branches. Once the inner step becomes invalid
it stops, which is equivalent to C's masks. Inputs that would make C shift
an `int32` by 32 or more are undefined behaviour in C. Honest inputs never
produce them, and they are not part of the evidence.

`reduceO0Ideal` throws `IllegalArgumentException` in two cases:

- `p` is not the prime whose integer square root is the stored level
  constant;
- the bound makes the Hermitian width exceed the `Ibz` capacity. The
  Hermitian width dominates the work width, so this one check bounds every
  intermediate.

C's debug-only assertions on Hermitian positivity and the output width are
not ported. They cannot fail on the reduction they check, and ported they
would be unreachable branches. The independent model checks the properties
on every record.

## Independent evidence

- `tools/generate_gaussian_vectors.py` models the whole algorithm on Python
  integers with C's bit-length and 64-bit semantics. Its inputs are honest
  inert left `O0`-ideals `(x, y, N)`:
  - `N` is a product of inert primes of six factor-size shapes at level I
    and two shapes at levels III and V;
  - each input is turned into its lattice exactly like `quat_to_lattice`;
  - each level's first ideal is also reduced with a bound 40 bits larger.

  Each reduction is checked:
  - the transform from the HNF is integral and unimodular;
  - the first weighted norm equals the exact `Z[i]` Lagrange minimum;
  - the second exceeds the exact one by at most `2^-64` relative;
  - the key is in the first quadrant.

  `quaternion/GaussianVectors.kt` holds 33 records:
  - 13 `gauss` records;
  - 20 `ginner` records, taken from the traced inner calls plus four
    synthetic edge cases: zero windows, a disallowed swap, an early stop on
    a short vector, and a swap-needed stop.
- `checkIbzReference` replays all 2602 records against the checksum-pinned C
  with C assertions enabled. The adapter's `ginner` and `gauss` operations
  call `LG_gaussian_inner` and `quat_lll_reduce_O0_ideal`, and the pin for
  `gaussian_xgcd.c` is added.
- `GaussianReductionTest` covers:
  - both prime preconditions;
  - the capacity precondition;
  - both rotation cases of `orient`, including a negative real key that ends
    on the positive imaginary axis.

  With these, JVM line and branch coverage is 100%.
- Three retained `GaussianBenchmark` cases are checked against `BigInteger`.
  They check:
  - the integral transform and the unchanged determinant;
  - the `i v1` and `i v2` columns;
  - the first-quadrant key;
  - the norm order;
  - that no Gaussian multiple of `v1` near the rounded quotient shortens
    `v2` beyond the documented window precision.

## Alternatives

- **Exact Lagrange-Gauss over `Z[i]`:** rejected. Its `v2` can differ from
  C's in the last size-reduction step, and its orientation is not C's. Every
  later ideal, isogeny and signature computation would diverge from the
  reference.
- **Reuse the dimension-4 dual reduction:** rejected. C uses the Gaussian
  reduction here, and the bases differ.

## Consequences

`quat_ideal_reduce_basis` can be ported with results identical to C. The
smoke benchmark inventory grows from 337 to 340 cases. There are no public
ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
