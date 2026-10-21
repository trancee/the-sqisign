// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.ParameterSet

private const val TORSION_BYTE_MASK = 0xff

/** An owned canonical residue modulo 2^exponent, distinct from an ordinary ladder scalar. */
internal class TorsionScalar
private constructor(
    internal val exponent: Int,
    private val bytes: ByteArray,
) {
    internal fun toLittleEndian(): ByteArray = bytes.copyOf()

    internal fun isOdd(): Boolean = bytes[0].toInt() and 1 != 0

    internal fun toLadderScalar(parameters: ParameterSet): ByteArray {
        require(exponent <= parameters.torsionExponent) {
            "Torsion scalar exponent must fit the ladder parameter"
        }
        return bytes.copyOf(parameters.fieldBytes)
    }

    internal operator fun compareTo(other: TorsionScalar): Int {
        requireSameExponent(other)
        for (index in bytes.lastIndex downTo 0) {
            val comparison =
                (bytes[index].toInt() and TORSION_BYTE_MASK).compareTo(
                    other.bytes[index].toInt() and TORSION_BYTE_MASK,
                )
            if (comparison != 0) return comparison
        }
        return 0
    }

    internal fun add(other: TorsionScalar): TorsionScalar {
        requireSameExponent(other)
        val output = ByteArray(bytes.size)
        var carry = 0
        for (index in bytes.indices) {
            val sum =
                (bytes[index].toInt() and TORSION_BYTE_MASK) +
                    (other.bytes[index].toInt() and TORSION_BYTE_MASK) +
                    carry
            output[index] = sum.toByte()
            carry = sum ushr Byte.SIZE_BITS
        }
        return reduced(output)
    }

    internal fun subtract(other: TorsionScalar): TorsionScalar {
        requireSameExponent(other)
        val output = ByteArray(bytes.size)
        var borrow = 0
        for (index in bytes.indices) {
            val difference =
                (bytes[index].toInt() and TORSION_BYTE_MASK) -
                    (other.bytes[index].toInt() and TORSION_BYTE_MASK) -
                    borrow
            output[index] = difference.toByte()
            borrow = difference ushr (Int.SIZE_BITS - 1)
        }
        return reduced(output)
    }

    internal fun negate(): TorsionScalar =
        TorsionScalar(exponent, ByteArray(bytes.size)).subtract(this)

    internal fun multiply(other: TorsionScalar): TorsionScalar {
        requireSameExponent(other)
        val output = ByteArray(bytes.size)
        for (leftIndex in bytes.indices) {
            var carry = 0
            for (rightIndex in 0 until bytes.size - leftIndex) {
                val position = leftIndex + rightIndex
                // Immediate normalization bounds each multiply-add by 65535.
                val accumulated =
                    (bytes[leftIndex].toInt() and TORSION_BYTE_MASK) *
                        (other.bytes[rightIndex].toInt() and TORSION_BYTE_MASK) +
                        (output[position].toInt() and TORSION_BYTE_MASK) +
                        carry
                output[position] = accumulated.toByte()
                carry = accumulated ushr Byte.SIZE_BITS
            }
        }
        return reduced(output)
    }

    internal fun inverse(): TorsionScalar {
        require(isOdd()) { "Torsion inverse requires an odd residue" }
        val one = TorsionScalar(exponent, ByteArray(bytes.size).also { it[0] = 1 })
        val two = one.add(one)
        var inverse = one
        var correctBits = 1
        while (correctBits < exponent) {
            inverse = inverse.multiply(two.subtract(multiply(inverse)))
            correctBits *= 2
        }
        return inverse
    }

    private fun reduced(output: ByteArray): TorsionScalar {
        output[output.lastIndex] = (output.last().toInt() and torsionTopMask(exponent)).toByte()
        return TorsionScalar(exponent, output)
    }

    private fun requireSameExponent(other: TorsionScalar) {
        require(exponent == other.exponent) { "Torsion scalars must have the same exponent" }
    }

    internal companion object {
        internal fun fromLittleEndian(exponent: Int, encoded: ByteArray): TorsionScalar {
            require(exponent in 1..ParameterSet.NIST_V.torsionExponent) {
                "Torsion scalar exponent must be between one and 664"
            }
            require(encoded.size == (exponent + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS) {
                "Torsion scalar encoding must have exactly ceil(exponent/8) bytes"
            }
            require(encoded.last().toInt() and TORSION_BYTE_MASK <= torsionTopMask(exponent)) {
                "Torsion scalar encoding must not set unused high bits"
            }
            return TorsionScalar(exponent, encoded.copyOf())
        }
    }
}

private fun torsionTopMask(exponent: Int): Int = (1 shl ((exponent - 1) % Byte.SIZE_BITS + 1)) - 1
