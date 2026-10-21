// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.bitSize
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.quaternion.LllConfig
import ch.trancee.sqisign.quaternion.reduceO0Ideal
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time Lagrange-Gauss reduction over `Z[i]` of a deterministic honest inert left
 * `O0`-ideal whose norm is a product of two inert primes, with the reference bound of the norm's
 * width plus two.
 */
@State(Scope.Benchmark)
public open class GaussianBenchmark {
    /** Selects NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var config: LllConfig
    internal lateinit var hnf: List<List<Ibz>>
    internal lateinit var prime: Ibz
    internal var bound: Int = 0

    /** Parses the ideal outside the measured operation. */
    @Setup
    public fun prepare() {
        val parameters = ParameterSet.valueOf(parameterSet)
        val capacity = ibzBenchmarkCapacity(parameterSet)
        config = LllConfig(parameters)
        val values =
            gaussianInputs(parameters).map { requireNotNull(Ibz.parse(capacity, it, HEX_RADIX)) }
        val zero = Ibz.zero(capacity)
        val one = Ibz.one(capacity)
        val (twoN, e, dq) = values
        val (c, b) = values.drop(TAIL)
        hnf =
            listOf(
                listOf(twoN, zero, e, dq),
                listOf(zero, twoN, c, b),
                listOf(zero, zero, one, zero),
                listOf(zero, zero, zero, one),
            )
        prime = values.last()
        // ibz_get_bound(N) + 2: the signed width of N is the magnitude width of 2N.
        bound = twoN.bitSize() + 2
    }

    /** `quat_lll_reduce_O0_ideal`: Lehmer passes, Hermitian key and orientation. */
    @Benchmark public open fun reduceO0Ideal(): Any = config.reduceO0Ideal(hnf, prime, bound)
}

private const val HEX_RADIX = 16

/** Index of `C` among the generated inputs. */
private const val TAIL = 3
