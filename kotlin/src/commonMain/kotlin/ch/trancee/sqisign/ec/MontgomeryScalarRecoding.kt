// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

/** Algorithm 4.89 recoding; the curve has checked equal widths and positive coefficients. */
internal fun recodeBiscalar(left: ByteArray, right: ByteArray): Pair<IntArray, Boolean> {
    val leftOdd = left[0].toInt() and 1 == 1
    val rightOdd = right[0].toInt() and 1 == 1
    val adjusted =
        arrayOf(
            if (leftOdd) left else decrementedScalar(left),
            if (rightOdd) right else decrementedScalar(right),
        )
    var side = if (!leftOdd && rightOdd) 1 else 0
    val bits = left.size * Byte.SIZE_BITS
    val digits = IntArray(2 * bits)
    for (index in 0 until bits) {
        digits[2 * index] = adjusted[side].bitAt(index) xor adjusted[side].bitAt(index + 1)
        val second = adjusted[1 - side].bitAt(index) xor adjusted[1 - side].bitAt(index + 1)
        digits[2 * index + 1] = second
        if (second == 1) side = 1 - side
    }
    return digits to (side == 1)
}

private fun decrementedScalar(value: ByteArray): ByteArray {
    val decremented = value.copyOf()
    var borrow = 1
    for (index in decremented.indices) {
        val difference = (decremented[index].toInt() and 0xff) - borrow
        decremented[index] = difference.toByte()
        borrow = (difference ushr (Int.SIZE_BITS - 1)) and 1
    }
    return decremented
}

private fun ByteArray.bitAt(index: Int): Int =
    if (index == size * Byte.SIZE_BITS) 0
    else this[index / Byte.SIZE_BITS].toInt() ushr (index % Byte.SIZE_BITS) and 1
