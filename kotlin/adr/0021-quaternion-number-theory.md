# ADR 0021: Quaternion number theory

**Status:** Accepted
**Date:** 2026-10-19

## Context

The second slice of milestone 6 ports the `ibz_t` number theory that the
quaternion, lattice and ideal routines call in the pinned
`src/mp/ref/lvlx/mp.c` and `src/mp/ref/include/primality_test.h`:
`ibz_gcd`, `ibz_xgcd`, `ibz_invmod`, `ibz_crt`, `ibz_legendre`, `ibz_invmat`
(with `ibz_invmod_2e`), `ibz_sqrt_floor`, `ibz_pow_mod`, `ibz_sqrt_mod_p`,
`ibz_sqrt_m1_mod`, `ibz_sqrt_m1_mod_verified` and `ibz_probab_prime`.
These functions return values with C's bit lengths, and the randomized ones
consume the caller's random bytes in a fixed order. Later slices compare exact
intermediate values, so both must match C.

Several C routines assert, hang or produce undefined results outside narrower
domains than their prototypes suggest:

- `ibz_xgcd` and `ibz_invmod` assert when a quotient fills the whole capacity.
- `ibz_crt` asserts when products exceed the capacity.
- `ibz_legendre` loops or asserts when the value and modulus share a factor.
- Montgomery routines need `p.bitlen + 64 < capacity`.

## Decision

Add internal extension functions on `Ibz`, all variable-time:

- **Unbounded naturals.** `IbzNatural` (trimmed words: add, subtract,
  multiply, Knuth division, shifts, Newton square root) and `IbzModRing`
  (residues, square-and-multiply, halving) do the work, so squares of
  near-capacity moduli never meet an `Ibz` bound. Results are created at C's
  bit length.
- **Montgomery residues.** Every ring modulus is odd, so `IbzModRing` keeps
  elements in Montgomery form `a R mod m` with `R = 2^(16 k)`. Callers
  convert with `enter` and `exit`; addition, halving, comparison and zero
  tests work on the form unchanged. `IbzMontgomery` multiplies by CIOS over
  16-bit digits in `Int`. Each multiply-add is at most
  `(2^16 - 1)^2 + 2 (2^16 - 1) = 2^32 - 1`, so it is exact in 32-bit
  wrapping arithmetic on every target. Kotlin/JS emulates `Long` with boxed
  objects, and `Long` products with a Knuth division per multiplication made
  the oracle tests run for over 100 s on Node. With 16-bit digits they run
  in about 2 s. JVM smoke scores stay within about 25% either way.
- **GCD family.** `gcd`, `extendedGcd`, `inverseMod` and `crt` use classical
  Euclid instead of C's Lehmer steps. Both produce the same quotient sequence,
  so the Bezout cofactors are identical. Cofactors are replayed as
  magnitudes, since Euclidean cofactors alternate in sign. `extendedGcd` and
  `inverseMod` require `max(bitlen) <= capacity - 64`, and `crt` requires
  coprime moduli and C's two product bounds. Kotlin throws
  `IllegalArgumentException` where C would assert.
- **Residues.** `jacobi` is the binary Jacobi algorithm. It requires a
  non-negative value and an odd positive modulus, and it rejects non-coprime
  non-zero values where C loops. `sqrtFloor` returns C's tight bit length.
  `inverseMatrixMod2exp` reproduces `ibz_invmat` exactly: an entry wider than
  `e + 1` bits is reinterpreted at that length by its low words, and the
  determinant and Hensel products wrap without a check, as in C.
- **Modular powers and roots.**
  - `powMod` requires reduced operands and an odd positive modulus with
    64 bits of headroom.
  - `sqrtModPrime` handles `p = 3 mod 4` by powering to `(p + 1) / 4`. It
    always returns that candidate and reports whether it squares back. Other
    odd primes use Tonelli-Shanks after drawing `rand_interval(1, p - 1)`
    until it finds a non-residue. An even modulus or a non-residue returns
    no root, matching C's untouched output.
- **The `ibz_sqrt_m1_mod` quirk.** C passes each `rand_interval(0, p)` draw
  `x` into Montgomery powering without `nresx`, so the base is really `x / R`,
  where `R = 2^(61 * ((p.bitlen + 62) / 61))`. Kotlin multiplies by `R^-1`
  explicitly. The root and the byte consumption then match C.
  `sqrtMinusOneModVerified` uses the first prime base below 140 whose symbol
  is `-1`, as `qlapoty_find_jacobi_minus_one` does.
- **Primality.** `isProbablePrime` performs these steps:
  1. Trial division by the odd primes up to 509.
  2. A strong base-2 Miller-Rabin test.
  3. A strong Lucas test with Selfridge parameters (perfect squares are
     rejected first).
  4. `rounds - 24` extra Miller-Rabin rounds on `rand_interval(2, n - 2)`
     witnesses, stopping at the first witness of compositeness.

  The step-4 helper and the Lucas test are internal so tests can reach
  branches that no real input reaches through the sieve.

## Independent evidence

- `tools/generate_number_theory_vectors.py` is an independent Python model
  stated on textbook definitions. It owns 711 records in
  `arithmetic/IbzNumberTheoryVectors.kt`, covering all three capacities.
  - Randomized records carry the exact bytes the model consumed. Kotlin must
    use all of them.
  - The prime records include Carmichael numbers, base-2 strong
    pseudoprimes, the Wieferich squares `1093^2` and `3511^2`, values at the
    capacity bound and scripted extra rounds.
- `checkIbzReference` replays these records together with the 944 `Ibz`
  records against the checksum-pinned C. `tools/ibz_reference.c` now
  includes the pinned `modqx.c`, and missing random bytes abort the run
  instead of looping.
- `IbzNumberTheoryTest` covers every precondition, and composites without
  small factors reach the remaining Miller-Rabin and Lucas branches.
  Together these give 100% JVM line and branch coverage.
- Twelve retained `IbzNumberTheoryBenchmark` cases are checked against
  `BigInteger`: gcd, inverse, modular power and full Baillie-PSW over a
  deterministic `capacity / 8`-bit prime at each level.

## Alternatives

- **Port Lehmer's algorithm:** rejected. It gives no difference in results,
  needs more code, and these calls are not hot.
- **Reduce products by Knuth division:** rejected after measurement. It is
  simpler, but on Kotlin/JS a single near-capacity modular power ran for tens
  of seconds and exceeded the 30-second Mocha limit.
- **Model C's Montgomery arithmetic word for word:** rejected. Only the
  `x / R` base of `ibz_sqrt_m1_mod` is observable, and an explicit `R^-1`
  reproduces it.
- **Mirror C's undefined or asserting inputs:** rejected. Kotlin rejects them
  with `IllegalArgumentException` instead of hanging or wrapping.

## Consequences

The LLL, quaternion and ideal slices can use exact `ibz_t` number theory and
C's randomness consumption. The smoke benchmark inventory grows from 304 to
316 cases. There are no public ABI, wire-format, dependency or toolchain
changes. No constant-time, expert-review or production claims are made.
