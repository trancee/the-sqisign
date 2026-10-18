// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.BasisHint
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time canonical basis operations on independently verified public maximal curves. */
@State(Scope.Benchmark)
public open class CanonicalBasisBenchmark {
    /** Existing concrete field representation, selected outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** Both branches of the specified canonical search. */
    @Param("SQUARE", "NONSQUARE") public var coefficientShape: String = "SQUARE"

    /** Exceptional order two or complete parameter torsion. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var workload: CanonicalBasisWorkload<*>

    /** Decodes the curve and published hint, but does not generate or validate a basis. */
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
        val input =
            canonicalBasisInputs(parameter)[
                when (coefficientShape) {
                    "SQUARE" -> 0
                    "NONSQUARE" -> 1
                    else ->
                        throw IllegalArgumentException(
                            "Unknown public coefficient shape: $coefficientShape",
                        )
                }]
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> CanonicalBasisWorkload(Fp2P324, input, exponent)
                ParameterSet.NIST_III -> CanonicalBasisWorkload(Fp2P500, input, exponent)
                ParameterSet.NIST_V -> CanonicalBasisWorkload(Fp2P664, input, exponent)
            }
    }

    /** Includes the complete search, cofactor clearing, deterministic difference and validation. */
    @Benchmark public open fun canonical(): Any = workload.canonical()

    /** Includes cofactor clearing, exact root choice and every mathematical basis check. */
    @Benchmark public open fun fromHint(): Any = workload.fromHint()

    /** Aligns full-order output to order 256; order-two cases have no smaller positive target. */
    @Benchmark
    public open fun canonicalForReconstruction(): Any = workload.canonicalForReconstruction()
}

private class CanonicalBasisWorkload<Element : QuadraticFieldElement<Element>>(
    field: QuadraticField<Element>,
    input: String,
    private val exponent: Int,
) {
    private val coefficient = field.fromLittleEndian(input.substringBefore(':').hexToByteArray())
    private val curve = MontgomeryCurve.fromCoefficients(field, coefficient, field.one())
    private val hint = BasisHint.fromEncoded(input.substringAfter(':').toInt().toByte())
    private val reconstructionExponent = if (exponent == 1) 0 else Byte.SIZE_BITS

    fun canonical(): Pair<TorsionBasis<Element>, BasisHint> =
        TorsionBasis.canonical(curve, exponent)

    fun fromHint(): TorsionBasis<Element> = TorsionBasis.fromHint(curve, exponent, hint)

    fun canonicalForReconstruction(): Pair<TorsionBasis<Element>, BasisHint> =
        TorsionBasis.canonical(curve, exponent, reconstructionExponent)
}
