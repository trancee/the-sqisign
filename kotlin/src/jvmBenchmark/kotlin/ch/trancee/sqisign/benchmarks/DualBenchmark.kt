// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.quaternion.LllConfig
import ch.trancee.sqisign.quaternion.dualReduceIdeal
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time dimension-4 dual reduction of a deterministic honest product of two inert
 * `O0`-ideals whose norms each have half the prime's bits plus 15.
 */
@State(Scope.Benchmark)
public open class DualBenchmark {
    /** Selects NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var config: LllConfig
    internal lateinit var hnf: List<List<Ibz>>
    internal lateinit var prime: Ibz

    /** Parses the ideal outside the measured operation. */
    @Setup
    public fun prepare() {
        val parameters = ParameterSet.valueOf(parameterSet)
        val capacity = ibzBenchmarkCapacity(parameterSet)
        config = LllConfig(parameters)
        val values =
            dualInputs(parameters).map { requireNotNull(Ibz.parse(capacity, it, HEX_RADIX)) }
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
    }

    /** `quat_lll_dual_reduce_ideal`: LLL tours, Minkowski step and basis materialisation. */
    @Benchmark public open fun dualReduceIdeal(): Any = config.dualReduceIdeal(hnf, prime)
}

private const val HEX_RADIX = 16

/** Index of `C` among the generated inputs. */
private const val TAIL = 3
