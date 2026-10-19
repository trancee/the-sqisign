// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import kotlin.test.Test
import kotlin.test.assertTrue

class SqiSignVerifyP500Test {
    @Test
    fun publicKatRecordZeroVerifies() {
        val record = KatRecordZero(ParameterSet.NIST_III)

        val valid = record.verify()

        assertTrue(valid)
    }
}
