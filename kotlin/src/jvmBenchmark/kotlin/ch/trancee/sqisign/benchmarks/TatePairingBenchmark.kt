// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.TatePairing
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

/** Variable-time Tate pairings of independently generated public points. */
@State(Scope.Benchmark)
public open class TatePairingBenchmark {
    /** Chooses the existing concrete field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** Order two or full order in the first slot; the second has full order. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var workload: TateWorkload<*>

    /** Decodes inputs and validates the maximality witness without evaluating the pairing. */
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
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> TateWorkload(Fp2P324, exponent)
                ParameterSet.NIST_III -> TateWorkload(Fp2P500, exponent)
                ParameterSet.NIST_V -> TateWorkload(Fp2P664, exponent)
            }
    }

    /** Includes checked operands, cubical evaluation, reduction and owned field encoding. */
    @Benchmark public open fun evaluate(): Any = workload.evaluate()

    /**
     * Additionally includes fresh witness binding and normalized cubical coefficient preparation.
     */
    @Benchmark public open fun prepareAndEvaluate(): Any = workload.prepareAndEvaluate()
}

private class TateWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val exponent: Int,
) {
    private val points =
        tatePairingInputs(field.parameters, exponent == field.parameters.torsionExponent).map {
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(it.hexToByteArray()),
                field.one(),
            )
        }
    private val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
    private val basis =
        TorsionBasis.checked(
            curve,
            field.parameters.torsionExponent,
            points[0],
            points[1],
            points[2],
        )
    private val pairing = TatePairing.prepare(curve, basis)

    fun evaluate(): ByteArray =
        pairing
            .evaluate(
                exponent,
                points[BASIS_POINT_COUNT],
                points[BASIS_POINT_COUNT + 1],
                points[BASIS_POINT_COUNT + 2],
            )
            .toLittleEndian()

    fun prepareAndEvaluate(): ByteArray =
        TatePairing.prepare(curve, basis)
            .evaluate(
                exponent,
                points[BASIS_POINT_COUNT],
                points[BASIS_POINT_COUNT + 1],
                points[BASIS_POINT_COUNT + 2],
            )
            .toLittleEndian()
}

private const val BASIS_POINT_COUNT = 3
