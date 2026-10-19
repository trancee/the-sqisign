// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import kotlin.test.Test

class KatVerificationP324Test {
    @Test fun everyPublicSignatureVerifies() = verifyKatRecords(ParameterSet.NIST_I) { true }
}
