// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
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

/** Variable-time canonical E0 construction and complete validation on synthetic public curves. */
@State(Scope.Benchmark)
public open class TorsionBasisBenchmark {
    /** The existing concrete field representation, selected outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** Order two or the complete parameter torsion subgroup. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    private lateinit var workload: E0BasisWorkload<*>

    /** Prepares only the curve and requested exponent; no basis is precomputed. */
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
                ParameterSet.NIST_I -> E0BasisWorkload(Fp2P324, exponent)
                ParameterSet.NIST_III -> E0BasisWorkload(Fp2P500, exponent)
                ParameterSet.NIST_V -> E0BasisWorkload(Fp2P664, exponent)
            }
    }

    /**
     * Includes coordinate decoding, order reduction, reference difference and every basis check.
     */
    @Benchmark public open fun canonicalE0(): Any = workload.canonicalE0()
}

private class E0BasisWorkload<Element : QuadraticFieldElement<Element>>(
    field: QuadraticField<Element>,
    private val exponent: Int,
) {
    private val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())

    fun canonicalE0(): TorsionBasis<Element> = TorsionBasis.canonicalE0(curve, exponent)
}
