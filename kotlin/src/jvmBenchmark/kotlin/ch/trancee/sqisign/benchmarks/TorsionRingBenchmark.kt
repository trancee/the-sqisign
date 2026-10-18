// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.TorsionMatrix
import ch.trancee.sqisign.ec.TorsionScalar
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

private const val SMALL_COEFFICIENT = 3

/** Variable-time bounded-ring operations on synthetic public coefficients. */
@State(Scope.Benchmark)
public open class TorsionRingBenchmark {
    /** Selects the full torsion width outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** The smallest or full supported torsion ring. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var maximum: TorsionScalar
    private lateinit var three: TorsionScalar
    private lateinit var matrix: TorsionMatrix

    /** Decodes coefficients outside timing, without precomputing operation results. */
    @Setup
    public fun prepare() {
        val parameter = ParameterSet.valueOf(parameterSet)
        val exponent =
            when (torsionShape) {
                "TWO_TORSION" -> 1
                "FULL_TORSION" -> parameter.torsionExponent
                else ->
                    throw IllegalArgumentException("Unknown public torsion shape: $torsionShape")
            }
        val width = (exponent + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
        val encoded = ByteArray(width) { 0xff.toByte() }
        encoded[encoded.lastIndex] = ((1 shl ((exponent - 1) % Byte.SIZE_BITS + 1)) - 1).toByte()
        maximum = TorsionScalar.fromLittleEndian(exponent, encoded)
        three =
            TorsionScalar.fromLittleEndian(
                exponent,
                ByteArray(width).also {
                    it[0] =
                        (SMALL_COEFFICIENT and ((1 shl minOf(exponent, Byte.SIZE_BITS)) - 1))
                            .toByte()
                },
            )
        val one = maximum.negate()
        val two = one.add(one)
        val negativeThree = three.negate()
        matrix = TorsionMatrix.of(negativeThree, two.add(two), two, negativeThree)
    }

    /** Retains the complete canonical ring product. */
    @Benchmark public open fun multiply(): Any = maximum.multiply(three)

    /** Retains addition reduced at the torsion bit boundary. */
    @Benchmark public open fun add(): Any = maximum.add(three)

    /** Retains subtraction in the declared torsion ring. */
    @Benchmark public open fun subtract(): Any = maximum.subtract(three)

    /** Includes every Newton step and canonical output construction. */
    @Benchmark public open fun inverse(): Any = maximum.inverse()

    /** Includes global sign selection and construction when negation is required. */
    @Benchmark public open fun normalizeMatrix(): Any = matrix.normalized()

    /** Includes determinant validation, its inverse and every adjugate entry. */
    @Benchmark public open fun invertMatrix(): Any = matrix.inverse()
}
