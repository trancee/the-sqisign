# ADR 0016: Montgomery field multiplication and bounded verification runtime

**Status:** Accepted
**Date:** 2026-10-18

## Context

Complete basis actions, changes, and correspondences (ADR 0015) made the
forced gate take hours. Measured before this decision:

- JVM: 965 tests, `2455s` summed test time; `p664_17` accounted for `1002s`.
- Node: the 272 changed basis tests alone summed `4345s`; single tests reached
  `253s`, and the Mocha per-test limit had been raised from `30s` to `600s`.
- macOS ARM64 Native did not finish the same tests within two hours. A stack
  sample showed only active field arithmetic beneath the discrete logarithm.

The user rejected recurring hours-long verification. Raising timeouts is
therefore no longer an acceptable remedy.

The cause was the field-multiplication kernel. Wider fields multiplied by
bitwise double-and-add over `ByteArray`. This performed about 1,300 allocating
modular additions per `p664_17` product. `p324_3` reduced exact products by
656-step restoring binary division. Exponentiations (inverse, roots, and residue
tests) multiplied this cost.

## Decision

Add the internal `FieldModulus`. It precomputes Montgomery constants for one odd
prime: 16-bit digits, `-p^-1 mod 2^16`, `R^2 mod p` for
`R = 2^(16 * digits)`, and the fixed inverse and square-root exponents.
Multiplication applies the CIOS Montgomery product twice
(`mont(mont(a, b), R^2)`). Inputs and outputs therefore stay canonical
little-endian residues. Exponentiation converts into the Montgomery domain
once, squares and multiplies there, and converts back once.

All partial sums are bounded by `(2^16 - 1)^2 + 2 * (2^16 - 1) < 2^32`. They are
exact unsigned 32-bit values in Kotlin `Int` on JVM, JS (`Math.imul`), and Native,
so no `Long` arithmetic is needed. The final conditional subtraction uses the
borrow, so no comparison has an unreachable equality path.

`Fp500P27`, `Fp664P17`, their quadratic extensions, and `Fp324` multiplication
and exponentiation all use `FieldModulus`. Addition, subtraction, canonical
encoding, ordering, root selection, and all public behavior are unchanged.
`UInt328`/`UInt656` keep their exact-integer contracts for other callers.

Kotlin/Native test executables are built with `optimized = true` and
`debuggable = false`. Native tests now exercise optimized compiled code, and
symbolized debug stacks are not provided. The Node Mocha per-test limit returns
to its original `30s`. The CI check-job timeout returns from 60 to
30 minutes (ADR 0009 had doubled it).

## Evidence

The behavior-preserving refactor passes every existing independent Python vector
and pinned C field check. The refactor itself adds no vector.

| Measurement (local macOS ARM64) | Before | After |
| --- | --- | --- |
| JVM `p664` correspondence `fullOrder` | `60.6s` | `2.6s` |
| JVM `p500` change `curve2ReducedDense` | `12.7s` | `0.66s` |
| JVM summed test time, 965 tests | `2455s` | `96.3s` |
| Node, slowest of 878 tests | `253s` | `6.0s` |
| Node summed, 878 tests (before: 272 only) | `4345s` | `180s` |
| Native `p664` correspondence `fullOrder`, debug binary | `53.3s` | `32.2s` (after hoisting) |
| Native `p664` correspondence `fullOrder`, optimized | n/a | `6.0s` |
| Native summed, 878 tests | not finished in 2h (debug) | `168.5s` |
| Forced `clean check` wall time | hours | `12m14s` |
| JVM benchmark smoke, 348 cases | `20m48s` | `2m24s` |

These are single local runs, not controlled benchmarks. They show the order of
magnitude, not a performance budget.

## Alternatives

- **Keep raising timeouts:** rejected by the user. The cost recurs on every gate.
- **Run exhaustive vectors only on JVM or on a schedule:** rejected. It weakens
  the C1 cross-platform evidence model, and the arithmetic fix makes it
  unnecessary.
- **Knuth long division:** rejected because rare quotient-correction branches
  are hard to cover deterministically for T3.
- **Keep elements in Montgomery form:** deferred because it changes the internal
  representation more widely than this bounded fix.

## Consequences

The arithmetic remains variable-time, and no constant-time or production claims
are made. Native test failures report less precise stacks. Linux Native runtime
proof still requires owning CI.
