// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class LatticeOracleTest {
    @Test
    fun lehmerReductionFollowsTheReferencePath() =
        each("squares") {
            val squares = sumOfSquares(it.ibz(0), it.ibz(1))
            "${encode(squares.x)} ${encode(squares.y)}"
        }

    @Test
    fun cornacchiaSortsTheReducedVectorAndChecksItsNorm() =
        each("cornacchia") {
            val squares = it.ibz(0).cornacchiaPrime()
            if (squares == null) "0" else "1 ${encode(squares.x)} ${encode(squares.y)}"
        }

    @Test
    fun roundedDivisionRoundsHalvesTowardZero() =
        each("rdiv") { encode(it.ibz(0).roundedDivide(it.ibz(1))) }

    @Test
    fun sumsOfTwoSquaresKeepTheReferenceBitLengths() =
        each("sq2") { encode(sumOfTwoSquares(it.ibz(0), it.ibz(1))) }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, LatticeVectors.records)) {
            record.assertResult { compute(record) }
        }
    }
}
