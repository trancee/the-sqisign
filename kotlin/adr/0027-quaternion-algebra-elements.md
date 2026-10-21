# ADR 0027: Quaternion algebra elements, 4-vectors and 4x4 matrices

**Status:** Accepted
**Date:** 2026-10-21

## Context

The pinned `src/quaternion/ref/lvlx/algebra.c` implements elements of the
quaternion algebra `B_{p,inf} = <1, i, j, ij>` with `i^2 = -1` and
`j^2 = -p`. An element `quat_alg_elem_t` is a denominator and four integer
coordinates, `(c0 + c1 i + c2 j + c3 ij) / d`. `src/quaternion/ref/lvlx/dim4.c`
implements the `ibz_vec_4_t` vectors and `ibz_mat_4x4_t` matrices that
lattices, ideals and signing build on.

Every operation is a fixed sequence of `ibz_t` additions, multiplications,
divisions and gcds. The result bit length of each `ibz_t` operation depends
on its operands' bit lengths, so the bit lengths of a result depend on the
order of operations, not only on its value. Sums start from the one-bit zero
`ibz_set(0, 0)`. Later bounded reductions see those bit lengths. As in ADRs
0021 to 0026, the port must reproduce C's values and bit lengths.

Several functions in both files have no caller outside the C unit tests:
`ibz_mat_4x4_inv_with_det_as_denom` and its coefficient helpers,
`ibz_mat_4x4_mul`, `ibz_mat_4x4_transpose`, `ibz_mat_4x4_equal`,
`ibz_vec_4_add`, `quat_alg_sub`, `quat_alg_elem_equal` and `quat_alg_scalar`.
The exactness flags returned by `ibz_vec_4_scalar_div` and
`ibz_mat_4x4_scalar_div` are never read.

## Decision

Port the production-reachable subset faithfully and variable-time, keeping
C's operation order:

| Kotlin | Reference |
| --- | --- |
| `QuaternionElement(denominator, coordinates)` | `quat_alg_elem_t` |
| `QuaternionElement.plus` | `quat_alg_add` with `quat_alg_equal_denom` |
| `QuaternionElement.conjugate`, `scaledBy`, `normalized`, `isZero` | `quat_alg_conj`, `quat_alg_elem_scalar_mul`, `quat_alg_normalize`, `quat_alg_elem_is_zero` |
| `QuaternionAlgebra(p).multiply`, `norm` | `quat_alg_mul` with `quat_alg_coord_mul`, `quat_alg_norm` |
| `QuaternionNorm(numerator, denominator)` | the two outputs of `quat_alg_norm` |
| `content`, `scaledBy`, `dividedBy`, `vectorMinus`, `isZeroVector` on `List<Ibz>` | `ibz_vec_4_content`, `_scalar_mul`, `_scalar_div`, `_sub`, `_is_zero` |
| `evaluate`, `evaluateTransposed`, `quadraticForm`, `entryGcd`, `matrixDividedBy`, `matrixScaledBy` on `List<List<Ibz>>` | `ibz_mat_4x4_eval`, `_eval_t`, `quat_qf_eval`, `ibz_mat_4x4_gcd`, `_scalar_div`, `_scalar_mul` |
| `identityMatrix`, `zeroMatrix` | `ibz_mat_4x4_identity`, `ibz_mat_4x4_zero` |

Vectors are `List<Ibz>` and matrices are row-major `List<List<Ibz>>`, as in
the lattice reductions. Divisions return only the quotients. The test-only
functions above are not ported; a later caller would port them with their own
evidence. `quat_alg_make_primitive` needs `quat_lattice_contains` and moves to
the lattice slice.

Checked `Ibz` arithmetic throws `ArithmeticException` where C silently wraps
at capacity. No honest operand reaches capacity. The elements are not data
classes, so no generated members fall outside coverage.

## Independent evidence

- `tools/generate_algebra_vectors.py` models every ported operation on
  bounded `(bitlen, value)` integers in C's order, with operands whose bit
  lengths carry up to 70 bits of slack, negative denominators, zero
  elements, zero matrices and scaled inputs with a nontrivial content. Every
  value is checked against exact rational quaternion arithmetic: the
  product, the sum, the reduced norm in lowest terms with a positive
  denominator, normalization preserving the element with coprime content and
  positive denominator, and the matrix evaluations and quadratic form against
  direct sums. `quaternion/AlgebraVectors.kt` holds 387 records at 8-bit,
  64-bit and `p`-sized operands on all three levels.
- `checkIbzReference` replays all 2989 records against the checksum-pinned
  C with C assertions enabled. The adapter adds 15 operations and pins
  `algebra.c`, `dim4.c` and `finit.c`.
- `QuaternionStructureTest` covers the identity and zero matrix bit lengths,
  the zero tests and the coordinate-count precondition. JVM line and branch
  coverage of the new files is 100%.
- Nine retained `QuaternionAlgebraBenchmark` cases (`multiply`, `norm` and
  `quadraticForm` per level) are checked against `BigInteger`: the Hamilton
  product, the norm value in lowest terms and `x^T G x`.

## Alternatives

- **Port every function of both files:** rejected. The test-only functions
  would be unreachable production code.
- **Exact rational arithmetic with tight bit lengths:** rejected. Values
  would agree, but bit lengths would differ, and later bounded reductions
  would diverge from C.

## Consequences

Lattices, ideals and signing can build on C-identical elements and matrices.
The smoke benchmark inventory grows from 340 to 349 cases. There are no
public ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
