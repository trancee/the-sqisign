// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import kotlin.test.Test

class VerifyCorpusP664Test {
    @Test fun kotlinMatchesEveryPinnedCVerdict() = verifyCorpus(ParameterSet.NIST_V)
}
