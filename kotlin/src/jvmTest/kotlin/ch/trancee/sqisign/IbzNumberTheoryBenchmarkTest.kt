// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzInverse
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.IbzNumberTheoryBenchmark
import ch.trancee.sqisign.benchmarks.ibzBenchmarkCapacity
import ch.trancee.sqisign.benchmarks.ibzBenchmarkOperand
import ch.trancee.sqisign.benchmarks.numberTheoryPrimeBits
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IbzNumberTheoryBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = IbzNumberTheoryBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val bits = numberTheoryPrimeBits(ibzBenchmarkCapacity(parameterSet))
            val prime =
                BigInteger(ibzBenchmarkOperand(bits, negative = false), 16).let { start ->
                    generateSequence(start.or(BigInteger.ONE)) { it + BigInteger.TWO }
                        .first { it.isProbablePrime(PRIME_CERTAINTY) }
                }
            val value = BigInteger(ibzBenchmarkOperand(bits - 1, negative = true), 16).mod(prime)

            val gcd = assertIs<Ibz>(benchmark.gcd())
            val inverse = assertIs<IbzInverse>(benchmark.inverseMod())
            val power = assertIs<Ibz>(benchmark.powMod())
            val probablePrime = assertIs<Boolean>(benchmark.probablePrime())

            val label = parameterSet
            assertEquals(prime, big(benchmark.prime), label)
            assertEquals(value, big(benchmark.value), label)
            assertEquals(BigInteger.ONE, big(gcd), label)
            assertTrue(inverse.exists, label)
            assertEquals(value.modInverse(prime), big(inverse.value), label)
            assertEquals(value.modPow(prime - BigInteger.TWO, prime), big(power), label)
            assertTrue(probablePrime, label)
        }
    }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        const val PRIME_CERTAINTY = 64
    }
}
