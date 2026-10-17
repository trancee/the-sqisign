// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

internal fun compareFieldBytes(left: ByteArray, right: ByteArray): Int {
    for (index in left.lastIndex downTo 0) {
        val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun isZeroFieldBytes(value: ByteArray): Boolean = value.all { it == 0.toByte() }

internal fun subtractFieldBytes(left: ByteArray, right: ByteArray): ByteArray {
    val difference = ByteArray(left.size)
    var borrow = 0
    for (index in left.indices) {
        val value = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff) - borrow
        difference[index] = value.toByte()
        borrow = (value ushr Int.SIZE_BITS - 1) and 1
    }
    return difference
}

internal fun addFieldModulo(left: ByteArray, right: ByteArray, modulus: ByteArray): ByteArray {
    val sum = ByteArray(left.size)
    var carry = 0
    for (index in left.indices) {
        val value = (left[index].toInt() and 0xff) + (right[index].toInt() and 0xff) + carry
        sum[index] = value.toByte()
        carry = value ushr Byte.SIZE_BITS
    }
    return if (compareFieldBytes(sum, modulus) >= 0) subtractFieldBytes(sum, modulus) else sum
}

internal fun subtractFieldModulo(left: ByteArray, right: ByteArray, modulus: ByteArray): ByteArray =
    if (compareFieldBytes(left, right) >= 0) {
        subtractFieldBytes(left, right)
    } else {
        subtractFieldBytes(modulus, subtractFieldBytes(right, left))
    }

internal fun negateFieldModulo(value: ByteArray, modulus: ByteArray): ByteArray =
    if (isZeroFieldBytes(value)) value.copyOf() else subtractFieldBytes(modulus, value)

internal fun multiplyFieldModulo(left: ByteArray, right: ByteArray, modulus: ByteArray): ByteArray {
    var result = ByteArray(left.size)
    var addend = left.copyOf()
    for (byte in right) {
        val bits = byte.toInt() and 0xff
        for (bit in 0 until Byte.SIZE_BITS) {
            if (((bits ushr bit) and 1) != 0) {
                result = addFieldModulo(result, addend, modulus)
            }
            addend = addFieldModulo(addend, addend, modulus)
        }
    }
    return result
}

internal fun exponentiateField(
    value: ByteArray,
    exponent: ByteArray,
    modulus: ByteArray,
): ByteArray {
    var result = ByteArray(value.size).also { it[0] = 1 }
    for (bit in exponent.size * Byte.SIZE_BITS - 1 downTo 0) {
        result = multiplyFieldModulo(result, result, modulus)
        if (((exponent[bit / Byte.SIZE_BITS].toInt() ushr (bit % Byte.SIZE_BITS)) and 1) != 0) {
            result = multiplyFieldModulo(result, value, modulus)
        }
    }
    return result
}

internal fun fieldInverseExponent(modulus: ByteArray): ByteArray {
    val two = ByteArray(modulus.size).also { it[0] = 2 }
    return subtractFieldBytes(modulus, two)
}

internal fun fieldSquareRootExponent(modulus: ByteArray): ByteArray {
    val plusOne = modulus.copyOf()
    var carry = 1
    for (index in plusOne.indices) {
        val value = (plusOne[index].toInt() and 0xff) + carry
        plusOne[index] = value.toByte()
        carry = value ushr Byte.SIZE_BITS
    }
    repeat(2) {
        var highBit = 0
        for (index in plusOne.lastIndex downTo 0) {
            val value = plusOne[index].toInt() and 0xff
            plusOne[index] = ((value ushr 1) or (highBit shl (Byte.SIZE_BITS - 1))).toByte()
            highBit = value and 1
        }
    }
    return plusOne
}
