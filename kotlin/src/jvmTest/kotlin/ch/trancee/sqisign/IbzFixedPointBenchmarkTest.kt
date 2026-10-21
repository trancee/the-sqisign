// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzReciprocal
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.IbzFixedPointBenchmark
import ch.trancee.sqisign.benchmarks.fixedPointPrecision
import ch.trancee.sqisign.benchmarks.ibzBenchmarkOperand
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IbzFixedPointBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = IbzFixedPointBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val precision = fixedPointPrecision(parameterSet)
            val left = BigInteger(ibzBenchmarkOperand(precision, negative = false), 16)
            val right = BigInteger(ibzBenchmarkOperand(precision, negative = true), 16)

            val product = assertIs<Ibz>(benchmark.highProduct())
            val reciprocal = assertIs<IbzReciprocal>(benchmark.reciprocal())

            val label = parameterSet
            val floor = (left * right).shiftRight(precision)
            assertEquals(floor, big(product), label)
            assertEquals(precision, reciprocal.scale, label)
            val exact = BigInteger.ONE.shiftLeft(2 * precision) / left
            val error = (big(reciprocal.value) - exact).abs()
            assertTrue(error <= BigInteger.valueOf(NEWTON_TOLERANCE), "$label error $error")
        }
    }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        const val NEWTON_TOLERANCE = 4L
    }
}
