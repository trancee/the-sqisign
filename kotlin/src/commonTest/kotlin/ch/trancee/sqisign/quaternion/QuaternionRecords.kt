// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord

/** The record field order `2N E Dq C B p` of an inert O0-ideal HNF. */
internal const val HNF_TWO_N = 0
internal const val HNF_E = 1
internal const val HNF_DQ = 2
internal const val HNF_C = 3
internal const val HNF_B = 4
internal const val HNF_P = 5

/** The numerator `[[2N, 0, E, Dq], [0, 2N, C, B], [0, 0, 1, 0], [0, 0, 0, 1]]` of the record. */
internal fun IbzRecord.idealHnf(): List<List<Ibz>> {
    val zero = Ibz.zero(capacity)
    val one = Ibz.one(capacity)
    val twoN = ibz(HNF_TWO_N)
    return listOf(
        listOf(twoN, zero, ibz(HNF_E), ibz(HNF_DQ)),
        listOf(zero, twoN, ibz(HNF_C), ibz(HNF_B)),
        listOf(zero, zero, one, zero),
        listOf(zero, zero, zero, one),
    )
}

internal fun IbzRecord.lllConfig(): LllConfig =
    LllConfig(ParameterSet.entries.single { it.identifier == level })
