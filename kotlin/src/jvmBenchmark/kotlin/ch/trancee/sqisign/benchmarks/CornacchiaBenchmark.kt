// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.isProbablePrime
import ch.trancee.sqisign.arithmetic.low32
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.sqrtMinusOneModVerified
import ch.trancee.sqisign.quaternion.cornacchiaPrime
import ch.trancee.sqisign.quaternion.sumOfSquares
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time Cornacchia over a deterministic public prime `1 mod 4` of `capacity / 4` bits, the
 * first such prime with a verified square root of -1 above a fixed digit pattern.
 */
@State(Scope.Benchmark)
public open class CornacchiaBenchmark {
    /** Selects the `ibz_t` capacity of NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var prime: Ibz
    internal lateinit var root: Ibz

    /** Searches the prime and its square root of -1 outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        val start = parse(capacity, ibzBenchmarkOperand(cornacchiaPrimeBits(capacity), false))
        val bound = start.bitlen + 1
        val four = Ibz.of(capacity, RESIDUE_STEP, RESIDUE_STEP_BITS)
        var candidate = (start + Ibz.of(capacity, (1 - start.low32()) and 3, 3)).withBound(bound)
        var square: Ibz? = null
        while (square == null) {
            if (candidate.isProbablePrime(BPSW_ONLY_ROUNDS, ::noRandomBytes)) {
                square = candidate.sqrtMinusOneModVerified()
            }
            if (square == null) {
                candidate = (candidate + four).withBound(bound)
            }
        }
        prime = candidate
        root = square
    }

    /** `ibz_cornacchia_prime`: square root of -1, lattice reduction and the norm check. */
    @Benchmark public open fun twoSquares(): Any? = prime.cornacchiaPrime()

    /** `quat_lll_dim2_sumofsquares` alone, from the precomputed square root of -1. */
    @Benchmark public open fun latticeReduction(): Any = sumOfSquares(prime, root)
}

private const val PRIME_FRACTION = 4
private const val RESIDUE_STEP = 4
private const val RESIDUE_STEP_BITS = 4
private const val HEX_RADIX = 16

internal fun cornacchiaPrimeBits(capacity: Int): Int = capacity / PRIME_FRACTION

private fun parse(capacity: Int, text: String): Ibz =
    requireNotNull(Ibz.parse(capacity, text, HEX_RADIX))
