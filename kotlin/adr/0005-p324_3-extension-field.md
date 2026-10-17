# ADR 0005: `p324_3` quadratic extension field

**Status:** Accepted
**Date:** 2026-10-17

## Context

The field-core milestone requires `Fp2 = Fp[i]/(i^2 + 1)` in addition to the
base field. Internal `Fp324` now provides canonical residues modulo
`p = 3 * 2^324 - 1`; the reference encoding stores the real component followed
by the imaginary component, each in 41 little-endian bytes. The reference
`fp2_less_than` compares encoded values by the imaginary component first and
the real component second.

## Decision

Add internal `Fp2P324` over `Fp324`, with strict 82-byte real-then-imaginary
decoding, component-wise addition/subtraction/negation, multiplication,
squaring, inversion, quadratic-residue testing, and roots derived from verified
base-field roots.
Reject noncanonical encodings and inversion of zero. Select the smaller root
using the reference ordering: imaginary component first, then real component.
Keep the representation canonical and portable; do not expose a public API or
add runtime dependencies.

Generate deterministic operation and nonlinear vectors from Python integer
arithmetic, and cross-check them against the pinned C `fp2` and base-field
implementations. The test seam is the `Fp2P324` arithmetic and canonical-byte
boundary in common tests; the external C adapter remains test-only.

## Alternatives

- **Reuse the reference Montgomery representation:** rejected because it
  couples common Kotlin code to reference-specific limbs and complicates wire
  conversion.
- **Derive roots only from exponentiation in the extension field:** rejected
  in favor of the norm-based algorithm, which reuses the verified base-field
  square-root operation and directly matches the reference behavior.
- **Choose roots by real component first:** rejected because it differs from
  the reference's encoded ordering.
- **Add constant-time selection and sign semantics in this slice:** deferred in
  this initial scope; they are now specified in ADR 0006 but remain
  variable-time and make no constant-time claim.

## Risks

The implementation depends on the base field's prime modulus and the
nonresidue `-1`. The norm and root computations use variable-time operations,
and the exact-product reduction uses bitwise long division. Python and pinned C
agreement provide differential evidence but do not prove constant-time
behavior, secret-input safety, performance suitability, or expert
cryptographic review. At this initial decision, other parameter sets and the
remainder of specification 4.2 remained out of scope; ADR 0006 records the
subsequent cross-parameter implementation.

## Migration

This is an internal additive arithmetic layer. No public ABI, wire format,
dependency, build tool, parameter-set behavior, or release version changes.
Regenerate `TestVectors.kt` with `python3 tools/generate_test_vectors.py`.
`checkUnsignedReference` verifies 64 extension-field operation records and
ten inverse/residue/root records against the pinned C implementation and
independent Python integers. The wider parameter sets and canonical controls
are completed in ADR 0006.
