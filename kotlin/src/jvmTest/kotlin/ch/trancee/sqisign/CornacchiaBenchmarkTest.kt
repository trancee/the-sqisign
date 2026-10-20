// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.CornacchiaBenchmark
import ch.trancee.sqisign.benchmarks.cornacchiaPrimeBits
import ch.trancee.sqisign.benchmarks.ibzBenchmarkCapacity
import ch.trancee.sqisign.quaternion.TwoSquares
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CornacchiaBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = CornacchiaBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val p = big(benchmark.prime)
            val root = big(benchmark.root)

            val sorted = assertIs<TwoSquares>(benchmark.twoSquares())
            val reduced = assertIs<TwoSquares>(benchmark.latticeReduction())

            val label = parameterSet
            assertEquals(cornacchiaPrimeBits(ibzBenchmarkCapacity(parameterSet)), p.bitLength())
            assertTrue(p.isProbablePrime(PRIME_CERTAINTY) && p.testBit(0) && !p.testBit(1), label)
            assertEquals(p - BigInteger.ONE, root * root % p, label)
            val (x, y) = big(sorted.x) to big(sorted.y)
            assertTrue(x <= y && x * x + y * y == p, label)
            val (u, v) = big(reduced.x) to big(reduced.y)
            assertEquals(p, u * u + v * v, label)
            val congruent =
                (u - root * v).mod(p).signum() == 0 || (u + root * v).mod(p).signum() == 0
            assertTrue(congruent, label)
        }
    }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        const val PRIME_CERTAINTY = 64
    }
}
