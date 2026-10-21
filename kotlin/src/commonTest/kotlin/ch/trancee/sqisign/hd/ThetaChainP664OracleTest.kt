// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.Fp2P664
import kotlin.test.Test

// One test per mode keeps each JS test well inside the Mocha per-test limit.
class ThetaChainP664OracleTest {
    private val oracle = ThetaChainOracleAssertions(Fp2P664)

    @Test fun katKernelGluesAndSplitsInVerifyMode() = verify(ThetaChainMode.VERIFY)

    @Test fun katKernelGluesAndSplitsInBothMode() = verify(ThetaChainMode.BOTH)

    @Test fun katKernelGluesAndSplitsInFirstMode() = verify(ThetaChainMode.FIRST)

    @Test fun katKernelGluesAndSplitsInSecondMode() = verify(ThetaChainMode.SECOND)

    private fun verify(mode: ThetaChainMode) =
        oracle.verifyChains(ThetaChainVectors.p664Chains, setOf("kat0"), setOf(mode))
}
