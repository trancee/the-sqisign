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

internal fun addFieldModulo(left: ByteArray, right: ByteArray, modulus: FieldModulus): ByteArray {
    val sum = ByteArray(left.size)
    var carry = 0
    for (index in left.indices) {
        val value = (left[index].toInt() and 0xff) + (right[index].toInt() and 0xff) + carry
        sum[index] = value.toByte()
        carry = value ushr Byte.SIZE_BITS
    }
    return if (compareFieldBytes(sum, modulus.bytes) >= 0) subtractFieldBytes(sum, modulus.bytes)
    else sum
}

internal fun subtractFieldModulo(
    left: ByteArray,
    right: ByteArray,
    modulus: FieldModulus,
): ByteArray =
    if (compareFieldBytes(left, right) >= 0) {
        subtractFieldBytes(left, right)
    } else {
        subtractFieldBytes(modulus.bytes, subtractFieldBytes(right, left))
    }

internal fun negateFieldModulo(value: ByteArray, modulus: FieldModulus): ByteArray =
    if (isZeroFieldBytes(value)) value.copyOf() else subtractFieldBytes(modulus.bytes, value)

internal fun multiplyFieldModulo(
    left: ByteArray,
    right: ByteArray,
    modulus: FieldModulus,
): ByteArray = modulus.multiply(left, right)

internal fun exponentiateField(
    value: ByteArray,
    exponent: ByteArray,
    modulus: FieldModulus,
): ByteArray = modulus.exponentiate(value, exponent)

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
