// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThetaChainBoundaryTest {
    @Test
    fun lengthsOutsideTheReferenceRangeAreRejected() {
        val maximum = Fp2P324.parameters.torsionExponent - 2

        for (length in listOf(1, maximum + 1)) {
            val error = assertFailsWith<IllegalArgumentException> { chain(length = length) }

            assertEquals("Theta chain length must be in 2..$maximum", error.message)
        }
    }

    @Test
    fun kernelPointsAtInfinityOrTheOriginAreRejected() {
        val origin = MontgomeryPoint.fromCoordinates(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val infinity = MontgomeryPoint.infinity(Fp2P324)

        for (point in listOf(origin, infinity)) {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    chain(t1 = CouplePoint(point, kernel[1]))
                }

            assertEquals("Theta chain kernel points must be finite and not (0:1)", error.message)
        }
    }

    @Test
    fun pushedPointsWithoutAComponentAtInfinityAreRejected() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                chain(points = listOf(CouplePoint(kernel[0], kernel[1])))
            }

        assertEquals("Pushed couple points must have a component at infinity", error.message)
    }

    @Test
    fun gluingEvaluationRejectsANonzeroDualCoordinate() {
        val one = Fp2P324.one()
        val regular = ThetaPoint(one, one, one, Fp2P324.zero())
        val gluing = ThetaGluing.fromSquared(Fp2P324, identity(), regular, regular)

        val rejection =
            assertFailsWith<ThetaChainRejection> {
                gluing.evaluateSpecial(CouplePoint(kernel[0], kernel[3]))
            }

        assertEquals(ThetaChainFailure.GLUING_EVALUATION, rejection.failure)
    }

    @Test
    fun gluingRejectsSquaredThetaPointsWithAZeroFactor() {
        val zero = Fp2P324.zero()
        val one = Fp2P324.one()
        val regular = ThetaPoint(one, one, one, zero)

        val rejection =
            assertFailsWith<ThetaChainRejection> {
                ThetaGluing.fromSquared(
                    Fp2P324,
                    identity(),
                    regular,
                    ThetaPoint(zero, one, one, zero),
                )
            }

        assertEquals(ThetaChainFailure.GLUING_ZERO, rejection.failure)
    }

    @Test
    fun verifiedGenericStepsRejectAZeroSquaredCoordinate() {
        val zero = Fp2P324.zero()
        val one = Fp2P324.one()
        val regular = ThetaPoint(one, zero, zero, zero)

        val rejection =
            assertFailsWith<ThetaChainRejection> {
                ThetaIsogeny.compute(
                    Fp2P324,
                    regular,
                    ThetaPoint(one, one, zero, zero),
                    false,
                    true,
                )
            }

        assertEquals(ThetaChainFailure.GENERIC, rejection.failure)
    }

    private fun chain(
        length: Int = record[2].toInt(),
        t1: CouplePoint<Fp2P324> = CouplePoint(kernel[0], kernel[1]),
        points: List<CouplePoint<Fp2P324>> = emptyList(),
    ): ThetaChainOutcome<Fp2P324> =
        thetaChain(
            length,
            CoupleCurve(curves[0], curves[1]),
            ThetaChainKernel(t1, CouplePoint(kernel[2], kernel[3])),
            points,
            ThetaChainMode.BOTH,
        )

    private fun identity(): List<List<Fp2P324>> =
        List(4) { row ->
            List(4) { column -> if (row == column) Fp2P324.one() else Fp2P324.zero() }
        }

    private companion object {
        val oracle = ThetaChainOracleAssertions(Fp2P324)
        val record = ThetaChainVectors.p324Chains.map { it.split(':') }.first { it[0] == "kat0" }
        val curves = record.subList(3, 5).map(oracle::curve)
        val kernel = record.subList(5, 9).map(oracle::scaled)
    }
}
