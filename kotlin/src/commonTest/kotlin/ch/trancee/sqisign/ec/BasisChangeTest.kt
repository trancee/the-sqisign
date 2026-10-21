// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasisChangeTest {
    @Test
    fun aFullReferenceRecoversIdentityAtExponentOne() {
        val target = reducedReference()
        val prepared = BasisChange.prepare(curve, full)

        val actual = prepared.between(target)

        assertEquals(
            listOf("01", "00", "00", "01"),
            actual.rowMajor().map { it.toLittleEndian().toHexString() },
        )
    }

    @Test
    fun preparationRejectsAReferenceBoundToAnotherCurve() {
        val other = MontgomeryCurve.fromCoefficients(field, field.one(), field.one())

        val failure = assertFailsWith<IllegalArgumentException> { BasisChange.prepare(other, full) }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    @Test
    fun aLowerOrderBasisCannotWitnessThePreparedPairingDomain() {
        val lower = reducedReference()

        val failure =
            assertFailsWith<IllegalArgumentException> { BasisChange.prepare(curve, lower) }

        assertEquals("Tate pairing requires a full parameter-order basis", failure.message)
    }

    @Test
    fun aTargetMustBelongToThePreparedCurve() {
        val encoded = PairingVectors.p324PairingCurves[1].split(':')
        val other =
            MontgomeryCurve.fromCoefficients(
                field,
                field.fromLittleEndian(encoded[0].hexToByteArray()),
                field.one(),
            )
        val points =
            encoded.drop(1).map {
                val point =
                    MontgomeryPoint.fromCoordinates(
                        field,
                        field.fromLittleEndian(it.hexToByteArray()),
                        field.one(),
                    )
                repeatedDouble(other, point, field.parameters.torsionExponent - 1)
            }
        val target = TorsionBasis.checked(other, 1, points[0], points[1], points[2])
        val prepared = BasisChange.prepare(curve, full)

        val failure = assertFailsWith<IllegalArgumentException> { prepared.between(target) }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    private fun reducedReference(): TorsionBasis<Fp2P324> =
        TorsionBasis.checked(
            curve,
            1,
            repeatedDouble(curve, full.first, field.parameters.torsionExponent - 1),
            repeatedDouble(curve, full.second, field.parameters.torsionExponent - 1),
            repeatedDouble(curve, full.difference, field.parameters.torsionExponent - 1),
        )

    private companion object {
        val field = Fp2P324
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val full by lazy {
            val encoded = PairingVectors.p324PairingCurves.first().split(':')
            val points =
                encoded.drop(1).map {
                    MontgomeryPoint.fromCoordinates(
                        field,
                        field.fromLittleEndian(it.hexToByteArray()),
                        field.one(),
                    )
                }
            TorsionBasis.checked(
                curve,
                field.parameters.torsionExponent,
                points[0],
                points[1],
                points[2],
            )
        }
    }
}
