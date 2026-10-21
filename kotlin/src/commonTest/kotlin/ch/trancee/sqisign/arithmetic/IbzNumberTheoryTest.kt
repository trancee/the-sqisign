// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class IbzNumberTheoryTest {
    @Test
    fun gcdOperationsRejectMixedCapacities() {
        val wide = Ibz.one(CAPACITY + Long.SIZE_BITS)

        assertFailsWith<IllegalArgumentException> { z("2:1").gcd(wide) }
        assertFailsWith<IllegalArgumentException> { z("2:1").extendedGcd(wide) }
        assertFailsWith<IllegalArgumentException> { z("2:1").inverseMod(wide) }
        assertFailsWith<IllegalArgumentException> { z("2:1").jacobi(wide) }
    }

    @Test
    fun cofactorOperationsNeedOneLimbOfHeadroom() {
        assertFailsWith<IllegalArgumentException> { z("193:1").extendedGcd(z("2:1")) }
        assertFailsWith<IllegalArgumentException> { z("2:1").inverseMod(z("193:1")) }
    }

    @Test
    fun inversesNeedAPositiveModulus() {
        assertFailsWith<IllegalArgumentException> { z("2:1").inverseMod(z("1:0")) }
        assertFailsWith<IllegalArgumentException> { z("2:1").inverseMod(z("3:-3")) }
    }

    @Test
    fun chineseRemaindersRejectSharedFactorsAndOverflow() {
        val small = z("3:1")

        assertFailsWith<IllegalArgumentException> { Ibz.crt(small, small, z("3:2"), z("4:4")) }
        assertFailsWith<IllegalArgumentException> {
            Ibz.crt(small, z("200:1"), z("3:3"), z("100:4000000000000000000000001"))
        }
        assertFailsWith<IllegalArgumentException> {
            Ibz.crt(small, small, z("152:$POWER_150_PLUS_ONE"), z("152:$POWER_150"))
        }
    }

    @Test
    fun jacobiSymbolsNeedANonNegativeValueAndAnOddPositiveCoprimeModulus() {
        assertFailsWith<IllegalArgumentException> { z("3:-1").jacobi(z("3:3")) }
        assertFailsWith<IllegalArgumentException> { z("2:1").jacobi(z("4:4")) }
        assertFailsWith<IllegalArgumentException> { z("2:1").jacobi(z("3:-3")) }
        assertFailsWith<IllegalArgumentException> { z("4:6").jacobi(z("5:9")) }
    }

    @Test
    fun residueHelpersRejectNegativeInputs() {
        val one = Ibz.one(CAPACITY)

        assertFailsWith<IllegalArgumentException> { z("3:-1").sqrtFloor() }
        assertFailsWith<IllegalArgumentException> {
            Ibz.inverseMatrixMod2exp(one, one, one, one, -1)
        }
        assertFailsWith<IllegalArgumentException> { IbzModRing(LongArray(0)) }
        assertFailsWith<IllegalArgumentException> { IbzModRing(IbzNatural.of(4)) }
    }

    @Test
    fun modularPowersNeedReducedOperandsAndAnOddBoundedModulus() {
        val zero = Ibz.zero(CAPACITY)

        assertFailsWith<IllegalArgumentException> { z("3:-1").powMod(zero, z("4:7")) }
        assertFailsWith<IllegalArgumentException> { z("4:7").powMod(zero, z("4:7")) }
        assertFailsWith<IllegalArgumentException> { zero.powMod(z("3:-1"), z("4:7")) }
        assertFailsWith<IllegalArgumentException> { zero.powMod(z("4:7"), z("4:7")) }
        assertFailsWith<IllegalArgumentException> { zero.powMod(zero, z("4:4")) }
        assertFailsWith<IllegalArgumentException> { zero.powMod(zero, z("4:-7")) }
        assertFailsWith<IllegalArgumentException> { zero.powMod(zero, z("193:7")) }
    }

    @Test
    fun squareRootsNeedReducedValuesAndModuliOfTheRightResidue() {
        assertFailsWith<IllegalArgumentException> { z("3:-1").sqrtModPrime(z("4:7"), ::noDraws) }
        assertFailsWith<IllegalArgumentException> { z("4:7").sqrtModPrime(z("4:7"), ::noDraws) }
        assertFailsWith<IllegalArgumentException> { z("4:7").sqrtMinusOneMod(::noDraws) }
        assertFailsWith<IllegalArgumentException> { z("4:-5").sqrtMinusOneModVerified() }
        assertFailsWith<IllegalArgumentException> { z("4:7").sqrtMinusOneModVerified() }
        assertFailsWith<IllegalArgumentException> { z("254:5").sqrtMinusOneModVerified() }
    }

    @Test
    fun primalityRejectsValuesBeyondTheMontgomeryBounds() {
        assertFailsWith<IllegalArgumentException> { z("254:5").isProbablePrime(1, ::noDraws) }
        assertFailsWith<IllegalArgumentException> { z("200:5").isProbablePrime(1, ::noDraws) }
    }

    @Test
    fun bailliePswRejectsCompositesWithoutSmallFactors() {
        // 665281 = 577 * 1153 reaches 1 without -1; F5 = 641 * 6700417 is a base-2 strong
        // pseudoprime.
        assertFalse(z("21:a26c1").isProbablePrime(1, ::noDraws))
        assertFalse(z("34:100000001").isProbablePrime(1, ::noDraws))
    }

    @Test
    fun lucasRejectsValuesSharingAFactorWithTheDiscriminant() {
        assertFalse(z("7:23").isStrongLucasProbablePrime())
    }

    @Test
    fun extraRoundsStopAtTheFirstCompositenessWitness() {
        assertFalse(z("21:a26c1").passesRandomRounds(2) { ByteArray(it) })
    }

    private fun noDraws(length: Int): ByteArray = error("Unexpected draw of $length bytes")

    private fun z(text: String): Ibz = IbzRecord.decodeIbz(CAPACITY, text)

    private companion object {
        const val CAPACITY = 256
        const val POWER_150 = "40000000000000000000000000000000000000"
        const val POWER_150_PLUS_ONE = "40000000000000000000000000000000000001"
    }
}
