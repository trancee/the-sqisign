# ADR 0006: Complete field core for all parameter sets

**Status:** Accepted
**Date:** 2026-10-17

## Context

The initial field slices recorded in ADRs 0004 and 0005 implemented only the
`p324_3` base field and quadratic extension. The milestone now requires matching
internal field behavior for all three SQIsign v3 parameter sets, including
canonical root ordering and explicit sign, selection, and swap semantics.
Representations must remain portable in Kotlin `commonMain`, reject malformed
or noncanonical encodings, and avoid introducing a production C or big-integer
dependency.

## Decision

Provide internal base-field types `Fp324`, `Fp500P27`, and `Fp664P17` for
`p324_3`, `p500_27`, and `p664_17`, respectively. Each imports only its exact
canonical little-endian width, rejects representatives at or above its prime,
and implements addition, subtraction, negation, multiplication, squaring,
inversion, residue testing, and verified canonical square roots. Keep `Fp324`
on the existing `UInt328`/`UInt656` arithmetic; use common byte-backed modular
helpers for the wider fields. (Multiplication and exponentiation for all three
fields are superseded by
[`ADR 0016`](0016-montgomery-field-multiplication.md).)

Provide `Fp2P324`, `Fp2P500`, and `Fp2P664` over `Fp[i]/(i^2 + 1)`, encoded as
the real component followed by the imaginary component, each canonical and
little-endian. Their arithmetic, inverses, residue tests, and square roots use
the corresponding base field. Order extension values by encoded order:
imaginary component first, then real component.

Define `isNegative(value)` as `value > -value` under canonical field ordering,
so zero is nonnegative. Define `select(first, second, selectSecond)` to return
`second` only when the Boolean is true. Define conditional swap to return the
two immutable inputs in original or reversed order according to its Boolean.
These operations use ordinary control flow and are explicitly variable-time;
they make no constant-time or secret-input guarantee.

Keep the classes internal and dependency-free. Verify behavior with common
Kotlin tests, independent Python integer vectors, and the pinned C reference
adapter. The C adapter maps Boolean selection to the reference's all-zero or
all-one mask and swap to its zero-or-one control.

## Alternatives

- **Keep only the `p324_3` implementation:** rejected because it leaves the
  other supported parameter sets without the field behavior required by the
  milestone.
- **Use the reference Montgomery limbs for all widths:** rejected because it
  would couple common Kotlin code to reference-specific representations and
  require a second encoding boundary.
- **Use JVM big integers or C interop for wider fields:** rejected because
  these operations belong in `commonMain` and must not add platform-specific
  or production runtime dependencies.
- **Expose mask-based or constant-time selection:** rejected because a
  source-level mask or branch-free expression cannot establish constant-time
  behavior across JVM, JS, and Kotlin/Native.
- **Order extension values by real component first:** rejected because it
  differs from the reference encoding comparison and could select a different
  canonical root.

## Risks

All arithmetic, comparison, root selection, Boolean selection, and swapping are
variable-time. The byte-backed wide-field implementation prioritizes portable
correctness and independent checks; it has no measured performance budget.
Python/C agreement does not establish prime validation, constant-time behavior,
secret-input safety, independent expert cryptographic review, or production
readiness. Linux validation cannot execute the macOS ARM64 Native test.

## Migration

This is an internal additive implementation. It changes no public ABI, wire
format, runtime dependency, published API, or release version. The earlier
ADRs 0004 and 0005 document the initial `p324_3` slices; this decision extends
their scope without changing their canonical representation.

`generate_test_vectors.py` owns the common Python fixtures. Arithmetic and
nonlinear records cover 113 `p324_3` base-field pairs, 48 pairs for each wider
base field, and 64 operation pairs plus ten nonlinear cases for each extension
field. `check_unsigned_reference.py` checks these against Python integers and
the pinned C field implementation. It also checks sign, selection, and swap
for both Boolean values over every generated base- and extension-field
operand pair. Common tests exercise the byte/arithmetic boundaries and both
control outcomes.
