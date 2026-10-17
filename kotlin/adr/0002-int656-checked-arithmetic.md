# ADR 0002: Checked wide signed arithmetic

**Status:** Accepted
**Date:** 2026-10-17

## Context

`Int656` retains exact signed products and full-width signed division
quotients in an immutable 82-byte two's-complement representation. Callers
need to compare and combine those intermediates without narrowing or silently
wrapping results. The reference C integer layer supports operands and results
well above 656 bits, so it can independently classify signed overflow at this
width.

## Decision

Add internal signed comparison, checked addition, and checked subtraction to
`Int656`. Comparison returns `-1`, `0`, or `1`. Arithmetic returns exact,
immutable `Int656` values or throws `ArithmeticException` with an operation-
specific message when the mathematical result is outside
`[-2^655, 2^655 - 1]`.

Perform byte-wise two's-complement addition/subtraction at the encoded width,
then detect signed overflow from the operand and result signs. Compare sign
first, followed by unsigned byte order from most to least significant. Never
mutate either input or expose result storage. These operations are variable-
time; this decision makes no secret-input, constant-time, or secure-erasure
claim.

## Alternatives

- **Wrap modulo `2^656`:** rejected because it silently changes exact signed
  intermediate values.
- **Return a wider value or overflow flag:** rejected because no wider result
  type or caller contract is required for these bounded operations.
- **Reuse a platform big integer:** rejected because it would add a production
  dependency and break `commonMain` portability.
- **Duplicate the `Int328` algorithm over signed limb storage:** rejected in
  favor of operating directly on `Int656`'s owned bytes without introducing a
  second representation.

## Risks

Overflow must be classified from sign transitions, not the unsigned carry or
borrow, which are not equivalent for signed two's-complement values. Boundary
and deterministic full-width Python/C vectors check both operations and
comparison against the pinned reference. Fixed-width byte loops are variable-
time with respect to allocation/runtime behavior and are not a leakage proof.

## Migration

This is additive internal arithmetic. No public ABI, serialized format,
dependency, or toolchain changes. The generated fixture schema is new and
leaves existing records unchanged. Common tests, JVM workload checks, the exact
CI smoke matrix, project profile, and Kotlin reference docs are updated in the
same change. There are no current callers to migrate.
