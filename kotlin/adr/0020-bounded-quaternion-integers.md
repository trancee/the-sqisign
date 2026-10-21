# ADR 0020: Bounded quaternion integers

**Status:** Accepted
**Date:** 2026-10-21
**Supersedes:** ADRs 0001, 0002 and 0003

## Context

Milestone 6 ports the quaternion and lattice core (specification §4.3, pinned
`src/quaternion/ref`). Every quaternion, lattice and ideal routine uses the
signed `ibz_t` of the pinned `src/mp/ref/lvlx/mp.c`, not a fixed 328- or
656-bit integer. An `ibz_t` carries a bit length `bitlen` next to its words:
its value is the two's complement of its first `ceil(bitlen / 64)` words.
Each parameter set fixes a capacity: 1920 bits for `p324_3`, 2560 for
`p500_27` and 3520 for `p664_17`. Operations derive the result's `bitlen`
from the operands' bit lengths, clamped to that capacity.

The milestone-1 `Int328` and `Int656` types (ADRs 0001-0003) were fixed-width
stand-ins written before these bounds were known. No production code used
them, and they cannot hold quaternion intermediates.

## Decision

Replace `Int328` and `Int656` with one internal immutable `Ibz`, and delete the
old types together with their tests, benchmarks, oracle modes and fixtures.

- **Representation.** `Ibz(capacityBits, bitlen, words: LongArray)` stores the
  two's complement of `limbs(bitlen)` 64-bit words. Values are immutable, and
  every accessor that exposes words returns a copy.
- **Bit-length rules match C.**
  - Sums use `min(max(a, b) + 1, capacity)` bits.
  - Products use `min(a + b - 1, capacity)` bits.
  - Left shifts use `min(bitlen + e, capacity)` bits.
  - `zero` and `one` use bit lengths 1 and 2.
  - `withBound`, `plusWithBound`, `plusIntWithBound` and `timesIntWithBound`
    reproduce C's explicit `bitlen` arguments, including truncation by sign
    extension.
- **Checked overflow (departure from C).** C keeps the wrapped words when a
  result exceeds the capacity. Kotlin throws `ArithmeticException` instead:
  - checked `plus` and `minus` throw when the exact result does not fit in
    `64 * limbs(bits)` bits;
  - `times` throws when the product needs more than the capacity, or exactly
    the capacity while the requested length is larger;
  - `shiftLeft` throws only when the length is clamped and bits are lost.

  Internal callers that rely on C's bounded truncation use the explicit
  `checked = false` or `*WithBound` forms, so every wrap is visible at its call
  site. The oracle marks each wrapping record with `! `, and Kotlin must throw
  on it.
- **Preserved C behaviour.**
  - `divRem` truncates toward zero, and a nonzero remainder takes the
    dividend's sign. `mod` adds `|modulus|` to a negative remainder.
  - `shiftRight` is C's logical shift of the stored words, not an arithmetic
    shift of the value, so negative inputs produce C's exact result.
  - `mod2exp`, `twoAdic`, `low32`, the 64-bit extractors, `sizeInBase` and the
    digit export all follow `mp.c`.
- **Text (partial departure).** `toString(radix)` and `parse(radix)` support
  only radices 10 and 16. Any other radix is a caller error. `parse` returns
  `null` for an empty body, a non-ASCII or invalid digit, or a value above the
  capacity. C's `ibz_set_from_str` silently wraps in the last case.
- **Randomness.** `randomInterval` and `randomSymmetric` follow C's
  reduction: they draw 64 bits more than the bound, then reduce, so the
  statistical distance from uniform is below `2^-64`. They take a caller-supplied
  `(Int) -> ByteArray` source. Entropy policy stays with the milestone-8 signer.
- **Algorithms.**
  - Multiplication is schoolbook over 32-bit digits with `Long` accumulators,
    which is exact on JVM, JS and Native.
  - Division is Knuth algorithm D (Hacker's Delight `divmnu`) over 32-bit
    digits.
  - Everything is variable-time.

## Independent evidence

- `tools/generate_ibz_vectors.py` is an independent Python model, stated on
  `(bitlen, value)` pairs instead of C limb loops. It owns 944 records in
  `arithmetic/IbzVectors.kt`. The records cover all three capacities, signed
  boundaries at 12 bit lengths, near-capacity operands, overflow markers,
  every unary and binary operation, text, digit export and both random
  samplers.
- `tools/check_ibz_reference.py` (Gradle `checkIbzReference`, which is part of
  `check` and CI) compiles the checksum-pinned `mp.c` through
  `tools/ibz_reference.c`. It requires C to reproduce every record. For `! `
  records it requires C's wrapped value.
- Common tests replay the corpus on every runtime. They also reach every
  Knuth D branch: the `qhat >= b` correction, the `rhat` overflow exit and the
  add-back step use operands found by a targeted Python search. Together
  they give 100% JVM line and branch coverage.
- Six retained `IbzBenchmark` cases per operation (add, multiply and
  divide-with-remainder at all three capacities, word-sized and
  half-capacity operands) are checked against `BigInteger`.

## Alternatives

- **Keep `Int328`/`Int656` and add wider fixed types:** rejected. Quaternion
  bounds depend on the parameter set and on intermediate bit lengths. More
  fixed widths would duplicate kernels without matching C's `bitlen`
  semantics.
- **`BigInteger` or a dependency:** rejected. It is not multiplatform, and
  `PORTING.md` requires explicit bounds without native or third-party
  big-number runtimes.
- **Wrap silently like C:** rejected. Repository rule: never silently truncate
  a bound violation. Intentional truncation stays explicit through the
  bounded forms.

## Consequences

The quaternion slices build on one integer type whose bit lengths match C.
Fixture-based differential tests can then compare exact intermediate values.
The smoke benchmark inventory changes from 363 to 304 cases: 77 legacy signed
cases are removed and 18 `IbzBenchmark` cases are added. There are no public
ABI, wire-format, dependency or toolchain changes. No constant-time,
expert-review or production claims are made.
