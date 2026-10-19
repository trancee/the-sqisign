// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.SqiSign
import ch.trancee.sqisign.WireFormat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val KAT_RECORDS = 100

/** Verifies every upstream KAT signature of [parameters] whose count satisfies [selected]. */
internal fun verifyKatRecords(parameters: ParameterSet, selected: (Int) -> Boolean) {
    val records = katRecords(parameters)
    assertEquals((0 until KAT_RECORDS).toList(), records.map { it.count })
    val failures =
        records
            .filter { selected(it.count) }
            .filterNot {
                val signature = it.signedMessage.copyOf(parameters.signatureBytes)
                assertTrue(
                    it.signedMessage
                        .copyOfRange(parameters.signatureBytes, it.signedMessage.size)
                        .contentEquals(it.message),
                )
                SqiSign.verify(
                    WireFormat.publicKey(parameters, it.publicKey),
                    it.message,
                    WireFormat.signature(parameters, signature),
                )
            }
            .map { it.count }
    assertEquals(emptyList(), failures, "KAT records that failed verification")
}
