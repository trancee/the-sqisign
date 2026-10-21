// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
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

private const val DOUBLED_POINT_NUMERATOR = 9
private const val DOUBLED_POINT_DENOMINATOR = 40
private const val REFERENCE_ROOT_REAL_MAGNITUDE = 5
private const val REFERENCE_ROOT_IMAGINARY = 12

/** Variable-time x-only operations on synthetic public points of A=0, at every level. */
@State(Scope.Benchmark)
public open class MontgomeryBenchmark {
    /** Concrete existing extension-field representation; selected outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** The same public point with a short or dense full parameter-width scalar. */
    @Param("SHORT", "FULL_WIDTH") public var scalarShape: String = "SHORT"

    private lateinit var workload: MontgomeryWorkload<*>

    /** Constructs typed curves, coordinates, encodings and root inputs outside timing. */
    @Setup
    public fun prepare() {
        val parameter = ParameterSet.valueOf(parameterSet)
        val bytes =
            when (scalarShape) {
                "SHORT" -> ByteArray(parameter.fieldBytes).also { it[0] = 17 }
                "FULL_WIDTH" -> ByteArray(parameter.fieldBytes) { 0xff.toByte() }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public curve scalar shape: $scalarShape",
                    )
            }
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> MontgomeryWorkload(Fp2P324, bytes)
                ParameterSet.NIST_III -> MontgomeryWorkload(Fp2P500, bytes)
                ParameterSet.NIST_V -> MontgomeryWorkload(Fp2P664, bytes)
            }
    }

    /** Includes inversion and canonical field result allocation. */
    @Benchmark public open fun coefficient(): Any = workload.coefficient()

    /** Retains both projective coordinates without affine normalization. */
    @Benchmark public open fun doublePoint(): Any = workload.doublePoint()

    /** Adds P and [2]P with the known difference -P, including result allocation. */
    @Benchmark public open fun differentialAdd(): Any = workload.differentialAdd()

    /** Complete encoded-scalar ladder and all intermediate/result allocations. */
    @Benchmark public open fun multiply(): Any = workload.multiply()

    /** Complete low-to-high three-point ladder, without affine normalization. */
    @Benchmark public open fun threePointMultiply(): Any = workload.threePointMultiply()

    /** Includes both scalar recodings and complete projective evaluation. */
    @Benchmark public open fun biscalarMultiply(): Any = workload.biscalarMultiply()

    /** Includes biquadratics, projective normalization and the reference-sign root. */
    @Benchmark public open fun deterministicDifference(): Any = workload.deterministicDifference()

    /** Includes both biquadratics and common-root elimination, without affine normalization. */
    @Benchmark public open fun sharedDifference(): Any = workload.sharedDifference()

    /** Verified specification-sign root, not the canonical smaller root. */
    @Benchmark public open fun referenceSquareRoot(): Any? = workload.referenceSquareRoot()
}

private class MontgomeryWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val scalar: ByteArray,
) {
    private val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
    private val point = MontgomeryPoint.fromCoordinates(field, element(2), field.one())
    private val doubled =
        MontgomeryPoint.fromCoordinates(
            field,
            element(DOUBLED_POINT_NUMERATOR),
            element(DOUBLED_POINT_DENOMINATOR),
        )
    private val tripled = MontgomeryPoint.differentialAdd(point, doubled, point)
    private val referenceRootInput =
        element(REFERENCE_ROOT_REAL_MAGNITUDE).negate().add(element(0, REFERENCE_ROOT_IMAGINARY))

    fun coefficient(): Element = curve.montgomeryCoefficient()

    fun doublePoint(): MontgomeryPoint<Element> = curve.double(point)

    fun differentialAdd(): MontgomeryPoint<Element> =
        MontgomeryPoint.differentialAdd(point, doubled, point)

    fun multiply(): MontgomeryPoint<Element> = curve.multiply(point, scalar)

    fun threePointMultiply(): MontgomeryPoint<Element> =
        curve.threePointMultiply(point, doubled, point, scalar)

    fun biscalarMultiply(): MontgomeryPoint<Element> =
        curve.biscalarMultiply(point, doubled, point, scalar, scalar)

    fun deterministicDifference(): MontgomeryPoint<Element> =
        curve.deterministicDifference(point, doubled)

    fun sharedDifference(): MontgomeryPoint<Element> =
        curve.sharedDifference(point, doubled, doubled, tripled)

    fun referenceSquareRoot(): Element? = field.squareRootWithReferenceSign(referenceRootInput)

    private fun element(real: Int, imaginary: Int = 0): Element =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also {
                it[0] = real.toByte()
                it[field.parameters.fieldBytes] = imaginary.toByte()
            },
        )
}
