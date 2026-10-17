// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.extendedGcdVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int328ExtendedGcdTest {
    @Test
    fun coefficientNarrowingAcceptsSignedBoundariesAndRejectsBothOverflows() {
        val minimumBytes =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximumBytes =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val minimumWideBytes =
            ByteArray(Int656.ENCODED_BYTES) { -1 }
                .also {
                    minimumBytes.copyInto(it)
                }
        val maximumWideBytes =
            ByteArray(Int656.ENCODED_BYTES).also {
                maximumBytes.copyInto(it)
            }
        val overflowBytes =
            ByteArray(Int656.ENCODED_BYTES).also { it[Int328.ENCODED_BYTES - 1] = Byte.MIN_VALUE }
        val negativeOverflowBytes =
            ByteArray(Int656.ENCODED_BYTES) { -1 }
                .also {
                    it[Int328.ENCODED_BYTES - 1] = Byte.MAX_VALUE
                }
        val minimum = Int656.fromLittleEndian(minimumWideBytes)
        val maximum = Int656.fromLittleEndian(maximumWideBytes)
        val positiveOverflow = Int656.fromLittleEndian(overflowBytes)
        val negativeOverflow = Int656.fromLittleEndian(negativeOverflowBytes)

        assertContentEquals(
            minimumBytes,
            Int328.narrowCoefficient(minimum).toLittleEndian(),
        )
        assertContentEquals(
            maximumBytes,
            Int328.narrowCoefficient(maximum).toLittleEndian(),
        )
        val error =
            assertFailsWith<ArithmeticException> { Int328.narrowCoefficient(positiveOverflow) }
        assertEquals("Extended-GCD coefficient exceeds signed 328-bit range", error.message)
        val negativeError =
            assertFailsWith<ArithmeticException> { Int328.narrowCoefficient(negativeOverflow) }
        assertEquals(
            "Extended-GCD coefficient exceeds signed 328-bit range",
            negativeError.message,
        )
    }

    @Test
    fun bezoutCoefficientsReconstructTheGreatestCommonDivisor() {
        val left = signed(240)
        val right = signed(46)

        val output = left.extendedGcd(right)

        assertContentEquals(unsigned(2).toLittleEndian(), output.gcd.toLittleEndian())
        assertBezoutIdentity(
            left,
            right,
            output.gcd,
            output.leftCoefficient,
            output.rightCoefficient,
        )
    }

    @Test
    fun signsAreAppliedToTheBezoutCoefficients() {
        for ((leftValue, rightValue) in listOf(-240 to 46, 240 to -46, -240 to -46)) {
            val left = signed(leftValue)
            val right = signed(rightValue)
            val output = left.extendedGcd(right)

            assertContentEquals(unsigned(2).toLittleEndian(), output.gcd.toLittleEndian())
            assertBezoutIdentity(
                left,
                right,
                output.gcd,
                output.leftCoefficient,
                output.rightCoefficient,
            )
        }
    }

    @Test
    fun signedExtendedGcdMatchesIndependentVectorsAndBezoutIdentities() {
        for ((index, record) in extendedGcdVectors.withIndex()) {
            val fields = record.split(':')
            val left = Int328.fromLittleEndian(fields[0].hexToByteArray())
            val right = Int328.fromLittleEndian(fields[1].hexToByteArray())
            val expectedGcdBytes = fields[2].hexToByteArray()
            val expectedGcd = UInt328.fromLittleEndian(expectedGcdBytes)
            val oracleLeftCoefficient = Int328.fromLittleEndian(fields[3].hexToByteArray())
            val oracleRightCoefficient = Int328.fromLittleEndian(fields[4].hexToByteArray())

            val output = left.extendedGcd(right)

            assertContentEquals(expectedGcdBytes, output.gcd.toLittleEndian(), "GCD case $index")
            assertBezoutIdentity(
                left,
                right,
                expectedGcd,
                oracleLeftCoefficient,
                oracleRightCoefficient,
            )
            assertBezoutIdentity(
                left,
                right,
                expectedGcd,
                output.leftCoefficient,
                output.rightCoefficient,
            )
        }
    }

    @Test
    fun twoZeroOperandsProduceZeroGcdAndCoefficients() {
        val zero = signed(0)

        val output = zero.extendedGcd(zero)

        assertContentEquals(unsigned(0).toLittleEndian(), output.gcd.toLittleEndian())
        assertContentEquals(zero.toLittleEndian(), output.leftCoefficient.toLittleEndian())
        assertContentEquals(zero.toLittleEndian(), output.rightCoefficient.toLittleEndian())
    }

    @Test
    fun oneZeroOperandUsesTheNonzeroOperandSignAsItsCoefficient() {
        for ((leftValue, rightValue, coefficients) in
            listOf(
                Triple(-240, 0, -1 to 0),
                Triple(240, 0, 1 to 0),
                Triple(0, -46, 0 to -1),
                Triple(0, 46, 0 to 1),
            )) {
            val left = signed(leftValue)
            val right = signed(rightValue)

            val output = left.extendedGcd(right)

            assertContentEquals(
                unsigned(maxOf(kotlin.math.abs(leftValue), kotlin.math.abs(rightValue)))
                    .toLittleEndian(),
                output.gcd.toLittleEndian(),
            )
            assertContentEquals(
                signed(coefficients.first).toLittleEndian(),
                output.leftCoefficient.toLittleEndian(),
            )
            assertContentEquals(
                signed(coefficients.second).toLittleEndian(),
                output.rightCoefficient.toLittleEndian(),
            )
        }
    }

    @Test
    fun minimumAndZeroKeepTheNonnegativeGcdInUnsignedWidth() {
        val minimumBytes =
            ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val minimum = Int328.fromLittleEndian(minimumBytes)
        val zero = signed(0)
        val expectedGcd =
            ByteArray(UInt328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }

        val output = minimum.extendedGcd(zero)

        assertContentEquals(expectedGcd, output.gcd.toLittleEndian())
        assertContentEquals(signed(-1).toLittleEndian(), output.leftCoefficient.toLittleEndian())
        assertContentEquals(zero.toLittleEndian(), output.rightCoefficient.toLittleEndian())
    }
}

private fun assertBezoutIdentity(
    left: Int328,
    right: Int328,
    gcd: UInt328,
    leftCoefficient: Int328,
    rightCoefficient: Int328,
) {
    val expected = ByteArray(Int656.ENCODED_BYTES)
    gcd.toLittleEndian().copyInto(expected)
    val actual = left.multiply(leftCoefficient).add(right.multiply(rightCoefficient))

    assertContentEquals(expected, actual.toLittleEndian())
}

private fun signed(value: Int): Int328 =
    Int328.fromLittleEndian(
        ByteArray(Int328.ENCODED_BYTES) { if (value < 0) 0xff.toByte() else 0 }
            .also { it[0] = value.toByte() },
    )

private fun unsigned(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })
