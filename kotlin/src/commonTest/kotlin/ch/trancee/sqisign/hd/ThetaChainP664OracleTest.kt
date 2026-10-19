// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.Fp2P664
import kotlin.test.Test

class ThetaChainP664OracleTest {
    @Test
    fun katKernelGluesAndSplitsInEveryMode() =
        ThetaChainOracleAssertions(Fp2P664)
            .verifyChains(ThetaChainVectors.p664Chains, setOf("kat0"))
}
