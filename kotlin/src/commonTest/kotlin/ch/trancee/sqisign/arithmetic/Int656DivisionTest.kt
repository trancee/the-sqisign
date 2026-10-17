// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.signedWideDivisionVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int656DivisionTest {
    @Test fun wideSignedDivisionVectorBatch0() = assertDivisionBatch(0)

    @Test fun wideSignedDivisionVectorBatch1() = assertDivisionBatch(1)

    @Test fun wideSignedDivisionVectorBatch2() = assertDivisionBatch(2)

    @Test fun wideSignedDivisionVectorBatch3() = assertDivisionBatch(3)

    private fun assertDivisionBatch(batch: Int) {
        for (index in
            batch until signedWideDivisionVectors.size step SIGNED_WIDE_DIVISION_BATCHES) {
            val fields = signedWideDivisionVectors[index].split(':')
            if (fields[2] == "zero_divisor" || fields[2] == "overflow") continue
            val dividendBytes = fields[0].hexToByteArray()
            val divisorBytes = fields[1].hexToByteArray()
            val dividend = Int656.fromLittleEndian(dividendBytes)
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
            assertContentEquals(dividendBytes, dividend.toLittleEndian(), "Dividend case $index")
            assertContentEquals(divisorBytes, divisor.toLittleEndian(), "Divisor case $index")
        }
    }

    @Test
    fun explicitFailureCasesRejectZeroDivisorsAndWideMinimumOverflow() {
        for ((index, record) in signedWideDivisionVectors.withIndex()) {
            val fields = record.split(':')
            if (fields[2] != "zero_divisor" && fields[2] != "overflow") continue
            val dividendBytes = fields[0].hexToByteArray()
            val divisorBytes = fields[1].hexToByteArray()
            val dividend = Int656.fromLittleEndian(dividendBytes)
            val divisor = Int328.fromLittleEndian(divisorBytes)

            if (fields[2] == "zero_divisor") {
                val error =
                    assertFailsWith<IllegalArgumentException> {
                        dividend.divideAndRemainder(divisor)
                    }
                assertEquals(
                    "Signed division requires a nonzero divisor",
                    error.message,
                    "Case $index",
                )
            } else {
                val error =
                    assertFailsWith<ArithmeticException> { dividend.divideAndRemainder(divisor) }
                assertEquals("Signed 656-bit division overflow", error.message, "Case $index")
            }
            assertContentEquals(dividendBytes, dividend.toLittleEndian(), "Dividend case $index")
            assertContentEquals(divisorBytes, divisor.toLittleEndian(), "Divisor case $index")
        }
    }

    @Test
    fun wideMinimumDividedByOneProducesTheWideMinimumQuotient() {
        val dividendBytes =
            ByteArray(Int656.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val dividend = Int656.fromLittleEndian(dividendBytes)
        val divisor = Int328.fromLittleEndian(ByteArray(Int328.ENCODED_BYTES).also { it[0] = 1 })

        val division = dividend.divideAndRemainder(divisor)

        assertContentEquals(
            ByteArray(Int656.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE },
            division.quotient.toLittleEndian(),
        )
        assertContentEquals(ByteArray(Int328.ENCODED_BYTES), division.remainder.toLittleEndian())
    }

    @Test
    fun aSmallWideDividendAndLargerNarrowDivisorReturnTheOriginalRemainder() {
        val dividendBytes = ByteArray(Int656.ENCODED_BYTES).also { it[0] = 7 }
        val divisorBytes =
            ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val dividend = Int656.fromLittleEndian(dividendBytes)
        val divisor = Int328.fromLittleEndian(divisorBytes)

        val division = dividend.divideAndRemainder(divisor)

        assertContentEquals(ByteArray(Int656.ENCODED_BYTES), division.quotient.toLittleEndian())
        assertContentEquals(
            dividendBytes.copyOf(Int328.ENCODED_BYTES),
            division.remainder.toLittleEndian(),
        )
    }
}

private const val SIGNED_WIDE_DIVISION_BATCHES = 4
