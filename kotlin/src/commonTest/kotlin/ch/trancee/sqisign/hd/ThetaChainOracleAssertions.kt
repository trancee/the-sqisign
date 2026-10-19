// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

internal class ThetaChainOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
) {
    internal fun verifyModels(records: List<String>) {
        for (record in records.map { it.split(':') }) {
            val model = thetaToMontgomery(field, element(record[0]), element(record[1]))

            if (record[2] == "-") {
                assertNull(model, "${field.parameters} $record")
            } else {
                val expected = record[2].split(',').map(::element)
                val actual = listOf(model!!.coefficient) + model.matrix
                assertTrue(
                    expected.zip(actual).all { (left, right) -> left.isEqualTo(right) },
                    "${field.parameters} $record",
                )
            }
        }
    }

    internal fun verifyMatrices(records: List<String>) {
        for (record in records.map { it.split(':') }) {
            val (k1, k2) =
                record.subList(1, 5).map(::affine).chunked(2) { (left, right) ->
                    CouplePoint(left, right)
                }

            val actual = gluingMatrix(field, k1, k2).flatten()

            val expected = record[5].split(',').map(::element)
            assertTrue(
                expected.size == actual.size &&
                    expected.zip(actual).all { (left, right) -> left.isEqualTo(right) },
                "${field.parameters} ${record[0]}",
            )
        }
    }

    internal fun verifySplits(records: List<String>) {
        for (record in records.map { it.split(':') }) {
            val points =
                record[2].split(',').map(::element).chunked(4).map {
                    ThetaPoint(it[0], it[1], it[2], it[3])
                }

            val outcome = ThetaChainOutcome.of {
                splitThetaProduct(field, points.first(), points.drop(1), mode(record[1]))
            }

            assertOutcome(record[3], outcome, "${record[0]}")
        }
    }

    internal fun verifyChains(records: List<String>, labels: Set<String>) {
        val selected = records.map { it.split(':') }.filter { it[0] in labels }
        assertTrue(selected.isNotEmpty(), "Theta chain selection must not be empty")
        for (record in selected) {
            val (first, second) = record.subList(3, 5).map(::curve)
            val kernel = record.subList(5, 9).map(::scaled)
            val points =
                record[9]
                    .split(';')
                    .filter { it.isNotEmpty() }
                    .map { pair ->
                        val (left, right) = pair.split('/').map(::scaled)
                        CouplePoint(left, right)
                    }

            val outcome =
                thetaChain(
                    record[2].toInt(),
                    CoupleCurve(first, second),
                    ThetaChainKernel(
                        CouplePoint(kernel[0], kernel[1]),
                        CouplePoint(kernel[2], kernel[3]),
                    ),
                    points,
                    mode(record[1]),
                )

            assertOutcome(record[10], outcome, "${record[0]} ${record[1]}")
        }
    }

    internal fun curve(encoded: String): MontgomeryCurve<Element> {
        val scale = small(7)
        return MontgomeryCurve.fromCoefficients(field, element(encoded).multiply(scale), scale)
    }

    internal fun scaled(encoded: String): MontgomeryPoint<Element> =
        if (encoded == "-") MontgomeryPoint.infinity(field)
        else MontgomeryPoint.fromCoordinates(field, element(encoded).multiply(small(3)), small(3))

    private fun small(value: Int): Element =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also { it[0] = value.toByte() },
        )

    internal fun assertOutcome(
        expected: String,
        outcome: ThetaChainOutcome<Element>,
        label: String,
    ) {
        val message = "${field.parameters} $label $expected"
        if (expected.contains(',')) {
            val codomain = codomain(outcome, message)
            val values = expected.split(',')
            val sides = listOf(codomain.first, codomain.second)
            for ((side, value) in sides.zip(values)) {
                matches(value, side?.curve?.montgomeryCoefficient(), message)
            }
            val images = values.drop(2).chunked(2)
            for ((index, pair) in images.withIndex()) {
                for ((side, value) in sides.zip(pair)) {
                    matches(value, side?.images?.get(index)?.affineX(), message)
                }
            }
        } else {
            assertEquals(expected, (outcome as? ThetaChainRejected)?.failure?.reason, message)
        }
    }

    internal fun codomain(
        outcome: ThetaChainOutcome<Element>,
        message: String,
    ): ThetaChainCodomain<Element> =
        when (outcome) {
            is ThetaChainCodomain -> outcome
            is ThetaChainRejected -> fail("$message: ${outcome.failure}")
        }

    private fun matches(expected: String, actual: Element?, message: String) {
        if (expected == "-") assertNull(actual, message)
        else assertTrue(actual!!.isEqualTo(element(expected)), message)
    }

    internal fun mode(encoded: String): ThetaChainMode =
        ThetaChainMode.entries.single { it.reference == encoded.toInt() }

    internal fun affine(encoded: String): MontgomeryPoint<Element> =
        if (encoded == "-") MontgomeryPoint.infinity(field)
        else MontgomeryPoint.fromCoordinates(field, element(encoded), field.one())

    internal fun element(encoded: String): Element =
        field.fromLittleEndian(encoded.hexToByteArray())
}
