// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import kotlin.test.Test

class KatVerificationP500OddTest {
    @Test
    fun everySelectedPublicSignatureVerifies() =
        verifyKatRecords(ParameterSet.NIST_III) { it % 2 == 1 }
}
