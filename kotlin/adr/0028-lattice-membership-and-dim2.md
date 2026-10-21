# ADR 0028: Lattice membership, 2x2 matrices and Gaussian integers

**Status:** Accepted
**Date:** 2026-10-21

## Context

The pinned `src/quaternion/ref/lvlx/lattice.c` implements lattices
`quat_lattice_t`: a denominator and a 4x4 basis whose columns span the
lattice. `quat_lattice_contains` solves for the coordinates of an element by
back-substitution in a triangular basis and reports whether they are
integral. `quat_alg_make_primitive` in `algebra.c` uses it to write an
element of an order as its content times a primitive coordinate vector; ideal
generators and `id2iso.c` call it with `MAXORD_O0`.

The pinned `src/quaternion/ref/lvlx/dim2.c` implements 2x2 integer matrices
and Gaussian integers. Production callers are `qlapoty.c`
(`ibz_mat_2x2_eval`, `_mul`, `_inv_with_det_as_denom`), `id2iso.c`
(`ibz_mat_2x2_inv_mod` modulo `2^f`), key generation and signing
(`ibz_mat_2x2_normalize` with `e` the challenge length or `f`), and the
ideal code (`ibz_vec_2_gaussian_mul`, `_gcd`).

As in ADRs 0021 to 0027, result bit lengths of `ibz_t` operations depend on
operand bit lengths and operation order, and later bounded reductions see
them, so the port must reproduce C's values and bit lengths.

`quat_lattice_contains` has a second caller in `stdorder.c`, inside an
assertion only. The remaining `lattice.c` functions (`is_hnf`, `reduce_denom`,
`inclusion`, `equal`, `gram`) and the 2x2 helpers `det_from_ibz`, `set`,
`scalar_mul`, `mul_mod`, `vec_2_add`, `vec_2_sub`, `vec_2_is_zero` and
`gaussian_is_unit` have no production caller in the slices ported so far or
only test callers. `quat_lattice_sample_from_ball` needs the ideal Gram matrix,
the parallelogram bound and the PRNG.

## Decision

Port the production-reachable subset faithfully and variable-time, keeping
C's operation order:

| Kotlin | Reference |
| --- | --- |
| `QuaternionLattice(denominator, basis).coordinatesOf` | `quat_lattice_contains` |
| `LatticeCoordinates(coordinates, isMember)` | its outputs |
| `QuaternionElement.primitiveIn(order)`, `PrimitiveElement` | `quat_alg_make_primitive` |
| `evaluate2x2`, `times2x2` on `List<List<Ibz>>` | `ibz_mat_2x2_eval`, `ibz_mat_2x2_mul` |
| `adjugate2x2`, `Adjugate2x2` | `ibz_mat_2x2_inv_with_det_as_denom` |
| `inverse2x2Mod`, `ModularInverse2x2` | `ibz_mat_2x2_inv_mod` |
| `normalized2x2(exponent)` | `ibz_mat_2x2_normalize` |
| `gaussianProduct`, `gaussianGcd` | `ibz_vec_2_gaussian_mul`, `ibz_vec_2_gaussian_gcd` |

C's assertions become preconditions: `coordinatesOf` requires an upper
triangular basis of full rank, and `primitiveIn` requires membership.
Divisions round toward zero like `ibz_div`. The Gaussian remainder keeps C's
re-bounding to the divisor's coordinate bit lengths; honest operands whose
coordinates share a bit length keep their values, and the generator asserts
this for every record. A singular adjugate is `null` instead of C's untouched
output, and `ModularInverse2x2` returns the zero matrix C writes when no
inverse exists. The skipped functions above are not ported; a later caller
ports them with its own evidence, and `quat_lattice_sample_from_ball` moves to
the ideal slice.

## Independent evidence

- `tools/generate_dim2_lattice_vectors.py` models every ported operation on
  bounded `(bitlen, value)` integers in C's order on 8-bit, 64-bit and
  `p`-sized operands at all three levels. It checks values independently:
  products and evaluations against direct sums, the determinant times the
  adjugate against the scaled identity, the modular inverse against the
  identity modulo `2^e` or the all-zero result for even determinants, every
  normalized entry against `x` or `2^e - x`, Gaussian products against
  `(a0 b0 - a1 b1, a0 b1 + a1 b0)`, Gaussian gcds by mutual divisibility and
  the exact gcd norm, and lattice coordinates against exact rational solving,
  including `MAXORD_O0`, random triangular lattices and non-members.
  `quaternion/Dim2LatticeVectors.kt` holds 272 records.
- `checkIbzReference` replays all 3261 records against the checksum-pinned C
  with C assertions enabled. The adapter adds nine operations and pins
  `dim2.c` and `lattice.c`.
- `Dim2LatticeStructureTest` covers a zero first gcd operand, both basis
  preconditions and the membership precondition. JVM line and branch
  coverage of the new files is 100%.
- Nine retained `Dim2LatticeBenchmark` cases (`gaussianGcd`, `makePrimitive`
  in `O0` and `inverse2x2Mod` modulo `2^e`, per level) are checked against
  `BigInteger`: an independent Gaussian Euclid, reconstruction of the element
  with coprime coordinates, and `M M^-1 = I` modulo `2^e`.

## Alternatives

- **Port every function of both files:** rejected. Unreachable code would
  need tests of its own without a production caller.
- **Exact rational solving for membership:** rejected. Values agree, but
  bit lengths would differ from C's back-substitution.

## Consequences

Ideals, `qlapoty` and `id2iso` can build on C-identical lattice membership,
2x2 matrices and Gaussian gcds. The smoke benchmark inventory grows from 349
to 358 cases. There are no public ABI, wire-format, dependency or toolchain
changes. No constant-time, expert-review or production claims are made.
