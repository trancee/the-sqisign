// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.quaternion.QuaternionElement
import ch.trancee.sqisign.quaternion.QuaternionLattice
import ch.trancee.sqisign.quaternion.gaussianGcd
import ch.trancee.sqisign.quaternion.inverse2x2Mod
import ch.trancee.sqisign.quaternion.primitiveIn
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time `dim2.c` and `lattice.c` workloads: the gcd of `p`-sized Gaussian integers with a
 * common factor, the primitive coordinates of a `p`-sized element of `O0`, and a 2x2 inverse modulo
 * the `2^e` torsion order of the parameter set.
 */
@State(Scope.Benchmark)
public open class Dim2LatticeBenchmark {
    /** Selects NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var first: List<Ibz>
    internal lateinit var second: List<Ibz>
    internal lateinit var order: QuaternionLattice
    internal lateinit var orderBasis: List<List<Ibz>>
    internal lateinit var element: QuaternionElement
    internal lateinit var matrix: List<List<Ibz>>
    internal lateinit var modulus: Ibz

    /** Parses the operands outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        val texts = dim2LatticeInputs(ParameterSet.valueOf(parameterSet))
        val values = texts.dropLast(1).map { requireNotNull(Ibz.parse(capacity, it, HEX_RADIX)) }
        first = commonBound(values.subList(0, 2))
        second = commonBound(values.subList(2, GAUSSIAN_END))
        val two = Ibz.of(capacity, 2, 3)
        val one = Ibz.one(capacity)
        val zero = Ibz.zero(capacity)
        orderBasis =
            listOf(
                listOf(two, zero, zero, one),
                listOf(zero, two, one, zero),
                listOf(zero, zero, one, zero),
                listOf(zero, zero, zero, one),
            )
        order = QuaternionLattice(two, orderBasis)
        element = QuaternionElement(two, values.subList(GAUSSIAN_END, ELEMENT_END))
        matrix = values.subList(ELEMENT_END, values.size).chunked(2)
        modulus = one.shiftLeft(texts.last().toInt(HEX_RADIX))
    }

    /** `ibz_vec_2_gaussian_gcd`. */
    @Benchmark public open fun gaussianGcd(): Any = gaussianGcd(first, second)

    /** `quat_alg_make_primitive` in `O0`, as ideal generators and `id2iso` call it. */
    @Benchmark public open fun makePrimitive(): Any = element.primitiveIn(order)

    /** `ibz_mat_2x2_inv_mod` modulo `2^e`, as `id2iso` calls it. */
    @Benchmark public open fun inverse2x2Mod(): Any = matrix.inverse2x2Mod(modulus)

    private fun commonBound(pair: List<Ibz>): List<Ibz> {
        val bound = maxOf(pair[0].bitlen, pair[1].bitlen)
        return pair.map { it.withBound(bound) }
    }
}

private const val HEX_RADIX = 16
private const val GAUSSIAN_END = 4
private const val ELEMENT_END = 8
