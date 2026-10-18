# ADR 0012: Torsion scalars and basis matrices

**Status:** Accepted
**Date:** 2026-10-18

## Context

Algorithms 3.7-3.10 and 4.106 need coefficients in `Z/(2^e)`, including
all 664 torsion bits. Ordinary curve ladders instead accept the entire
parameter encoding width without a torsion premise (ADR 0009).
Neither field elements nor the existing bounded signed integers represent
this ring. The user approved internal mathematical test seams for this layer.

## Decision

Use an immutable internal `TorsionScalar`, with `1 <= e <= 664`, owning
exactly `ceil(e/8)` unsigned little-endian bytes. Decode strictly: reject
other widths and nonzero unused high bits rather than reducing input.
Arithmetic explicitly operates modulo `2^e`; mixed exponents fail before
evaluation. Export to an ordinary ladder zero-extends to its parameter
width only after checking that the exponent fits that parameter's torsion.

Use byte arithmetic with immediate carry normalization. Addition accumulates
at most 511; subtraction lies in `[-256,255]`; each schoolbook product
accumulator is at most `255*255 + 255 + 255 = 65535`.
Discarded product terms and carries are multiples of the declared modulus,
not overflow in an exact-integer operation. Odd inversion uses Newton
lifting `x <- x*(2-a*x)`, doubling the number of correct bits each iteration;
even residues fail explicitly.

An internal row-major `TorsionMatrix` keeps a single exponent, uses the
column action specified by algorithm 3.7, and requires an odd determinant
where invertibility is needed. Algorithm 3.10 normalization selects the
lexicographically smaller of `M` and `-M mod 2^e`, including zero and
self-negative half-modulus entries. Matrix inversion uses the adjugate
and the odd determinant's ring inverse.

## Alternatives

- **Reuse parameter-width ladder bytes directly:** rejected; those retain
  bits outside the torsion ring and do not establish canonical residues.
- **Use field residues or narrow signed integers:** rejected; the modulus,
  range and overflow semantics differ.
- **Introduce a general arbitrary-precision integer framework:** unnecessary
  for this bounded ring and its demonstrated basis-change consumers.
- **Normalize individual matrix columns independently:** rejected; x-only
  differential bases permit one global sign, not independent column signs.

## Risks and migration

This is variable-time experimental arithmetic, not suitable for secret inputs.
The generator `tools/generate_torsion_ring_vectors.py` owns 462 Python
scalar records and 276 matrix records in common tests. All agree with
checksum-pinned C integer/matrix operations, including exponents one,
non-byte-aligned boundaries, all parameter torsion widths, and response
widths. C matrix normalization can leave a negated zero entry equal to
`2^e`; the external adapter explicitly compares its residue modulo `2^e`.
The Kotlin result is always canonical, including those zero entries.
The C adapter rejects malformed, truncated, noncanonical and overlong inputs.

`checkTorsionRingReference` verifies generated content and all C records as
part of `check`; CI watches the additional reference dependencies.
Six retained `TorsionRingBenchmark` operations cover two/full torsion at
every parameter, with every output independently checked using JVM
`BigInteger`. Setup constructs operands only; timing retains canonical
results, complete Newton inversion, normalization and matrix inversion.
The exact smoke matrix grows from 252 to 288 cases. These benchmarks
establish executability, not a controlled performance
baseline or numeric resource budget. Expert cryptographic review remains
unestablished.

These internal contracts do not change public ABI, SQIsign wire formats,
runtime dependencies, toolchain or release version. Ordinary ladder semantics
remain unchanged. Pairings, complete basis changes, curve normalization and
isogeny chains require their own subsequent evidence.
