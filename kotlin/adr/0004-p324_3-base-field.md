# ADR 0004: Initial `p324_3` base field

**Status:** Accepted
**Date:** 2026-10-17

## Context

The field-core milestone follows the portable integer foundation and the
SQIsign v3 `p324_3` parameter set. `UInt328` already represents every canonical
base-field residue and `UInt656` retains exact products, but neither type
performs field reduction. The C reference uses a generated Montgomery
representation; that representation is an implementation detail and its
generated arithmetic source has parser gaps in the codebase graph.

## Decision

Add internal `Fp324` for canonical residues modulo
`p = 3 * 2^324 - 1`. Decode exactly 41 little-endian bytes and reject
representatives `>= p`; never silently reduce serialized field values. Addition,
subtraction, negation, multiplication and squaring return canonical residues.
Multiplication computes the exact `UInt656` product and uses unsigned division
to obtain its remainder modulo `p`. Inversion uses exponent `p - 2` and rejects
zero. Since `p = 3 (mod 4)`, square root uses `(p + 1) / 4`, verifies the result,
returns null for non-residues, and chooses the smaller root.

Keep the Kotlin representation canonical rather than importing Montgomery
limbs. This preserves an explicit encoding invariant and reuses the existing
portable arithmetic without introducing a second modular representation.
Expose no public API and add no runtime dependencies.

## Alternatives

- **Adopt the C Montgomery representation:** rejected for this first portable
  layer because it would couple Kotlin code to the reference's limb radix and
  complicate canonical wire conversion.
- **Add specialized pseudo-Mersenne reduction:** deferred until correctness
  and a reproducible performance requirement justify a more complex reducer.
- **Use JVM big integers or native C interop:** rejected because field code
  belongs in `commonMain` and production code must remain dependency-free.
- **Silently reduce during byte decoding:** rejected because noncanonical
  attacker-supplied encodings must be rejected at the boundary.

## Risks

The implementation relies on the selected prime being prime and congruent to
three modulo four. Exponentiation and division are variable-time, while
long-division reduction is expected to be slow. Python modular arithmetic and
the pinned C field implementation provide independent evidence, but neither
proves constant-time behavior, secret-input safety, or expert cryptographic
review. At this initial slice, `Fp2`, field sign/select operations, and the
`p500_27` and `p664_17` widths were not yet implemented; their completed scope
is recorded in ADR 0006.

## Migration

This is an internal additive arithmetic layer. No public ABI, wire format,
dependency, build tool, parameter-set behavior or release version changes.
Regenerate `TestVectors.kt` with `python3 tools/generate_test_vectors.py`.
`checkUnsignedReference` verifies fixture generation and cross-checks 113
field-operation records plus six inverse/residue/root cases against the pinned
`p324_3` reference files and Python modular integers. The wider parameter sets,
extension fields, and canonical controls are completed in ADR 0006.
