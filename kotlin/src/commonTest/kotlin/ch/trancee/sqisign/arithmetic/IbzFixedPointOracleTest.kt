// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class IbzFixedPointOracleTest {
    @Test
    fun signedShiftsAreExactBeforeNarrowing() =
        each("cshift") { encode(it.ibz(0).shiftSigned(it.int(1), it.int(2), it.int(3))) }

    @Test
    fun highProductsFloorTheExactProduct() =
        each("highp") { encode(it.ibz(0).highProduct(it.ibz(1), it.int(2), it.int(3))) }

    @Test
    fun scaledProductsShiftTheExactProduct() =
        each("highs") {
            encode(it.ibz(0).scaledProduct(it.ibz(1), it.int(2), it.int(3), it.int(4)))
        }

    @Test
    fun reciprocalsFollowTheNewtonIterationFromTheSeed() =
        each("recip") {
            val reciprocal = it.ibz(0).fixedPointReciprocal(it.int(1), it.int(2))
            "${encode(reciprocal.value)} ${reciprocal.scale}"
        }

    @Test
    fun wordAlignedShiftsRoundTiesUp() =
        each("rshift") { encode(it.ibz(0).roundShiftWords(it.int(1), it.int(2))) }

    @Test
    fun lessThanMasksAgreeWithOrdering() =
        each("ltmask") { if (it.ibz(0) < it.ibz(1)) "-1" else "0" }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, IbzFixedPointVectors.records)) {
            record.assertResult { compute(record) }
        }
    }
}
