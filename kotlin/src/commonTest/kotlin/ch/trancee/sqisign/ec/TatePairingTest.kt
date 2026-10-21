// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TatePairingTest {
    @Test
    fun lowerOrderBasisDoesNotProveThePairingMaximalityPremise() {
        val field = Fp2P324
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val basis = TorsionBasis.canonicalE0(curve, 1)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                TatePairing.prepare(curve, basis)
            }

        assertEquals("Tate pairing requires a full parameter-order basis", failure.message)
    }

    @Test
    fun aFullBasisCannotWitnessAnotherCurve() {
        val other = MontgomeryCurve.fromCoefficients(field, field.one(), field.one())

        val failure =
            assertFailsWith<IllegalArgumentException> { TatePairing.prepare(other, basis) }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    @Test
    fun invalidExponentsFailBeforeOperandValidation() {
        for (exponent in listOf(-1, 0, field.parameters.torsionExponent + 1)) {
            assertRejected(
                exponent,
                point(1, 1),
                point(1, 1),
                point(1, 1),
                "Tate exponent must be between one and the parameter maximum",
            )
        }
    }

    @Test
    fun anOffCurveFirstOperandIsRejected() =
        assertRejected(
            1,
            point(1, 1),
            point(0),
            point(0),
            "Tate operands must lie on the curve",
        )

    @Test
    fun anOffCurveSecondOperandIsRejected() =
        assertRejected(
            1,
            point(0),
            point(1, 1),
            point(0),
            "Tate operands must lie on the curve",
        )

    @Test
    fun anOffCurveSumIsRejected() =
        assertRejected(
            1,
            point(0),
            point(0, 1),
            point(1, 1),
            "Tate operands must lie on the curve",
        )

    @Test
    fun firstOperandMustBeKilledByTheRequestedPower() =
        assertRejected(
            1,
            point(1),
            point(0, 1),
            point(0),
            "Tate first operand must be killed by the requested power of two",
        )

    @Test
    fun theFullWitnessCannotSupplyALowerOrderPremise() =
        assertRejected(
            1,
            basis.first,
            basis.second,
            basis.difference,
            "Tate first operand must be killed by the requested power of two",
        )

    @Test
    fun infiniteFirstOperandDoesNotExcuseAnInconsistentSum() =
        assertRejected(
            1,
            MontgomeryPoint.infinity(field),
            point(0, 1),
            point(0),
            "Tate operands must form a consistent sum or difference triple",
        )

    @Test
    fun infiniteSecondOperandDoesNotExcuseAnInconsistentSum() =
        assertRejected(
            1,
            point(0, 1),
            MontgomeryPoint.infinity(field),
            point(0),
            "Tate operands must form a consistent sum or difference triple",
        )

    @Test
    fun coincidentOperandsDoNotExcuseAnInconsistentSum() =
        assertRejected(
            1,
            point(0, 1),
            point(0, 1),
            point(0),
            "Tate operands must form a consistent sum or difference triple",
        )

    @Test
    fun distinctOperandsCannotHaveAnInfiniteSum() =
        assertRejected(
            1,
            point(0),
            point(0, 1),
            MontgomeryPoint.infinity(field),
            "Tate operands must form a consistent sum or difference triple",
        )

    @Test
    fun distinctOperandsRequireTheBiquadraticSumRelation() =
        assertRejected(
            1,
            point(0),
            point(0, 1),
            point(0),
            "Tate operands must form a consistent sum or difference triple",
        )

    @Test
    fun aConsistentDoubledPointAlsoGivesTheSelfPairingIdentity() {
        val first = point(1)

        val actual = pairing.evaluate(2, first, first, point(0))

        assertTrue(actual.isEqualTo(field.one()))
    }

    @Test
    fun supplyingTheWitnessDifferenceInvertsTheFullPairing() {
        val record =
            PairingVectors.p324TatePairings
                .map { it.split(':') }
                .single {
                    it[0] == "0" &&
                        it[1].toInt() == field.parameters.torsionExponent &&
                        it[2] == "generic"
                }
        val expected = field.conjugate(field.fromLittleEndian(record[6].hexToByteArray()))

        val actual = pairing.evaluate(basis.exponent, basis.first, basis.second, basis.difference)

        assertTrue(actual.isEqualTo(expected))
    }

    private fun assertRejected(
        exponent: Int,
        first: MontgomeryPoint<Fp2P324>,
        second: MontgomeryPoint<Fp2P324>,
        sum: MontgomeryPoint<Fp2P324>,
        reason: String,
    ) {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                pairing.evaluate(exponent, first, second, sum)
            }

        assertEquals(reason, failure.message)
    }

    private fun point(real: Int, imaginary: Int = 0): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(
            field,
            field.fromLittleEndian(
                ByteArray(field.parameters.extensionFieldBytes).also {
                    it[0] = real.toByte()
                    it[field.parameters.fieldBytes] = imaginary.toByte()
                },
            ),
            field.one(),
        )

    private companion object {
        val field = Fp2P324
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val basis by lazy { TorsionBasis.canonicalE0(curve, field.parameters.torsionExponent) }
        val pairing by lazy { TatePairing.prepare(curve, basis) }
    }
}
