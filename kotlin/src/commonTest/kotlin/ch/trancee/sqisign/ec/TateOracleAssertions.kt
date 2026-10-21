// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertTrue

internal class TateOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    curves: List<String>,
    private val records: List<String>,
) {
    private val domains = curves.map { encoded -> lazy { prepare(encoded) } }

    internal fun verify(curveIndex: Int, exponent: Int, labels: Set<String>? = null) {
        val selected =
            records
                .map { it.split(':') }
                .filter {
                    it[0].toInt() == curveIndex &&
                        it[1].toInt() == exponent &&
                        (labels == null || it[2] in labels)
                }
        assertTrue(selected.isNotEmpty(), "Pairing oracle selection must not be empty")
        val pairing = domains[curveIndex].value
        for (record in selected) {
            val left = point(record[3], 2)
            val right = point(record[4], 3)
            val sum = point(record[5], 5)
            val expected = field.fromLittleEndian(record[6].hexToByteArray())

            val actual = pairing.evaluate(exponent, left, right, sum)

            assertTrue(
                actual.isEqualTo(expected),
                "${field.parameters}, curve $curveIndex, $exponent, ${record[2]}",
            )
        }
    }

    private fun prepare(encoded: String): TatePairing<Element> {
        val inputs = encoded.split(':')
        val coefficient = field.fromLittleEndian(inputs[0].hexToByteArray())
        val scale = small(7)
        val curve = MontgomeryCurve.fromCoefficients(field, coefficient.multiply(scale), scale)
        val basis =
            TorsionBasis.checked(
                curve,
                field.parameters.torsionExponent,
                point(inputs[1], 2),
                point(inputs[2], 3),
                point(inputs[3], 5),
            )
        return TatePairing.prepare(curve, basis)
    }

    private fun point(encoded: String, scale: Int): MontgomeryPoint<Element> =
        if (encoded == "-") MontgomeryPoint.infinity(field)
        else
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(encoded.hexToByteArray()).multiply(small(scale)),
                small(scale),
            )

    private fun small(value: Int): Element =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also { it[0] = value.toByte() },
        )
}
