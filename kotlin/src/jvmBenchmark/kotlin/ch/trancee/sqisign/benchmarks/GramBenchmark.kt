// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.quaternion.GramForm
import ch.trancee.sqisign.quaternion.LllConfig
import ch.trancee.sqisign.quaternion.gramLehmer
import ch.trancee.sqisign.quaternion.materialiseBlock
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time dimension-2 Gram kernel on a deterministic public block that violates the Lovász
 * condition: `D_k` fills the diagonal bound, `D_{k+1}` has half its bits, and `mu` is in `[1/4,
 * 1/2)`.
 */
@State(Scope.Benchmark)
public open class GramBenchmark {
    /** Selects NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var config: LllConfig
    internal lateinit var dk: Ibz
    internal lateinit var dk1: Ibz
    internal lateinit var mu: Ibz
    internal lateinit var form: GramForm

    /** Parses the block and materialises its form outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        config = LllConfig(ParameterSet.valueOf(parameterSet))
        dk = operand(capacity, config.diagonalBits - 1, config.diagonalBits)
        dk1 = operand(capacity, config.diagonalBits / 2, config.diagonalBits)
        mu =
            operand(capacity, config.precisionBits - 2, config.precisionBits + config.transformBits)
        form = config.materialiseBlock(dk, dk1, mu, config.precisionBits)
    }

    /** `quat_lll_materialise_block`: the truncated block form of the LDL state. */
    @Benchmark
    public open fun materialiseBlock(): Any =
        config.materialiseBlock(dk, dk1, mu, config.precisionBits)

    /** `quat_lll_gram_lehmer` with the outer passes of the first block-reduction round. */
    @Benchmark
    public open fun lagrangeGauss(): Any = config.gramLehmer(form, config.outerIterations(0))
}

private const val HEX_RADIX = 16

private fun operand(capacity: Int, bits: Int, bound: Int): Ibz =
    requireNotNull(Ibz.parse(capacity, ibzBenchmarkOperand(bits, false), HEX_RADIX))
        .withBound(bound)
