// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test

class IsogenyP324OracleTest {
    @Test
    fun normalizationSelectsTheMaximalIsomorphicCoefficient() =
        oracle.verifyNormalizations(setOf("generic"))

    @Test
    fun normalizationRejectsKernelsAboveZeroAndWrongOrders() =
        oracle.verifyNormalizations(setOf("singular", "orderTwo", "orderEight", "infinity"))

    @Test fun fourIsogenyMatchesAffineVeluImagesAndCodomains() = oracle.verifyFourIsogenies()

    @Test
    fun fourIsogenyRejectsKernelsAboveZeroAndWrongOrders() = oracle.verifyFourIsogenyRejections()

    @Test
    fun shortChainsMatchTheIndependentAffineChain() =
        oracle.verifyChains(setOf("generic"), setOf(2, 8))

    @Test
    fun challengeAndResponseChainsMatchTheIndependentAffineChain() =
        oracle.verifyChains(
            setOf("generic"),
            setOf(Fp2P324.parameters.securityBits, Fp2P324.parameters.responseBits),
        )

    @Test
    fun maximalChainMatchesTheIndependentAffineChain() =
        oracle.verifyChains(setOf("generic"), setOf(Fp2P324.parameters.torsionExponent - 2))

    @Test
    fun chainRejectsSingularKernelsAndWrongOrders() =
        oracle.verifyChains(setOf("singular", "orderTooLow", "orderTooHigh", "infinity"))

    @Test fun chainRejectsLengthsOutsideThePositiveEvenDomain() = oracle.verifyChainLengthDomain()

    private companion object {
        val oracle =
            IsogenyOracleAssertions(
                Fp2P324,
                IsogenyVectors.p324Normalizations,
                IsogenyVectors.p324FourIsogenies,
                IsogenyVectors.p324Chains,
            )
    }
}
