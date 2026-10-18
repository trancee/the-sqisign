// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.BasisCorrespondence
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.setChangeOfBasisMatrix
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

/** Complete variable-time algorithm 3.9 on independently generated public bases. */
@State(Scope.Benchmark)
public open class BasisCorrespondenceBenchmark {
    /** Selects the existing concrete field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** An E0 binary correspondence or a full E0-to-nonzero correspondence. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var workload: BasisCorrespondenceWorkload<*>

    /** Decodes curves and validates supplied bases, without generating canonical references. */
    @Setup
    public fun prepare() {
        val parameter = ParameterSet.valueOf(parameterSet)
        val full =
            when (torsionShape) {
                "TWO_TORSION" -> false
                "FULL_TORSION" -> true
                else ->
                    throw IllegalArgumentException("Unknown public torsion shape: $torsionShape")
            }
        val inputs = basisCorrespondenceInputs(parameter, full)
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> BasisCorrespondenceWorkload(Fp2P324, inputs)
                ParameterSet.NIST_III -> BasisCorrespondenceWorkload(Fp2P500, inputs)
                ParameterSet.NIST_V -> BasisCorrespondenceWorkload(Fp2P664, inputs)
            }
    }

    /** Includes both canonical searches, changes, inverse action, normalization and hints. */
    @Benchmark public open fun setChangeOfBasisMatrix(): Any = workload.correspondence()
}

private class BasisCorrespondenceWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    inputs: List<String>,
) {
    private val exponent = inputs[0].toInt()
    private val firstCurve = curve(inputs[1])
    private val secondCurve = curve(inputs[SECOND_CURVE_OFFSET])
    private val firstBasis = basis(firstCurve, inputs.subList(2, SECOND_CURVE_OFFSET))
    private val secondBasis = basis(secondCurve, inputs.drop(SECOND_CURVE_OFFSET + 1))

    fun correspondence(): BasisCorrespondence =
        setChangeOfBasisMatrix(firstCurve, firstBasis, secondCurve, secondBasis)

    private fun curve(encoded: String): MontgomeryCurve<Element> =
        MontgomeryCurve.fromCoefficients(
            field,
            field.fromLittleEndian(encoded.hexToByteArray()),
            field.one(),
        )

    private fun basis(
        curve: MontgomeryCurve<Element>,
        encodings: List<String>,
    ): TorsionBasis<Element> {
        val points = encodings.map {
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(it.hexToByteArray()),
                field.one(),
            )
        }
        return TorsionBasis.checked(curve, exponent, points[0], points[1], points[2])
    }
}

private const val SECOND_CURVE_OFFSET = 5
