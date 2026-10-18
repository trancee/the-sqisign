# ADR 0013: Prepared power-of-two discrete logarithms

**Status:** Accepted
**Date:** 2026-10-18

## Context

Algorithm 4.106 solves four logarithms with the same primitive root.
Algorithm 4.37 specifies a balanced Pohlig-Hellman decomposition, avoiding
quadratic repeated full-length bit extraction. Torsion coefficients use
the positive-exponent ring from ADR 0012.

## Decision

Prepare a typed internal `PowerOfTwoDiscreteLog` for one field, positive
exponent at most that field's `f`, and primitive generator of order `2^e`.
Check `g^(2^(e-1)) = -1`; this proves the exact order without a redundant
subgroup predicate. Precompute inverse generator powers once. Since their
order divides `p+1`, conjugation is inversion for these powers, not for
arbitrary extension-field elements.

Use the balanced decomposition in algorithm 4.37. Solve the low half from
the appropriately squared value, correct the residual using the prepared
inverse powers, solve the high half and assemble all bits into a strict
`TorsionScalar`. A leaf accepts only one or minus one; other inputs, including
zero and values outside the subgroup, fail explicitly. Do not return a
success-shaped digit for a nonmember.

The prepared positive-exponent seam intentionally omits the specification's
trivial exponent-zero scalar case: checked bases have positive exponents and
balanced recursion never creates an empty half. Invalid exponents fail before
generator processing. No field representation or ordinary ladder changes.

## Alternatives

- **Repeated full-order bit extraction:** rejected; its quadratic squaring
  count is unnecessary at the 664-bit parameter.
- **Copy the C mutable ancestor stack:** the balanced mathematical decomposition
  is simpler; prepared generator powers retain reuse without coupling callers
  to mutable stack state.
- **Check subgroup membership before recursion:** rejected as duplicate
  computation; explicit leaf checks establish membership while solving.

## Risks and migration

All operations are variable-time and unsuitable for secret inputs. Field
operations, leaf digits and recursion are not constant-time. Independent
Python exponentiation and pinned C logarithms must verify zero/high-bit,
odd/even split lengths, full widths and malformed mathematical inputs.
Retained representative workloads and all supported runtime checks remain
required; they do not establish numeric budgets or a controlled baseline.
`tools/generate_pairing_vectors.py` owns 57 independent Python power records
in common `PairingVectors.kt` and benchmark-only `DiscreteLogInputs.kt`.
The existing `checkCurveReference` gate verifies both outputs and all 57
logarithms against the pinned C routine. Its adapter checks primitive order
and membership before entering C's assertion-based logarithm function.
Malformed encodings, truncated inputs, invalid exponents, nonprimitive
generators and nonmembers fail explicitly in the adapter as well as Kotlin.
The pairing header is additionally pinned and watched by CI.

Twenty-four retained `DiscreteLogBenchmark` cases cover zero/maximum
coefficients, two/full torsion and every parameter. `solve` excludes preparation;
`prepareAndSolve` additionally includes a fresh order check and power table.
Both retain and independently check every scalar bit. The complete smoke
matrix grows from 288 to 312 cases. No production coverage exclusions are
added. Expert cryptographic review remains unestablished.

The seam is internal. Public ABI, wire formats, runtime dependencies,
toolchain and release version are unchanged. Tate pairings and complete
basis changes require their own maximal-curve and exceptional-domain evidence.
