// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.sqrtFloorVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UInt328SqrtTest {
    @Test
    fun squareRootsMatchIndependentVectors() {
        for ((index, record) in sqrtFloorVectors.withIndex()) {
            val fields = record.split(':')
            val input = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val expected = fields[1].hexToByteArray()

            val output = input.sqrtFloor()

            assertContentEquals(expected, output.toLittleEndian(), "Square-root case $index")
        }
    }

    @Test
    fun squareRootOfZeroIsZero() {
        val output = unsigned(0).sqrtFloor()

        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.toLittleEndian())
    }

    @Test
    fun smallValuesMatchExactFloors() {
        val expected = intArrayOf(0, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 4, 4)
        for (value in expected.indices) {
            val output = unsigned(value).sqrtFloor()

            assertContentEquals(
                unsigned(expected[value]).toLittleEndian(),
                output.toLittleEndian(),
                "$value",
            )
        }
    }

    @Test
    fun powersOfTwoAndTheirNeighborsMatchExactFloors() {
        val one = unsigned(1)
        for (bit in 2 until 328 step 2) {
            val power = powerOfTwo(bit)
            val expected = powerOfTwo(bit / 2)
            assertContentEquals(
                expected.toLittleEndian(),
                power.sqrtFloor().toLittleEndian(),
                "2^$bit",
            )
            val below = power.subtract(one)
            val above = power.add(one)
            assertEquals(0, below.borrow, "2^$bit - 1 is representable")
            assertEquals(0, above.carry, "2^$bit + 1 is representable")
            assertContentEquals(
                expected.subtract(one).value.toLittleEndian(),
                below.value.sqrtFloor().toLittleEndian(),
                "2^$bit - 1",
            )
            assertContentEquals(
                expected.toLittleEndian(),
                above.value.sqrtFloor().toLittleEndian(),
                "2^$bit + 1",
            )
        }
    }

    @Test
    fun perfectSquaresAndTheirNeighborsMatchAcrossTheRootWidth() {
        val one = unsigned(1)
        for (bit in 0 until 164) {
            val root = powerOfTwo(bit)
            val square = narrow(root.multiply(root).toLittleEndian())
            val below = square.subtract(one)
            val above = square.add(one)
            assertEquals(0, below.borrow, "Root 2^$bit is nonzero")
            assertEquals(0, above.carry, "Square of 2^$bit has a representable successor")

            assertContentEquals(
                root.subtract(one).value.toLittleEndian(),
                below.value.sqrtFloor().toLittleEndian(),
                "(2^$bit)^2 - 1",
            )
            assertContentEquals(
                root.toLittleEndian(),
                square.sqrtFloor().toLittleEndian(),
                "(2^$bit)^2",
            )
            assertContentEquals(
                root.toLittleEndian(),
                above.value.sqrtFloor().toLittleEndian(),
                "(2^$bit)^2 + 1",
            )
        }
    }

    @Test
    fun maximumHasTheLargestRepresentableSquareRoot() {
        val maximumRootBytes = ByteArray(UInt328.ENCODED_BYTES)
        maximumRootBytes[20] = 0x0f
        for (index in 0 until 20) maximumRootBytes[index] = 0xff.toByte()
        val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { 0xff.toByte() })

        val output = maximum.sqrtFloor()

        assertContentEquals(maximumRootBytes, output.toLittleEndian())
    }
}

private fun unsigned(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })

private fun powerOfTwo(bit: Int): UInt328 =
    UInt328.fromLittleEndian(
        ByteArray(UInt328.ENCODED_BYTES).also { encoded ->
            encoded[bit / 8] = (1 shl (bit % 8)).toByte()
        },
    )

private fun narrow(wide: ByteArray): UInt328 {
    check(wide.drop(UInt328.ENCODED_BYTES).all { it == 0.toByte() })
    return UInt328.fromLittleEndian(wide.copyOf(UInt328.ENCODED_BYTES))
}
