// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class GramOracleTest {
    @Test
    fun outerPassesHalveEveryThreeRoundsDownToFour() =
        each("outer") { config(it).outerIterations(it.int(0)).toString() }

    @Test
    fun machineWordStepsStopAtTheFirstInvalidStep() =
        each("gmsb") {
            partialGramMsb(word(it, 0), word(it, 1), word(it, 2), it.int(3) == 1).joinToString(" ")
        }

    @Test
    fun blocksTruncateToTheCommonGramScale() =
        each("block") {
            val config = config(it)
            val form =
                config.materialiseBlock(it.ibz(0), it.ibz(1), it.ibz(2), config.precisionBits)
            "${encode(form.a)} ${encode(form.b)} ${encode(form.c)} ${form.scale}"
        }

    @Test
    fun lehmerGaussReductionFollowsTheReferencePath() =
        each("gram") {
            val reduction =
                config(it).gramLehmer(GramForm(it.ibz(0), it.ibz(1), it.ibz(2), 0), it.int(3))
            val form = reduction.form
            (listOf(form.a, form.b, form.c) + reduction.transform).joinToString(" ") { value ->
                encode(value)
            }
        }

    private fun config(record: IbzRecord): LllConfig =
        LllConfig(ParameterSet.entries.single { it.identifier == record.level })

    private fun word(record: IbzRecord, index: Int): Long =
        record.arguments[index].toULong(HEX_RADIX).toLong()

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, GramVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private companion object {
        const val HEX_RADIX = 16
    }
}
