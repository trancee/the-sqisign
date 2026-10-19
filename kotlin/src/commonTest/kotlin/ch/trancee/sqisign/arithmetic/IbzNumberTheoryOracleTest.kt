// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test
import kotlin.test.assertEquals

class IbzNumberTheoryOracleTest {
    @Test
    fun greatestCommonDivisorsKeepTheWiderOperandBound() =
        each("gcd") { encode(it.ibz(0).gcd(it.ibz(1))) }

    @Test
    fun bezoutCofactorsFollowTheEuclideanSequence() =
        each("xgcd") {
            val bezout = it.ibz(0).extendedGcd(it.ibz(1))
            "${encode(bezout.gcd)} ${encode(bezout.u)} ${encode(bezout.v)}"
        }

    @Test
    fun modularInversesReportExistenceAndKeepTheCandidate() =
        each("invmod") {
            val inverse = it.ibz(0).inverseMod(it.ibz(1))
            "${flag(inverse.exists)} ${encode(inverse.value)}"
        }

    @Test
    fun chineseRemaindersAreReducedModuloTheProduct() =
        each("crt") { encode(Ibz.crt(it.ibz(0), it.ibz(1), it.ibz(2), it.ibz(3))) }

    @Test
    fun matrixInversesModuloPowersOfTwoReinterpretWideEntries() =
        each("invmat") {
            val inverse =
                Ibz.inverseMatrixMod2exp(it.ibz(0), it.ibz(1), it.ibz(2), it.ibz(3), it.int(4))
            "${flag(inverse.exists)} ${inverse.entries.joinToString(" ") { entry -> encode(entry) }}"
        }

    @Test
    fun jacobiSymbolsMatchIndependentModel() =
        each("legendre") { it.ibz(0).jacobi(it.ibz(1)).toString() }

    @Test fun integerSquareRootsAreFloored() = each("sqrtfloor") { encode(it.ibz(0).sqrtFloor()) }

    @Test
    fun modularPowersUseTheModulusBound() =
        each("powmod") { encode(it.ibz(0).powMod(it.ibz(1), it.ibz(2))) }

    @Test
    fun modularSquareRootsDrawTheScriptedNonResidues() =
        each("sqrtp") {
            val source = ScriptedBytes(it.arguments[2])
            val root = it.ibz(0).sqrtModPrime(it.ibz(1), source::take)
            source.assertDrained()
            "${flag(root.found)} ${root.root?.let(::encode) ?: "-"}"
        }

    @Test
    fun squareRootsOfMinusOneConsumeTheScriptedDraws() =
        each("sqrtm1") {
            val source = ScriptedBytes(it.arguments[1])
            encode(it.ibz(0).sqrtMinusOneMod(source::take)).also { _ -> source.assertDrained() }
        }

    @Test
    fun verifiedSquareRootsOfMinusOneUseSmallJacobiBases() =
        each("sqrtm1v") {
            val root = it.ibz(0).sqrtMinusOneModVerified()
            "${flag(root != null)} ${root?.let(::encode) ?: "-"}"
        }

    @Test
    fun probablePrimesFollowBailliePswAndScriptedExtraRounds() =
        each("prime") {
            val source = ScriptedBytes(it.arguments[2])
            flag(it.ibz(0).isProbablePrime(it.int(1), source::take)).also { _ ->
                source.assertDrained()
            }
        }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, IbzNumberTheoryVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    private class ScriptedBytes(hex: String) {
        private val bytes = if (hex == "_") ByteArray(0) else hex.hexToByteArray()
        private var offset = 0

        fun take(length: Int): ByteArray =
            bytes.copyOfRange(offset, offset + length).also { offset += length }

        fun assertDrained() = assertEquals(bytes.size, offset)
    }
}
