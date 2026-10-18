// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P500
import kotlin.test.Test

class MontgomeryP500OracleTest {
    private val oracle = CurveOracleAssertions(Fp2P500, curveP500Vectors)

    @Test fun doublingMatchesPythonAndC() = oracle.doubling()

    @Test fun additionMatchesPythonAndC() = oracle.addition()

    @Test fun coefficientNormalizationMatchesPythonAndC() = oracle.coefficient()

    @Test fun deterministicDifferenceMatchesTheExactPythonAndCChoice() = oracle.difference()

    @Test fun multiplicationPreservesZeroAndShortScalars() = oracle.multiply(0..7)

    @Test fun multiplicationPreservesBitsAboveTheResponseWidth() = oracle.multiply(8..8)

    @Test fun multiplicationPreservesBit511() = oracle.multiply(9..9)

    @Test fun multiplicationPreservesAll512Bits() = oracle.multiply(10..10)

    @Test fun multiplicationPreservesAlternatingBits() = oracle.multiply(11..11)

    @Test fun threePointMultiplicationPreservesZero() = oracle.threePoint(0..0)

    @Test fun threePointMultiplicationPreservesOne() = oracle.threePoint(1..1)

    @Test fun threePointMultiplicationPreservesTwo() = oracle.threePoint(2..2)

    @Test fun threePointMultiplicationPreservesThree() = oracle.threePoint(3..3)

    @Test fun threePointMultiplicationPreservesSeven() = oracle.threePoint(4..4)

    @Test fun threePointMultiplicationPreserves255() = oracle.threePoint(5..5)

    @Test fun threePointMultiplicationPreserves256() = oracle.threePoint(6..6)

    @Test fun threePointMultiplicationPreserves257() = oracle.threePoint(7..7)

    @Test fun threePointMultiplicationPreservesBitsAboveTheResponseWidth() = oracle.threePoint(8..8)

    @Test fun threePointMultiplicationPreservesBit511() = oracle.threePoint(9..9)

    @Test fun threePointMultiplicationPreservesAll512Bits() = oracle.threePoint(10..10)

    @Test fun threePointMultiplicationPreservesAlternatingBits() = oracle.threePoint(11..11)

    @Test fun biscalarMultiplicationPreservesBothZeroCoefficients() = oracle.biscalar(0..0)

    @Test fun biscalarMultiplicationPreservesAZeroRightCoefficient() = oracle.biscalar(1..1)

    @Test fun biscalarMultiplicationPreservesEvenAndOddCoefficients() = oracle.biscalar(2..2)

    @Test fun biscalarMultiplicationPreservesOddAndEvenCoefficients() = oracle.biscalar(3..3)

    @Test fun biscalarMultiplicationPreservesAnUnequalOddAndEvenPair() = oracle.biscalar(4..4)

    @Test fun biscalarMultiplicationPreservesOddByteBoundaryCoefficients() = oracle.biscalar(5..5)

    @Test fun biscalarMultiplicationPreservesEvenByteBoundaryCoefficients() = oracle.biscalar(6..6)

    @Test fun biscalarMultiplicationPreservesBothOddCoefficients() = oracle.biscalar(7..7)

    @Test
    fun biscalarMultiplicationReducesZeroCoefficientsToSingleLadders() = oracle.biscalar(14..15)

    @Test fun biscalarMultiplicationPreservesUnequalWideScalars() = oracle.biscalar(8..8)

    @Test fun biscalarMultiplicationPreservesBothHighestBits() = oracle.biscalar(9..9)

    @Test fun biscalarMultiplicationPreservesBothMaximumScalars() = oracle.biscalar(10..10)

    @Test fun biscalarMultiplicationPreservesAlternatingBits() = oracle.biscalar(11..11)
}
