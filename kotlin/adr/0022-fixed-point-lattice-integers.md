# ADR 0022: Fixed-point lattice-reduction integers

**Status:** Accepted
**Date:** 2026-10-19

## Context

The third slice of milestone 6 ports the pinned integer-only LLL in
`src/quaternion/ref/lvlx/lll/`. Before porting the reductions themselves, the
port needs the fixed-point `ibz_t` layer they call in the pinned
`src/mp/ref/lvlx/mp.c`:

- `ibz_ct_shift` and `ibz_ct_shl` shift by a secret amount, then narrow to
  an output bound.
- `ibz_ct_highmul_p` and `ibz_ct_highmul_s` multiply exactly in a buffer
  wider than the capacity, then shift and narrow.
- `ibz_ct_fp_recip` computes a Newton reciprocal at scale `P`.
- `ibz_ct_round_shift_limb` rounds a whole-word shift, with ties toward
  positive infinity.
- `ibz_ct_lt_mask`, `ibz_cneg`, `ibz_cswap`, `ibz_set_bound_ct`,
  `ibz_bitsize_ct`, `ibz_mul_by_int_and_set_bound` and `ibz_cmp_int32` are
  the mask helpers.

C writes these as fixed ladders so that they are constant-time in the limb
values. Within their asserted domains every one of them computes the exact
mathematical result: `|shift| <= max_shift`, an output bound within the
capacity, and at most the product's word count for products. The only
exception is the deliberate narrowing to the output bound. The precisions
that matter are each level's `FP_P_BITS`: 640, 832 and 1152.

## Decision

Add internal, variable-time `Ibz` extensions in `IbzFixedPoint.kt` that
compute the exact value and then narrow it as C does. Narrowing reduces the
value modulo `2^(64 * limbs(bound))` and then sign-extends from bit
`bound - 1`.

| Kotlin | Reference |
| --- | --- |
| `shiftSigned(shift, maxShift, bound)` | `ibz_ct_shift`; `ibz_ct_shl` for `shift >= 0` |
| `highProduct(other, precision, bound)` | `ibz_ct_highmul_p` |
| `scaledProduct(other, shift, maxShift, bound)` | `ibz_ct_highmul_s` |
| `fixedPointReciprocal(precision, rounds)` | `ibz_ct_fp_recip`, returning `IbzReciprocal(value, scale)` |
| `roundShiftWords(precision, bound)` | `ibz_ct_round_shift_limb` |

The mask helpers need no new code. Their existing equivalents are:

| Reference | Kotlin equivalent |
| --- | --- |
| `ibz_ct_lt_mask` | `<` |
| `ibz_cneg` | conditional `unaryMinus` |
| `ibz_cswap` | swapping immutable references |
| `ibz_set_bound_ct` | `withBound` |
| `ibz_bitsize_ct` | `bitSize` |
| `ibz_mul_by_int_and_set_bound` | `timesIntWithBound` |
| `ibz_cmp_int32` | `compareTo(Int)` |

Products are formed from unbounded magnitudes in
`limbs(a) + limbs(b) + 1` words, as in C's wide buffer, so the product of
two capacity-width operands never meets an `Ibz` bound. The reciprocal follows
C's steps exactly:

1. Normalise to `P` bits at width `P + 4`.
2. Seed with `floor((2^127 - 1) / top64)`, then shift it left by `P - 63`.
3. Run `rounds` iterations of `w <- floor(w (2^(P+1) - floor(Dn w / 2^P)) / 2^P)`
   with C's intermediate bounds.

Inputs on which C asserts throw `IllegalArgumentException`:

- `|shift| > maxShift`.
- An output bound outside the capacity, or wider than the product.
- A non-positive divisor.
- A precision below 64, or one that leaves fewer than 6 bits of headroom.
- A precision or bound that is not a whole number of words.

## Independent evidence

- `tools/generate_fixed_point_vectors.py` models the layer exactly on Python
  integers, without word ladders. It owns 422 records in
  `arithmetic/IbzFixedPointVectors.kt` across all three capacities:
  - signed shifts of every `Ibz` operand shape;
  - wide products of near-capacity operands;
  - reciprocals at `FP_P_BITS`, 64 and 200 bits with 0, 2 or 5 rounds;
  - rounding at the tie boundaries;
  - less-than masks.
- `checkIbzReference` replays them with the `Ibz` and number-theory records
  (2077 in total) against the checksum-pinned C, with C assertions enabled.
- `IbzFixedPointTest` covers every precondition, which gives 100% JVM line and
  branch coverage.
- Six retained `IbzFixedPointBenchmark` cases are checked against
  `BigInteger`:
  - `highProduct` equals the exact floor;
  - after five rounds the reciprocal lies within 4 of `2^(2P) / D`.

## Alternatives

- **Port the constant-time ladders word for word:** rejected. Every other
  layer of the port is variable-time, so the ladders would add code without
  making any guarantee. The exact model yields identical values inside C's
  asserted domain, and the pinned C differential check confirms it.
- **Separate `ibz_ct_shl` and mask functions:** rejected. Each is
  value-identical to an existing operation or to `shiftSigned`.

## Consequences

The dimension-2, dimension-4 and Gaussian lattice reductions can be ported on
exact fixed-point values that match C bit for bit. The smoke benchmark
inventory grows from 316 to 322 cases. There are no public ABI, wire-format,
dependency or toolchain changes. No constant-time, expert-review or
production claims are made.
