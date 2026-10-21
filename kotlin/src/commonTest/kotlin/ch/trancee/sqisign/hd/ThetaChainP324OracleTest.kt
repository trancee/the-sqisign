// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test

class ThetaChainP324OracleTest {
    @Test
    fun thetaNullPointsMapToTheMaximalMontgomeryModel() =
        oracle.verifyModels(ThetaChainVectors.p324Models)

    @Test
    fun gluingMatricesMatchEverySymmetricElementCase() =
        oracle.verifyMatrices(ThetaChainVectors.p324Matrices)

    @Test
    fun splittingMapsProductThetaPointsToNormalizedCurvesAndRejectsDefects() =
        oracle.verifySplits(ThetaChainVectors.p324Splits)

    @Test
    fun katKernelsGlueAndSplitInEveryMode() =
        oracle.verifyChains(ThetaChainVectors.p324Chains, setOf("kat0", "kat1"))

    @Test
    fun malformedKernelsAreRejectedWithTheReferenceDiagnostic() =
        oracle.verifyChains(
            ThetaChainVectors.p324Chains,
            setOf("shorter", "longer", "duplicate", "swapped", "mixed", "halved", "degenerate"),
        )

    private companion object {
        val oracle = ThetaChainOracleAssertions(Fp2P324)
    }
}
