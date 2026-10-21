# ADR 0001: Full-width signed division

**Status:** Superseded by [ADR 0020](0020-bounded-quaternion-integers.md) (2026-10-21)  
**Date:** 2026-10-17

## Context

The Kotlin arithmetic foundation has an immutable signed `Int656` for exact
products and an unsigned `UInt656.divideAndRemainder(UInt328)` kernel. The next
integer operation must accept a full-width signed dividend without narrowing
its quotient. Signed boundary values cannot be converted to positive signed
magnitudes by ordinary negation, and the positive quotient for
`Int656.minimum / -1` is outside the signed 656-bit range.

The pinned C reference implements `ibz_div` using absolute-value division,
truncation toward zero, and a remainder with the dividend's sign. Its source
calls this “Euclidean division,” but the resulting remainder is not necessarily
nonnegative.

## Decision

Add internal `Int656.divideAndRemainder(Int328)`, returning an immutable full
`Int656` quotient and an `Int328` remainder. The quotient truncates toward zero;
the remainder has the dividend's sign, and
`dividend = quotient * divisor + remainder`, with
`abs(remainder) < abs(divisor)`.

Reject a zero divisor with `IllegalArgumentException` and reject
`Int656.minimum / -1` with `ArithmeticException`. Compute unsigned magnitudes
with exact-width two's-complement complement-plus-one, reuse
`UInt656.divideAndRemainder(UInt328)`, then restore quotient and remainder
signs. The unsigned remainder bound guarantees it fits in `Int328`. The C
adapter imports a sign-extended 657-bit dividend, within the pinned reference's
1,920-bit integer capacity, and classifies results that do not fit the signed
656-bit output.

The operation is variable-time and carries no secret-input, constant-time, or
secure-erasure guarantee. No public API, wire format, or production dependency
is introduced.

## Alternatives

- **Floor or Euclidean division:** rejected because it changes the established
  `Int328` truncation-toward-zero contract and diverges from pinned `ibz_div`.
- **Narrow the dividend or quotient:** rejected because it loses valid wide
  quotient bits and violates the exact-width arithmetic purpose.
- **Duplicate or widen the unsigned division kernel:** rejected because the
  existing kernel already handles exact 656-bit magnitudes and returns the
  required narrow remainder.
- **Wrap minimum divided by negative one:** rejected because it returns an
  invalid positive result instead of reporting signed overflow.

## Risks

The C reference storage is larger than this operation needs; its adapter must
continue to sign-extend the 657-bit input and preserve overflow information
before encoding 656-bit outputs. Sign restoration must preserve the minimum
negative value without overflow. Variable-time division is unsuitable for
secret operands without separate constant-time analysis and implementation.

## Migration

This is an additive internal operation. Existing `Int328` division, serialized
formats, public ABI, dependencies, and toolchain remain unchanged. The Python
vector generator, pinned-C adapter, common tests, JVM benchmark matrix, project
profile, and Kotlin documentation are updated together. No consumer migration
is required.
