// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.signedDivisionVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int328DivisionVectorTest {
    @Test
    fun quotientAndRemainderMatchIndependentSignedDivisionVectors() {
        val zero = Int328.fromLittleEndian(ByteArray(Int328.ENCODED_BYTES))
        for ((index, record) in signedDivisionVectors.withIndex()) {
            val fields = record.split(':')
            if (fields[2] == "zero_divisor" || fields[2] == "overflow") continue
            val dividendBytes = fields[0].hexToByteArray()
            val divisorBytes = fields[1].hexToByteArray()
            val dividend = Int328.fromLittleEndian(dividendBytes)
            val divisor = Int328.fromLittleEndian(divisorBytes)

            val division = dividend.divideAndRemainder(divisor)

            assertContentEquals(
                fields[2].hexToByteArray(),
                division.quotient.toLittleEndian(),
                "Quotient case $index",
            )
            assertContentEquals(
                fields[3].hexToByteArray(),
                division.remainder.toLittleEndian(),
                "Remainder case $index",
            )
            if (division.remainder.compareTo(zero) != 0) {
                assertEquals(
                    dividend.compareTo(zero),
                    division.remainder.compareTo(zero),
                    "Remainder sign case $index",
                )
            }
            assertContentEquals(dividendBytes, dividend.toLittleEndian(), "Dividend case $index")
            assertContentEquals(divisorBytes, divisor.toLittleEndian(), "Divisor case $index")
        }
    }

    @Test
    fun invalidDivisionsRejectTheExplicitFixtureFailureCategory() {
        for ((index, record) in signedDivisionVectors.withIndex()) {
            val fields = record.split(':')
            val expected =
                when (fields[2]) {
                    "zero_divisor" -> "Signed division requires a nonzero divisor"
                    "overflow" -> "Signed 328-bit division overflow"
                    else -> continue
                }
            val dividendBytes = fields[0].hexToByteArray()
            val divisorBytes = fields[1].hexToByteArray()
            val dividend = Int328.fromLittleEndian(dividendBytes)
            val divisor = Int328.fromLittleEndian(divisorBytes)

            val error =
                if (fields[2] == "zero_divisor") {
                    assertFailsWith<IllegalArgumentException> {
                        dividend.divideAndRemainder(divisor)
                    }
                } else {
                    assertFailsWith<ArithmeticException> { dividend.divideAndRemainder(divisor) }
                }

            assertEquals(expected, error.message, "Failure case $index")
            assertContentEquals(dividendBytes, dividend.toLittleEndian(), "Dividend case $index")
            assertContentEquals(divisorBytes, divisor.toLittleEndian(), "Divisor case $index")
        }
    }
}
