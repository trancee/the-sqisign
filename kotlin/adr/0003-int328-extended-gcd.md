# ADR 0003: Signed extended GCD for `Int328`

**Status:** Accepted
**Date:** 2026-10-17

## Context

Milestone 1 needs signed Bezout coefficients for later quaternion and lattice
arithmetic. `Int328` represents `[-2^327, 2^327 - 1]`; taking the magnitude of
its minimum value requires the unsigned value `2^327`, so neither a signed
absolute-value operation nor a signed GCD result can represent every valid
case. The operation also needs a defined zero-input convention and coefficient
range without changing the public SQIsign API.

## Decision

Add internal `Int328.extendedGcd(other)`, returning an immutable result with
`UInt328 gcd`, `Int328 leftCoefficient`, and `Int328 rightCoefficient`.
The GCD is nonnegative and the coefficients satisfy
`leftCoefficient * left + rightCoefficient * right = gcd`. For `(0, 0)`, all
three fields are zero. If exactly one input is zero, its coefficient is zero
and the nonzero operand's coefficient is its sign. The coefficient pair is
not canonical; callers must depend on the GCD and Bezout identity rather than
specific coefficients.

Run Euclid on the exact unsigned magnitudes. Keep the two coefficient recurrences
in signed `Int328` and form `quotient * coefficient` exactly as `Int656` before
checked subtraction and narrowing. In the terminal division, retain the
coefficients associated with the nonzero GCD remainder and do not calculate the
coefficient for the zero remainder: that unused value can exceed the signed
328-bit range even though the returned coefficients fit. Apply each original
operand's sign to its coefficient after Euclid completes. Inputs and returned
values remain immutable. The algorithm is variable-time and makes no
secret-input or constant-time claim.

## Alternatives

- **Return the GCD as signed `Int328`:** rejected because `gcd(Int328.minimum, 0)`
  is `2^327`, outside the positive signed range.
- **Return only a GCD:** rejected because later quaternion/lattice callers need
  the Bezout relation.
- **Canonicalize coefficients:** rejected because no caller requires a unique
  solution; imposing one would add unnecessary quotient/reduction work and
  could constrain later implementations.
- **Use a platform big integer:** rejected because it would add a production
  dependency and break `commonMain` portability.
- **Compute the unused final coefficient:** rejected because it may exceed the
  bounded signed result type and is not needed to identify the GCD.

## Risks

Intermediate products must remain exact before subtraction, and every retained
coefficient must fit signed 328 bits. The final-zero-step rule prevents an
irrelevant oversized coefficient from causing failure. Boundary, deterministic
full-width, signed-minimum and Fibonacci-chain vectors exercise this behavior.
Python supplies an independent integer oracle; pinned C `ibz_xgcd` supplies an
independent implementation. C may choose other valid coefficients, so its
GCD is compared directly and each returned coefficient pair is checked by the
Bezout identity. Tests do not establish a constant-time implementation,
expert cryptographic review, or secret-input suitability.

## Migration

This is additive internal arithmetic; no public ABI, wire format, dependency,
toolchain, or release-version change is required. A separate generated fixture
list and `--extended-gcd` C adapter mode preserve existing records. Common
tests, seven JVM benchmark workloads, the exact 156-case CI smoke matrix,
`PROJECT.md`, `README.md`, and `PORTING.md` are updated together. There are no
current repository consumers to migrate.
