// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.ParameterSet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TorsionScalarTest {
    @Test
    fun canonicalScalarDecodingPreservesAll664TorsionBits() {
        val encoded = ByteArray(83) { 0xff.toByte() }

        val scalar = TorsionScalar.fromLittleEndian(664, encoded)

        assertEquals(664, scalar.exponent)
        assertContentEquals(encoded, scalar.toLittleEndian())
    }

    @Test
    fun additionWrapsAtTheDeclaredBitModulusRatherThanTheByteBoundary() {
        val maximum = TorsionScalar.fromLittleEndian(9, "ff01".hexToByteArray())
        val one = TorsionScalar.fromLittleEndian(9, "0100".hexToByteArray())

        val sum = maximum.add(one)

        assertContentEquals("0000".hexToByteArray(), sum.toLittleEndian())
    }

    @Test
    fun subtractionBorrowsAcrossBytesAndWrapsModuloTheTorsionOrder() {
        val zero = TorsionScalar.fromLittleEndian(9, "0000".hexToByteArray())
        val one = TorsionScalar.fromLittleEndian(9, "0100".hexToByteArray())

        val difference = zero.subtract(one)

        assertContentEquals("ff01".hexToByteArray(), difference.toLittleEndian())
    }

    @Test
    fun multiplicationPropagatesCarriesBeforeReducingAtTheDeclaredModulus() {
        val maximum = TorsionScalar.fromLittleEndian(17, "ffff01".hexToByteArray())

        val product = maximum.multiply(maximum)

        assertContentEquals("010000".hexToByteArray(), product.toLittleEndian())
    }

    @Test
    fun negationReturnsTheCanonicalAdditiveInverse() {
        val one = TorsionScalar.fromLittleEndian(9, "0100".hexToByteArray())

        val opposite = one.negate()

        assertContentEquals("ff01".hexToByteArray(), opposite.toLittleEndian())
    }

    @Test
    fun oddResiduesHaveMultiplicativeInversesAtNonByteAlignedExponents() {
        val three = TorsionScalar.fromLittleEndian(17, "030000".hexToByteArray())

        val inverse = three.inverse()

        assertContentEquals("abaa00".hexToByteArray(), inverse.toLittleEndian())
    }

    @Test
    fun ladderExportZeroExtendsWithoutLosingTheHighestTorsionBit() {
        val scalar = TorsionScalar.fromLittleEndian(664, ByteArray(83) { 0xff.toByte() })

        val encoded = scalar.toLadderScalar(ParameterSet.NIST_V)

        assertContentEquals(ByteArray(84) { if (it < 83) 0xff.toByte() else 0 }, encoded)
    }

    @Test
    fun orderingComparesUnsignedMathematicalValuesAcrossAllBytes() {
        val cases =
            listOf(
                Triple("0001", "ff00", 1),
                Triple("7f00", "8000", -1),
                Triple("ff01", "ff01", 0),
            )
        for ((left, right, expected) in cases) {
            val first = TorsionScalar.fromLittleEndian(9, left.hexToByteArray())
            val second = TorsionScalar.fromLittleEndian(9, right.hexToByteArray())

            val comparison = first.compareTo(second)

            assertEquals(expected, comparison)
        }
    }

    @Test
    fun decodingAndEncodingNeverAliasCallerStorage() {
        val encoded = "ff01".hexToByteArray()
        val scalar = TorsionScalar.fromLittleEndian(9, encoded)
        encoded.fill(0)

        val exported = scalar.toLittleEndian()

        exported.fill(0)
        assertContentEquals("ff01".hexToByteArray(), scalar.toLittleEndian())
    }

    @Test
    fun invalidExponentsFailBeforeEncodingInspection() {
        for (exponent in listOf(Int.MIN_VALUE, 0, 665, Int.MAX_VALUE)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionScalar.fromLittleEndian(exponent, byteArrayOf())
                }

            assertEquals(
                "Torsion scalar exponent must be between one and 664",
                failure.message,
            )
        }
    }

    @Test
    fun scalarDecodingRejectsTruncatedAndOverlongEncodings() {
        for (encoded in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(1, 0, 0))) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionScalar.fromLittleEndian(9, encoded)
                }

            assertEquals(
                "Torsion scalar encoding must have exactly ceil(exponent/8) bytes",
                failure.message,
            )
        }
    }

    @Test
    fun unusedHighBitsAreRejectedRatherThanReduced() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                TorsionScalar.fromLittleEndian(9, "0002".hexToByteArray())
            }

        assertEquals("Torsion scalar encoding must not set unused high bits", failure.message)
    }

    @Test
    fun evenResiduesAreNotInvertible() {
        for (encoded in listOf("0000", "0200", "fe01")) {
            val scalar = TorsionScalar.fromLittleEndian(9, encoded.hexToByteArray())

            val failure = assertFailsWith<IllegalArgumentException> { scalar.inverse() }

            assertEquals("Torsion inverse requires an odd residue", failure.message)
        }
    }

    @Test
    fun exponentOneRetainsItsNonzeroUnit() {
        val one = TorsionScalar.fromLittleEndian(1, byteArrayOf(1))

        val inverse = one.inverse()

        assertContentEquals(byteArrayOf(1), inverse.toLittleEndian())
    }

    @Test
    fun parityUsesTheUnsignedLowBitAtSignedByteBoundaries() {
        for ((lowByte, expected) in listOf(0 to false, 128 to false, 129 to true, 255 to true)) {
            val scalar = TorsionScalar.fromLittleEndian(9, byteArrayOf(lowByte.toByte(), 1))

            val odd = scalar.isOdd()

            assertEquals(expected, odd)
        }
    }

    @Test
    fun ladderExportRejectsAnExponentAboveTheParameterTorsion() {
        val scalar = TorsionScalar.fromLittleEndian(501, ByteArray(63))

        val failure =
            assertFailsWith<IllegalArgumentException> {
                scalar.toLadderScalar(ParameterSet.NIST_III)
            }

        assertEquals("Torsion scalar exponent must fit the ladder parameter", failure.message)
    }

    @Test
    fun additionRejectsMixedExponents() = assertMixedExponentFailure { first, second ->
        first.add(second)
    }

    @Test
    fun subtractionRejectsMixedExponents() = assertMixedExponentFailure { first, second ->
        first.subtract(second)
    }

    @Test
    fun multiplicationRejectsMixedExponents() = assertMixedExponentFailure { first, second ->
        first.multiply(second)
    }

    @Test
    fun comparisonRejectsMixedExponents() = assertMixedExponentFailure { first, second ->
        first.compareTo(second)
    }

    private fun assertMixedExponentFailure(operation: (TorsionScalar, TorsionScalar) -> Unit) {
        val first = TorsionScalar.fromLittleEndian(1, byteArrayOf(1))
        val second = TorsionScalar.fromLittleEndian(2, byteArrayOf(1))

        val failure = assertFailsWith<IllegalArgumentException> { operation(first, second) }

        assertEquals("Torsion scalars must have the same exponent", failure.message)
    }
}
