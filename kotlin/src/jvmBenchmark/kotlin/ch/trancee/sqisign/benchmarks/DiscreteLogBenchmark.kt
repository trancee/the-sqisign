// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.ec.PowerOfTwoDiscreteLog
import ch.trancee.sqisign.ec.TorsionScalar
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

/** Variable-time discrete logarithms of synthetic public roots. */
@State(Scope.Benchmark)
public open class DiscreteLogBenchmark {
    /** Chooses the existing concrete extension field outside timing. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** The smallest or full parameter torsion exponent. */
    @Param("TWO_TORSION", "FULL_TORSION") public var torsionShape: String = "TWO_TORSION"

    /** Logarithm zero or the coefficient with every torsion bit set. */
    @Param("ZERO", "MAXIMUM") public var valueShape: String = "ZERO"

    private lateinit var workload: DiscreteLogWorkload<*>

    /** Decodes a public root and prepares reusable powers, but does not solve a logarithm. */
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
                ParameterSet.NIST_I -> DiscreteLogWorkload(Fp2P324, exponent, valueShape)
                ParameterSet.NIST_III -> DiscreteLogWorkload(Fp2P500, exponent, valueShape)
                ParameterSet.NIST_V -> DiscreteLogWorkload(Fp2P664, exponent, valueShape)
            }
    }

    /** Includes balanced solving, membership checks and canonical scalar assembly. */
    @Benchmark public open fun solve(): Any = workload.solve()

    /** Additionally includes a new primitive-order check and inverse-power preparation. */
    @Benchmark public open fun prepareAndSolve(): Any = workload.prepareAndSolve()
}

private class DiscreteLogWorkload<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val exponent: Int,
    valueShape: String,
) {
    private val generator =
        if (exponent == 1) field.one().negate()
        else field.fromLittleEndian(discreteLogGenerator(field.parameters).hexToByteArray())
    private val value =
        when (valueShape) {
            "ZERO" -> field.one()
            "MAXIMUM" -> field.conjugate(generator)
            else -> throw IllegalArgumentException("Unknown public logarithm shape: $valueShape")
        }
    private val solver = PowerOfTwoDiscreteLog.prepare(field, exponent, generator)

    fun solve(): TorsionScalar = solver.solve(value)

    fun prepareAndSolve(): TorsionScalar =
        PowerOfTwoDiscreteLog.prepare(field, exponent, generator).solve(value)
}
