// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.inverseVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UInt328ModInverseTest {
    @Test
    fun inversesMatchIndependentVectorsAndExplicitFailureCategories() {
        val oneBytes = smallUnsigned(1).toLittleEndian()
        for ((index, record) in inverseVectors.withIndex()) {
            val fields = record.split(':')
            val inputBytes = fields[0].hexToByteArray()
            val modulusBytes = fields[1].hexToByteArray()
            val value = UInt328.fromLittleEndian(inputBytes)
            val modulus = UInt328.fromLittleEndian(modulusBytes)
            val errorMessage =
                when (fields[2]) {
                    "invalid_modulus" ->
                        "Unsigned modular inverse requires a modulus of at least two"
                    "not_coprime" -> "Unsigned modular inverse requires coprime operands"
                    else -> null
                }
            if (errorMessage != null) {
                val error = assertFailsWith<IllegalArgumentException> { value.modInverse(modulus) }
                assertEquals(errorMessage, error.message, "Failure case $index")
                assertContentEquals(inputBytes, value.toLittleEndian(), "Failed input case $index")
                assertContentEquals(
                    modulusBytes,
                    modulus.toLittleEndian(),
                    "Failed modulus case $index",
                )
                continue
            }
            val expected = fields[2].hexToByteArray()

            val output = value.modInverse(modulus)

            assertContentEquals(expected, output.toLittleEndian(), "Inverse case $index")
            assertEquals(-1, output.compareTo(modulus), "Canonical inverse case $index")
            assertContentEquals(
                oneBytes,
                value.multiply(output).divideAndRemainder(modulus).remainder.toLittleEndian(),
                "Inverse identity case $index",
            )
            assertContentEquals(inputBytes, value.toLittleEndian(), "Input case $index")
            assertContentEquals(modulusBytes, modulus.toLittleEndian(), "Modulus case $index")
        }
    }

    @Test
    fun smallValuesMatchIndependentInverseSearch() {
        for (modulus in 2..31) {
            for (value in 0..63) {
                val input = smallUnsigned(value)
                val modulusValue = smallUnsigned(modulus)
                val expected = (1 until modulus).firstOrNull { (it * value) % modulus == 1 }
                if (expected == null) {
                    val error =
                        assertFailsWith<IllegalArgumentException> { input.modInverse(modulusValue) }
                    assertEquals(
                        "Unsigned modular inverse requires coprime operands",
                        error.message,
                    )
                    continue
                }

                val output = input.modInverse(modulusValue)

                assertContentEquals(
                    smallUnsigned(expected).toLittleEndian(),
                    output.toLittleEndian(),
                    "$value inverse modulo $modulus",
                )
            }
        }
    }

    @Test
    fun invalidModuliAreRejectedBeforeAttemptingInversion() {
        for (modulus in 0..1) {
            for (value in 0..1) {
                val input = smallUnsigned(value)
                val modulusValue = smallUnsigned(modulus)

                val error =
                    assertFailsWith<IllegalArgumentException> { input.modInverse(modulusValue) }

                assertEquals(
                    "Unsigned modular inverse requires a modulus of at least two",
                    error.message,
                )
            }
        }
    }

    @Test
    fun maximumMinusOneIsSelfInverseModuloMaximum() {
        val modulusBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val inputBytes = modulusBytes.copyOf().also { it[0] = -2 }
        val input = UInt328.fromLittleEndian(inputBytes)
        val modulus = UInt328.fromLittleEndian(modulusBytes)

        val output = input.modInverse(modulus)

        assertContentEquals(inputBytes, output.toLittleEndian())
        assertContentEquals(inputBytes, input.toLittleEndian(), "Input remains unchanged")
        assertContentEquals(modulusBytes, modulus.toLittleEndian(), "Modulus remains unchanged")
    }

    @Test
    fun oddMaximumIsSelfInverseModuloTheLargestPowerOfTwo() {
        val inputBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val input = UInt328.fromLittleEndian(inputBytes)
        val modulusBytes =
            ByteArray(UInt328.ENCODED_BYTES).also { it[it.lastIndex] = 0x80.toByte() }
        val modulus = UInt328.fromLittleEndian(modulusBytes)
        val expected = inputBytes.copyOf().also { it[it.lastIndex] = 0x7f }

        val output = input.modInverse(modulus)

        assertContentEquals(expected, output.toLittleEndian())
    }

    @Test
    fun equalOperandsAreRejectedWithoutMutation() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val value = UInt328.fromLittleEndian(encoded)

        val error = assertFailsWith<IllegalArgumentException> { value.modInverse(value) }

        assertEquals("Unsigned modular inverse requires coprime operands", error.message)
        assertContentEquals(encoded, value.toLittleEndian())
    }
}

private fun smallUnsigned(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })
