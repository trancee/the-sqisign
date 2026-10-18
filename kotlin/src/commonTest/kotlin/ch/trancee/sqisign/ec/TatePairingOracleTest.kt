// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test

class TatePairingOracleTest {
    @Test
    fun p324IntermediatePairingMatchesIndependentShiftedMiller() {
        oracle.verify(0, 8, setOf("generic"))
    }

    @Test
    fun coincidentAndInfiniteOperandsRetainTheMaximalDomainIdentity() {
        oracle.verify(0, 8, setOf("self", "opposite", "infinityFirst", "infinitySecond"))
    }

    @Test
    fun pairingWithZeroTwoTorsionInTheSecondSlotIsDefined() {
        oracle.verify(0, 8, setOf("secondT0"))
    }

    @Test
    fun fullOrderPairingWithZeroTwoTorsionRetainsItsNontrivialSign() {
        oracle.verify(0, Fp2P324.parameters.torsionExponent, setOf("secondT0"))
    }

    @Test fun e0OrderTwoPairings() = oracle.verify(0, 1)

    @Test
    fun e0OtherIntermediatePairings() =
        oracle.verify(0, 8, setOf("restricted", "firstT0", "smallerOrder", "inverse", "swapped"))

    @Test
    fun e0ReducedGenericPairing() =
        oracle.verify(0, Fp2P324.parameters.torsionExponent - 1, setOf("generic"))

    @Test
    fun e0ReducedRestrictionRemainsNontrivial() =
        oracle.verify(0, Fp2P324.parameters.torsionExponent - 1, setOf("restricted"))

    @Test
    fun e0ReducedBoundaries() =
        oracle.verify(
            0,
            Fp2P324.parameters.torsionExponent - 1,
            setOf(
                "self",
                "opposite",
                "firstT0",
                "secondT0",
                "smallerOrder",
                "infinityFirst",
                "infinitySecond",
            ),
        )

    @Test
    fun e0FullGenericPairing() =
        oracle.verify(0, Fp2P324.parameters.torsionExponent, setOf("generic"))

    @Test
    fun e0FullSwapped() = oracle.verify(0, Fp2P324.parameters.torsionExponent, setOf("swapped"))

    @Test
    fun e0FullOtherBoundaries() =
        oracle.verify(
            0,
            Fp2P324.parameters.torsionExponent,
            setOf(
                "firstT0",
                "smallerOrder",
                "secondT0Square",
                "bothT0",
            ),
        )

    @Test
    fun e0FullIdentities() =
        oracle.verify(
            0,
            Fp2P324.parameters.torsionExponent,
            setOf("self", "opposite", "infinityFirst", "infinitySecond"),
        )

    @Test fun squareCoefficientOrderTwo() = oracle.verify(1, 1)

    @Test fun squareCoefficientIntermediate() = oracle.verify(1, 8)

    @Test fun squareCoefficientReduced() = oracle.verify(1, Fp2P324.parameters.torsionExponent - 1)

    @Test
    fun squareCoefficientFullGeneric() =
        oracle.verify(1, Fp2P324.parameters.torsionExponent, setOf("generic"))

    @Test
    fun squareCoefficientFullBoundaries() =
        oracle.verify(1, Fp2P324.parameters.torsionExponent, setOf("self", "firstT0", "secondT0"))

    @Test fun nonsquareCoefficientOrderTwo() = oracle.verify(2, 1)

    @Test fun nonsquareCoefficientIntermediate() = oracle.verify(2, 8)

    @Test
    fun nonsquareCoefficientReduced() = oracle.verify(2, Fp2P324.parameters.torsionExponent - 1)

    @Test
    fun nonsquareCoefficientFullGeneric() =
        oracle.verify(2, Fp2P324.parameters.torsionExponent, setOf("generic"))

    @Test
    fun nonsquareCoefficientFullBoundaries() =
        oracle.verify(2, Fp2P324.parameters.torsionExponent, setOf("self", "firstT0", "secondT0"))

    private companion object {
        val oracle =
            TateOracleAssertions(
                Fp2P324,
                PairingVectors.p324PairingCurves,
                PairingVectors.p324TatePairings,
            )
    }
}
