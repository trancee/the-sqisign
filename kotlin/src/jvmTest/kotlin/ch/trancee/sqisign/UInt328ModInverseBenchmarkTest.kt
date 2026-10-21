// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.benchmarks.UInt328ModInverseBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt328ModInverseBenchmarkTest {
    @Test
    fun modularInverseWorkloadsMatchIndependentArithmetic() {
        val one = BigInteger.ONE
        val powerOfTwo = one.shiftLeft(327)
        val maximum = powerOfTwo.shiftLeft(1) - one
        val publicValue =
            BigInteger(
                    1,
                    Shake256.digest(
                            "public UInt328 inverse benchmark".encodeToByteArray(),
                            UInt328.ENCODED_BYTES,
                        )
                        .reversedArray(),
                )
                .setBit(0)
        val fixtures =
            listOf(
                Triple("ONE", one, maximum),
                Triple("MAX_MINUS_ONE", maximum - one, maximum),
                Triple("EVEN_MODULUS", maximum, powerOfTwo),
                Triple("COMPOSITE", BigInteger.valueOf(5), BigInteger.valueOf(12)),
                Triple("FULL_WIDTH", publicValue, powerOfTwo),
            )
        for ((shape, value, modulus) in fixtures) {
            val benchmark = UInt328ModInverseBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = value.modInverse(modulus)

            val output = benchmark.modInverse()

            val inverse = assertIs<UInt328>(output)
            assertEquals(expected, BigInteger(1, inverse.toLittleEndian().reversedArray()), shape)
        }
    }

    @Test
    fun unknownModularInverseShapesAreRejected() {
        val benchmark = UInt328ModInverseBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public modular inverse operand shape: UNKNOWN", error.message)
    }
}
