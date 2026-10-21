// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P664
import kotlin.test.Test

class TatePairingP664OracleTest {
    @Test fun e0OrderTwo() = oracle.verify(0, 1)

    @Test
    fun e0Intermediate() = oracle.verify(0, 8, setOf("generic", "inverse", "restricted", "swapped"))

    @Test
    fun e0IntermediateIdentities() =
        oracle.verify(0, 8, setOf("self", "opposite", "infinityFirst", "infinitySecond"))

    @Test
    fun e0IntermediateTwoTorsion() =
        oracle.verify(0, 8, setOf("firstT0", "secondT0", "smallerOrder"))

    @Test
    fun e0ReducedGeneric() =
        oracle.verify(0, Fp2P664.parameters.torsionExponent - 1, setOf("generic"))

    @Test
    fun e0ReducedRestrictionRemainsNontrivial() =
        oracle.verify(0, Fp2P664.parameters.torsionExponent - 1, setOf("restricted"))

    @Test
    fun e0ReducedBoundaries() =
        oracle.verify(
            0,
            Fp2P664.parameters.torsionExponent - 1,
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
    fun e0FullIdentities() =
        oracle.verify(
            0,
            Fp2P664.parameters.torsionExponent,
            setOf("self", "opposite", "infinityFirst", "infinitySecond"),
        )

    @Test
    fun e0FullGeneric() = oracle.verify(0, Fp2P664.parameters.torsionExponent, setOf("generic"))

    @Test
    fun e0FullSwapped() = oracle.verify(0, Fp2P664.parameters.torsionExponent, setOf("swapped"))

    @Test
    fun e0FullBoundaries() =
        oracle.verify(
            0,
            Fp2P664.parameters.torsionExponent,
            setOf(
                "firstT0",
                "secondT0",
                "smallerOrder",
                "secondT0Square",
                "bothT0",
            ),
        )

    @Test fun squareCoefficientOrderTwo() = oracle.verify(1, 1)

    @Test fun squareCoefficientIntermediate() = oracle.verify(1, 8)

    @Test fun squareCoefficientReduced() = oracle.verify(1, Fp2P664.parameters.torsionExponent - 1)

    @Test
    fun squareCoefficientFullGeneric() =
        oracle.verify(1, Fp2P664.parameters.torsionExponent, setOf("generic"))

    @Test
    fun squareCoefficientFullBoundaries() =
        oracle.verify(1, Fp2P664.parameters.torsionExponent, setOf("self", "firstT0", "secondT0"))

    @Test fun nonsquareCoefficientOrderTwo() = oracle.verify(2, 1)

    @Test fun nonsquareCoefficientIntermediate() = oracle.verify(2, 8)

    @Test
    fun nonsquareCoefficientReduced() = oracle.verify(2, Fp2P664.parameters.torsionExponent - 1)

    @Test
    fun nonsquareCoefficientFullGeneric() =
        oracle.verify(2, Fp2P664.parameters.torsionExponent, setOf("generic"))

    @Test
    fun nonsquareCoefficientFullBoundaries() =
        oracle.verify(2, Fp2P664.parameters.torsionExponent, setOf("self", "firstT0", "secondT0"))

    private companion object {
        val oracle =
            TateOracleAssertions(
                Fp2P664,
                PairingVectors.p664PairingCurves,
                PairingVectors.p664TatePairings,
            )
    }
}
