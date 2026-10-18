// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.BasisChange
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.TorsionMatrix
import ch.trancee.sqisign.ec.TorsionScalar
import ch.trancee.sqisign.ec.repeatedDouble
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

/** Variable-time transformations of independently generated public bases. */
@State(Scope.Benchmark)
public open class BasisTransformBenchmark {
    /** Selects the existing concrete field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** Exceptional binary swap or a full-order dense matrix. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var workload: BasisTransformWorkload<*>

    /** Validates public inputs and prepares the shared reference lifts/root outside timing. */
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
        val inputs = basisTransformInputs(parameter, full)
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> BasisTransformWorkload(Fp2P324, inputs)
                ParameterSet.NIST_III -> BasisTransformWorkload(Fp2P500, inputs)
                ParameterSet.NIST_V -> BasisTransformWorkload(Fp2P664, inputs)
            }
    }

    /** Includes validation, all three x-only combinations and the owned differential basis. */
    @Benchmark public open fun applyMatrix(): Any = workload.applyMatrix()

    /** Includes complete cross-differences, checked pairings, logarithms and normalization. */
    @Benchmark public open fun between(): Any = workload.between()

    /** Also includes fresh reference lifts and the full pairing root. */
    @Benchmark public open fun prepareAndBetween(): Any = workload.prepareAndBetween()
}

private class BasisTransformWorkload<Element : QuadraticFieldElement<Element>>(
    field: QuadraticField<Element>,
    inputs: List<String>,
) {
    private val exponent = inputs[0].toInt()
    private val curve =
        MontgomeryCurve.fromCoefficients(
            field,
            field.fromLittleEndian(inputs[1].hexToByteArray()),
            field.one(),
        )
    private val fullPoints =
        inputs.subList(FULL_BASIS_OFFSET, TARGET_BASIS_OFFSET).map {
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(it.hexToByteArray()),
                field.one(),
            )
        }
    private val sourcePoints = fullPoints.map {
        repeatedDouble(curve, it, field.parameters.torsionExponent - exponent)
    }
    private val source =
        TorsionBasis.checked(curve, exponent, sourcePoints[0], sourcePoints[1], sourcePoints[2])
    private val fullBasis =
        TorsionBasis.checked(
            curve,
            field.parameters.torsionExponent,
            fullPoints[0],
            fullPoints[1],
            fullPoints[2],
        )
    private val targetPoints =
        inputs.subList(TARGET_BASIS_OFFSET, MATRIX_OFFSET).map {
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(it.hexToByteArray()),
                field.one(),
            )
        }
    private val target =
        TorsionBasis.checked(curve, exponent, targetPoints[0], targetPoints[1], targetPoints[2])
    private val change = BasisChange.prepare(curve, fullBasis)
    private val entries =
        inputs.drop(MATRIX_OFFSET).map {
            TorsionScalar.fromLittleEndian(exponent, it.hexToByteArray())
        }
    private val matrix = TorsionMatrix.of(entries[0], entries[1], entries[2], entries.last())

    fun applyMatrix(): TorsionBasis<Element> = source.applyMatrix(curve, matrix)

    fun between(): TorsionMatrix = change.between(target)

    fun prepareAndBetween(): TorsionMatrix = BasisChange.prepare(curve, fullBasis).between(target)
}

private const val FULL_BASIS_OFFSET = 2
private const val TARGET_BASIS_OFFSET = 5
private const val MATRIX_OFFSET = 8
