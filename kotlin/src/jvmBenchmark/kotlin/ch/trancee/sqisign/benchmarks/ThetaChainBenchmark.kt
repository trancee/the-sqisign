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
import ch.trancee.sqisign.hd.CoupleCurve
import ch.trancee.sqisign.hd.CouplePoint
import ch.trancee.sqisign.hd.ThetaChainCodomain
import ch.trancee.sqisign.hd.ThetaChainKernel
import ch.trancee.sqisign.hd.ThetaChainMode
import ch.trancee.sqisign.hd.ThetaChainRejected
import ch.trancee.sqisign.hd.thetaChain
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time (2,2)-isogeny theta chains captured from public KAT signature verification. */
@State(Scope.Benchmark)
public open class ThetaChainBenchmark {
    /** Chooses the existing concrete field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    private lateinit var workload: ThetaChainWorkload<*>

    /** Decodes the captured curves and kernel points without validating them. */
    @Setup
    public fun prepare() {
        val parameter = ParameterSet.valueOf(parameterSet)
        workload =
            when (parameter) {
                ParameterSet.NIST_I -> ThetaChainWorkload(Fp2P324)
                ParameterSet.NIST_III -> ThetaChainWorkload(Fp2P500)
                ParameterSet.NIST_V -> ThetaChainWorkload(Fp2P664)
            }
    }

    /** Includes gluing, every generic step, the verification checks and the first-factor split. */
    @Benchmark public open fun verify(): Any = workload.verify()
}

private class ThetaChainWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
) {
    private val length: Int
    private val curves: CoupleCurve<Element>
    private val kernel: ThetaChainKernel<Element>

    init {
        val (chainLength, encoded) = thetaChainInputs(field.parameters)
        val values = encoded.map { field.fromLittleEndian(it.hexToByteArray()) }
        length = chainLength
        val (first, second) =
            values.take(2).map { MontgomeryCurve.fromCoefficients(field, it, field.one()) }
        val (t1, t2) =
            values.drop(2).chunked(2) { (left, right) ->
                CouplePoint(
                    MontgomeryPoint.fromCoordinates(field, left, field.one()),
                    MontgomeryPoint.fromCoordinates(field, right, field.one()),
                )
            }
        curves = CoupleCurve(first, second)
        kernel = ThetaChainKernel(t1, t2)
    }

    fun verify(): ByteArray =
        when (
            val outcome =
                thetaChain(
                    length,
                    curves,
                    kernel,
                    emptyList(),
                    ThetaChainMode.VERIFY,
                )
        ) {
            is ThetaChainCodomain ->
                checkNotNull(outcome.first).curve.montgomeryCoefficient().toLittleEndian()
            is ThetaChainRejected -> error("Captured KAT chain was rejected: ${outcome.failure}")
        }
}
