// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.gcd
import ch.trancee.sqisign.arithmetic.inverseMod
import ch.trancee.sqisign.arithmetic.isProbablePrime
import ch.trancee.sqisign.arithmetic.minus
import ch.trancee.sqisign.arithmetic.mod
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.powMod
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time number theory at each quaternion capacity over a deterministic public prime of
 * `capacity / 8` bits, the next prime above a fixed digit pattern.
 */
@State(Scope.Benchmark)
public open class IbzNumberTheoryBenchmark {
    /** Selects the `ibz_t` capacity of NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var prime: Ibz
    internal lateinit var value: Ibz
    internal lateinit var exponent: Ibz

    /** Parses operands and searches the prime outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        val bits = numberTheoryPrimeBits(capacity)
        prime = nextProbablePrime(parse(capacity, ibzBenchmarkOperand(bits, negative = false)))
        value = parse(capacity, ibzBenchmarkOperand(bits - 1, negative = true)).mod(prime)
        exponent = prime - Ibz.one(capacity) - Ibz.one(capacity)
    }

    /** Euclid's gcd of a residue and the prime. */
    @Benchmark public open fun gcd(): Any = value.gcd(prime)

    /** Bezout inverse of a residue modulo the prime. */
    @Benchmark public open fun inverseMod(): Any = value.inverseMod(prime)

    /** Square-and-multiply with a full-width exponent. */
    @Benchmark public open fun powMod(): Any = value.powMod(exponent, prime)

    /** Full Baillie-PSW on a prime, without extra random rounds. */
    @Benchmark
    public open fun probablePrime(): Any = prime.isProbablePrime(BPSW_ONLY_ROUNDS, ::noRandomBytes)
}

private const val PRIME_FRACTION = 8
internal const val BPSW_ONLY_ROUNDS = 24
private const val HEX_RADIX = 16

internal fun numberTheoryPrimeBits(capacity: Int): Int = capacity / PRIME_FRACTION

private fun parse(capacity: Int, text: String): Ibz =
    requireNotNull(Ibz.parse(capacity, text, HEX_RADIX))

private fun nextProbablePrime(start: Ibz): Ibz {
    val two = Ibz.of(start.capacityBits, 2, 3)
    val bound = start.bitlen + 1
    var candidate =
        (if (start.isOdd) start else start + Ibz.one(start.capacityBits)).withBound(bound)
    while (!candidate.isProbablePrime(BPSW_ONLY_ROUNDS, ::noRandomBytes)) {
        candidate = (candidate + two).withBound(bound)
    }
    return candidate
}

internal fun noRandomBytes(length: Int): ByteArray = error("Unexpected draw of $length bytes")
