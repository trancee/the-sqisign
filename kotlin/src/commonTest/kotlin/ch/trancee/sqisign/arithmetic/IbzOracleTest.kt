// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test
import kotlin.test.assertEquals

class IbzOracleTest {
    @Test
    fun sumsMatchIndependentModelAndRejectCapacityWrap() =
        binary("add") { left, right -> encode(left + right) }

    @Test
    fun differencesMatchIndependentModelAndRejectCapacityWrap() =
        binary("sub") { left, right -> encode(left - right) }

    @Test
    fun productsKeepBoundQuirksAndRejectCapacityWrap() =
        binary("mul") { left, right -> encode(left * right) }

    @Test
    fun comparisonsFollowSignedWordValues() =
        binary("cmp") { left, right -> left.compareTo(right).coerceIn(-1, 1).toString() }

    @Test
    fun truncatedDivisionKeepsOperandBounds() =
        binary("div") { left, right ->
            val division = left.divRem(right)
            "${encode(division.quotient)} ${encode(division.remainder)}"
        }

    @Test fun residuesAreNonNegative() = binary("mod") { left, right -> encode(left.mod(right)) }

    @Test
    fun divisibilityMatchesIndependentModel() =
        binary("divides") { left, right -> if (left.divides(right)) "1" else "0" }

    @Test
    fun boundedSumsTruncateWithoutCapacityRejection() =
        each("addb") { encode(it.ibz(0).plusWithBound(it.ibz(1), it.int(2))) }

    @Test
    fun negationAndAbsoluteValueKeepBounds() {
        each("neg") { encode(-it.ibz(0)) }
        each("abs") { encode(it.ibz(0).abs()) }
    }

    @Test
    fun shiftsFollowStoredWords() {
        each("shr") { encode(it.ibz(0).shiftRight(it.int(1))) }
        each("shl") { encode(it.ibz(0).shiftLeft(it.int(1))) }
        each("mod2exp") { encode(it.ibz(0).mod2exp(it.int(1))) }
    }

    @Test fun boundsExtendOrTruncate() = each("bound") { encode(it.ibz(0).withBound(it.int(1))) }

    @Test
    fun predicatesAndSizesMatchIndependentModel() =
        each("info") {
            val value = it.ibz(0)
            listOf(
                    value.bitSize(),
                    value.twoAdic(),
                    flag(value.isZero),
                    flag(value.isOne),
                    flag(!value.isNegative),
                    flag(value.isEven),
                    value.low32(),
                )
                .joinToString(" ")
                .also { _ -> assertEquals(value.isEven, !value.isOdd, it.description) }
        }

    @Test
    fun wordExtractionUsesMagnitudes() =
        each("extract") {
            val value = it.ibz(0)
            "${value.extractUnsigned64(it.int(1)).toULong().toString(16)} ${value.extractSigned64(it.int(1))}"
        }

    @Test
    fun digitCountsBoundSizesInBase() = each("size") { it.ibz(0).sizeInBase(it.int(1)).toString() }

    @Test
    fun boundedIntegerProductsAndSumsTruncate() {
        each("mulint") { encode(it.ibz(0).timesIntWithBound(it.int(1), it.int(2))) }
        each("addint") { encode(it.ibz(0).plusIntWithBound(it.int(1), it.int(2))) }
    }

    @Test
    fun smallComparisonsAndResiduesMatchIndependentModel() {
        each("cmpi") { it.ibz(0).compareTo(it.int(1)).coerceIn(-1, 1).toString() }
        each("modui") { it.ibz(0).modUnsigned(it.arguments[1].toULong()).toString() }
    }

    @Test
    fun nonNegativeValuesExportMinimalDigits() =
        each("digits") {
            val digits = it.ibz(0).toDigits()
            val text =
                digits.reversed().joinToString("") { word ->
                    word.toULong().toString(16).padStart(16, '0')
                }
            "${digits.size} ${text.ifEmpty { "-" }}"
        }

    @Test fun powersReuseCheckedProducts() = each("pow") { encode(it.ibz(0).pow(it.int(1))) }

    @Test
    fun textConversionsMatchIndependentModel() {
        each("str") { it.ibz(0).toString(it.int(1)) }
        each("parse") {
            val text = it.arguments[0].takeUnless { argument -> argument == "_" }.orEmpty()
            Ibz.parse(it.capacity, text, it.int(1))?.let(::encode) ?: "fail"
        }
    }

    @Test
    fun smallConstructorsNormaliseLikeC() {
        each("set") { encode(Ibz.of(it.capacity, it.int(0), it.int(1))) }
        each("bits") {
            val bits = it.int(0)
            val words = IbzRecord.magnitudeWords(it.arguments[1], maxOf((bits + 63) / 64, 1))
            encode(Ibz.copyBits(it.capacity, words, bits))
        }
    }

    @Test
    fun intervalSamplingConsumesExactlyTheScriptedBytes() {
        each("rand") {
            val source = ScriptedBytes(it.arguments[2])
            encode(Ibz.randomInterval(it.ibz(0), it.ibz(1), source::take)).also { _ ->
                source.assertDrained()
            }
        }
        each("randm") {
            val source = ScriptedBytes(it.arguments[1])
            encode(Ibz.randomSymmetric(it.capacity, it.int(0), source::take)).also { _ ->
                source.assertDrained()
            }
        }
    }

    private fun binary(operation: String, compute: (Ibz, Ibz) -> String) =
        each(operation) { compute(it.ibz(0), it.ibz(1)) }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation)) {
            record.assertResult { compute(record) }
        }
    }

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    private class ScriptedBytes(hex: String) {
        private val bytes = hex.hexToByteArray()
        private var offset = 0

        fun take(length: Int): ByteArray =
            bytes.copyOfRange(offset, offset + length).also { offset += length }

        fun assertDrained() = assertEquals(bytes.size, offset)
    }
}
