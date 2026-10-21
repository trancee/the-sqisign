// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.quaternion.QuaternionAlgebra
import ch.trancee.sqisign.quaternion.QuaternionElement
import ch.trancee.sqisign.quaternion.quadraticForm
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time quaternion algebra operations of `B_{p,inf}` on deterministic elements with
 * `p`-sized coordinates, and a `p^2`-sized 4x4 Gram form evaluated at a 64-bit vector.
 */
@State(Scope.Benchmark)
public open class QuaternionAlgebraBenchmark {
    /** Selects NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var algebra: QuaternionAlgebra
    internal lateinit var prime: Ibz
    internal lateinit var left: QuaternionElement
    internal lateinit var right: QuaternionElement
    internal lateinit var gram: List<List<Ibz>>
    internal lateinit var vector: List<Ibz>

    /** Parses the operands outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        val values =
            algebraInputs(ParameterSet.valueOf(parameterSet)).map {
                requireNotNull(Ibz.parse(capacity, it, HEX_RADIX))
            }
        prime = values[0]
        algebra = QuaternionAlgebra(prime)
        left = QuaternionElement(values[1], values.subList(2, LEFT_END))
        right = QuaternionElement(values[LEFT_END], values.subList(LEFT_END + 1, RIGHT_END))
        gram = values.subList(RIGHT_END, GRAM_END).chunked(DIMENSION)
        vector = values.subList(GRAM_END, values.size)
    }

    /** `quat_alg_mul`. */
    @Benchmark public open fun multiply(): Any = algebra.multiply(left, right)

    /** `quat_alg_norm`: the reduced norm in lowest terms. */
    @Benchmark public open fun norm(): Any = algebra.norm(left)

    /** `quat_qf_eval`: the hot inner evaluation of lattice sampling. */
    @Benchmark public open fun quadraticForm(): Any = gram.quadraticForm(vector)
}

private const val HEX_RADIX = 16
private const val DIMENSION = 4
private const val LEFT_END = 6
private const val RIGHT_END = 11
private const val GRAM_END = 27
