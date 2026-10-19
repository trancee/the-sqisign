// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import kotlin.test.Test

class KatVerificationP664Residue3Test {
    @Test
    fun everySelectedPublicSignatureVerifies() =
        verifyKatRecords(ParameterSet.NIST_V) { it % 4 == 3 }
}
