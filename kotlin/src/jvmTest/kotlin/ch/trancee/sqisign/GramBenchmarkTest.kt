// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.GramBenchmark
import ch.trancee.sqisign.quaternion.GramForm
import ch.trancee.sqisign.quaternion.GramReduction
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GramBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = GramBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val precision = benchmark.config.precisionBits
            val (dk, dk1, mu) = listOf(benchmark.dk, benchmark.dk1, benchmark.mu).map(::big)

            val form = assertIs<GramForm>(benchmark.materialiseBlock())
            val reduction = assertIs<GramReduction>(benchmark.lagrangeGauss())

            val label = parameterSet
            val offDiagonal = (mu * dk).shiftRight(precision)
            val diagonal = dk1 + (mu * offDiagonal).shiftRight(precision)
            val scale = form.scale
            assertEquals(benchmark.config.gramBits - 1, (dk + diagonal).bitLength() - scale, label)
            assertEquals(dk.shiftRight(scale), big(form.a), label)
            assertEquals(offDiagonal.shiftRight(scale), big(form.b), label)
            assertEquals(diagonal.shiftRight(scale), big(form.c), label)
            val (a, b, c) = listOf(form.a, form.b, form.c).map(::big)
            val u = reduction.transform.map(::big)
            val (u00, u01) = u[0] to u[1]
            val (u10, u11) = u[2] to u[3]
            assertEquals(BigInteger.ONE, (u00 * u11 - u01 * u10).abs(), label)
            val reduced = reduction.form
            assertEquals(u00 * u00 * a + TWO * u00 * u10 * b + u10 * u10 * c, big(reduced.a), label)
            assertEquals(
                u00 * u01 * a + (u00 * u11 + u01 * u10) * b + u10 * u11 * c,
                big(reduced.b),
                label,
            )
            assertEquals(u01 * u01 * a + TWO * u01 * u11 * b + u11 * u11 * c, big(reduced.c), label)
            assertTrue(
                big(reduced.b).abs() * TWO <= big(reduced.a) && big(reduced.a) <= big(reduced.c),
                label,
            )
        }
    }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        val TWO: BigInteger = BigInteger.TWO
    }
}
