// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.Fp2P500
import kotlin.test.Test

class ThetaChainP500OracleTest {
    @Test
    fun katKernelGluesAndSplitsInEveryMode() =
        ThetaChainOracleAssertions(Fp2P500)
            .verifyChains(ThetaChainVectors.p500Chains, setOf("kat0"))
}
