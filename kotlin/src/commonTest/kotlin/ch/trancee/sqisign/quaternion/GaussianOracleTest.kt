// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class GaussianOracleTest {
    @Test
    fun innerKernelReturnsTheReferenceTransform() =
        each("ginner") { record ->
            val words = (0 until WORDS).map { record.arguments[it].toULong(HEX).toLong() }
            val windows =
                GaussianWindows(
                    ComplexWord(words[0], words[1]),
                    ComplexWord(words[2], words[3]),
                    ComplexWord(words[4], words[5]),
                    ComplexWord(words[6], words[7]),
                )
            gaussianInner(
                    windows,
                    record.int(SWAP) != 0,
                    record.int(FROM_V1),
                    record.int(FROM_V2),
                )
                .flatten()
                .joinToString(" ")
        }

    @Test
    fun idealReductionReturnsTheReferenceBasis() =
        each("gauss") { record ->
            record
                .lllConfig()
                .reduceO0Ideal(record.idealHnf(), record.ibz(HNF_P), record.int(BOUND))
                .flatten()
                .joinToString(" ") { encode(it) }
        }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, GaussianVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private companion object {
        const val WORDS = 8
        const val HEX = 16
        const val SWAP = 8
        const val FROM_V1 = 9
        const val FROM_V2 = 10
        const val BOUND = 6
    }
}
