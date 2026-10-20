# ADR 0023: Dimension-2 lattice reduction and Cornacchia

**Status:** Accepted
**Date:** 2026-10-19

## Context

The pinned `src/quaternion/ref/lvlx/lll/lehmer_xgcd.c` reduces 2-dimensional
lattices with a Lehmer-style partial extended GCD. Its production caller is
`quat_lll_dim2_sumofsquares`, which `ibz_cornacchia_prime` in
`src/quaternion/ref/lvlx/integers.c` uses to write a prime `p = 1 mod 4` as
`x^2 + y^2`. `stdorder.c` and `qlapoty.c` call `ibz_cornacchia_prime` when
they solve norm equations. `integers.c` also provides `ibz_rounded_div` and
`ibz_sum_two_squares`.

The reduction works on fixed machine-word windows. `partial_xgcd_msb` runs
up to 31 subtractive steps on the leading 64 bits of the two basis vectors and
accumulates an `int32` transform. `ct_apply_matrix` applies that transform at
the bounds `DIM2_VALBITS(det_bits)` and `DIM2_WORKBITS(det_bits)`. The
stopping point depends on those windows, on a 16-bit square root of the
determinant window, and on `int32` wrap-around, so the result is a specific
reduced vector, not just any short one.

`quat_lll_dim2_short_basis`, `sizered_lehmer` and `sizered_msb` have only
test callers in the pinned tree. `ibz_generate_random_prime` is likewise
test-only.

## Decision

Port the production path faithfully and variable-time:

| Kotlin | Reference |
| --- | --- |
| `sumOfSquares(p, root)` | `quat_lll_dim2_sumofsquares` |
| private `LehmerReduction.reduce` and `step` | `partial_xgcd_lehmer` |
| private `LehmerReduction.apply` | `ct_apply_matrix` |
| internal `msbTransform` | `partial_xgcd_msb` |
| `Ibz.cornacchiaPrime()` | `ibz_cornacchia_prime` |
| `Ibz.roundedDivide(divisor)` | `ibz_rounded_div` |
| `sumOfTwoSquares(a, b)` | `ibz_sum_two_squares` |

Each step uses the same machine-word semantics as C:

- 64-bit windows are compared unsigned.
- The `int32` transform entries wrap.
- The square-root bound is shifted as in `DIM2_SQRT_SHIFT_CAP` and
  `DIM2_WINDOW_FLOOR`.
- Values are narrowed through `timesIntWithBound`, `shiftSigned` and
  `plusWithBound` from [ADR 0022](0022-fixed-point-lattice-integers.md).

The step that would reach a zero window, fall below the bound, lose precision,
or swap where swapping is forbidden stops the transform permanently. The zero
check comes before the swap, as in C.

The test-only `quat_lll_dim2_short_basis`, `sizered_lehmer`, `sizered_msb` and
`ibz_generate_random_prime` are not ported. C asserts that the work bound fits
the capacity; Kotlin throws `IllegalArgumentException` instead. Checked sums
throw `ArithmeticException` where C wraps.

## Independent evidence

- `tools/generate_lattice_vectors.py` models the Lehmer reduction on Python
  integers with explicit 64-bit and 32-bit word semantics. Every
  prime-modulus result is checked against `x^2 + y^2 = p`. It owns 291
  records in `quaternion/LatticeVectors.kt` across all three capacities:
  - reductions of primes from 5 to 1000 bits, with bit-length slack;
  - a composite with all of its square roots of -1;
  - an `a^2 + 1` modulus close to the work-bound limit;
  - Cornacchia on 1, 2, composites, a `3 mod 4` prime, and primes whose
    reduced vector is already sorted;
  - rounded divisions at the tie boundaries;
  - sums of squares, including the 64-bit wrap.
- `checkIbzReference` replays them with the `Ibz`, number-theory and
  fixed-point records (2368 in total) against the checksum-pinned C, with C
  assertions enabled.
- `LehmerReductionTest` covers the work-bound precondition, zero windows and a
  root that misses the norm, which gives 100% JVM line and branch coverage.
- Six retained `CornacchiaBenchmark` cases are checked against `BigInteger`:
  - the full Cornacchia call returns a sorted `x^2 + y^2 = p`;
  - the reduction alone returns a vector of norm `p` congruent to
    `(+-root * y, y)`.

## Alternatives

- **Textbook Cornacchia or full Gauss reduction:** rejected. Either finds a
  representation, but not necessarily C's vector or order. Downstream norm
  equations would then diverge from the reference.
- **Port `short_basis` and the size reductions now:** rejected. The pinned
  tree only calls them from tests, so porting them would add code with no
  production caller.

## Consequences

Norm-equation solving can call Cornacchia with results identical to C. The
smoke benchmark inventory grows from 322 to 328 cases. There are no public
ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
