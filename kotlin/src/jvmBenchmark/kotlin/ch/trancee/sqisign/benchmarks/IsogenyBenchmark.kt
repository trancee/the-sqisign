// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.FourIsogeny
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.normalize
import ch.trancee.sqisign.ec.twoIsogenyChain
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

/** Variable-time normalization, four-isogenies and security-length chains from E0. */
@State(Scope.Benchmark)
public open class IsogenyBenchmark {
    /** Chooses the existing concrete field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    private lateinit var workload: IsogenyWorkload<*>

    /** Decodes the generated public points without validating or evaluating them. */
    @Setup
    public fun prepare() {
        val parameter = ParameterSet.valueOf(parameterSet)
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> IsogenyWorkload(Fp2P324)
                ParameterSet.NIST_III -> IsogenyWorkload(Fp2P500)
                ParameterSet.NIST_V -> IsogenyWorkload(Fp2P664)
            }
    }

    /** Includes kernel validation, six candidates and canonical coefficient encoding. */
    @Benchmark public open fun normalize(): Any = workload.normalize()

    /** Includes kernel validation, codomain, one point image and both affine encodings. */
    @Benchmark public open fun fourIsogeny(): Any = workload.fourIsogeny()

    /** Includes every validated four-isogeny step and the final normalization. */
    @Benchmark public open fun chain(): Any = workload.chain()
}

private class IsogenyWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
) {
    private val points =
        isogenyInputs(field.parameters).map {
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(it.hexToByteArray()),
                field.one(),
            )
        }
    private val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())

    fun normalize(): ByteArray =
        curve.normalize(points[KERNEL]).montgomeryCoefficient().toLittleEndian()

    fun fourIsogeny(): ByteArray {
        val isogeny = FourIsogeny.from(curve, points[KERNEL])
        val image = checkNotNull(isogeny.evaluate(points[POINT]).affineX())
        return isogeny.codomain.montgomeryCoefficient().toLittleEndian() + image.toLittleEndian()
    }

    fun chain(): ByteArray =
        curve
            .twoIsogenyChain(points[SEED], field.parameters.securityBits)
            .montgomeryCoefficient()
            .toLittleEndian()
}

private const val SEED = 0
private const val KERNEL = 1
private const val POINT = 2
