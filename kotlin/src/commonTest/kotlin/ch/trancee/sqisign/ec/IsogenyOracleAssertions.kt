// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class IsogenyOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val normalizations: List<String>,
    private val fourIsogenies: List<String>,
    private val chains: List<String>,
) {
    internal fun verifyNormalizations(labels: Set<String>) {
        val selected = select(normalizations, labels)
        for (record in selected) {
            val curve = curve(record[1])
            val kernel = point(record[2], 3)

            if (record[3] == "-") {
                assertFailsWith<IllegalArgumentException>(record[0]) { curve.normalize(kernel) }
            } else {
                val actual = curve.normalize(kernel).montgomeryCoefficient()

                assertTrue(actual.isEqualTo(element(record[3])), "${field.parameters} $record")
            }
        }
    }

    internal fun verifyFourIsogenies() {
        for (record in fourIsogenies.map { it.split(':') }) {
            val isogeny = FourIsogeny.from(curve(record[0]), point(record[1], 3))

            val image = isogeny.evaluate(point(record[2], 5))

            assertTrue(
                isogeny.codomain.montgomeryCoefficient().isEqualTo(element(record[3])),
                "${field.parameters} codomain $record",
            )
            if (record[4] == "-") assertTrue(image.isInfinity(), "${field.parameters} $record")
            else
                assertTrue(
                    image.affineX()!!.isEqualTo(element(record[4])),
                    "${field.parameters} $record",
                )
        }
    }

    internal fun verifyFourIsogenyRejections() {
        for (record in
            select(normalizations, setOf("singular", "orderTwo", "orderEight", "infinity"))) {
            assertFailsWith<IllegalArgumentException>(record[0]) {
                FourIsogeny.from(curve(record[1]), point(record[2], 3))
            }
        }
    }

    internal fun verifyChains(labels: Set<String>, exponents: Set<Int>? = null) {
        val selected =
            select(chains, labels).filter { exponents == null || it[3].toInt() in exponents }
        assertTrue(selected.isNotEmpty(), "Chain oracle selection must not be empty")
        for (record in selected) {
            val curve = curve(record[1])
            val kernel = point(record[2], 5)
            val exponent = record[3].toInt()

            if (record[4] == "-") {
                assertFailsWith<IllegalArgumentException>(record[0]) {
                    curve.twoIsogenyChain(kernel, exponent)
                }
            } else {
                val actual = curve.twoIsogenyChain(kernel, exponent).montgomeryCoefficient()

                assertTrue(actual.isEqualTo(element(record[4])), "${field.parameters} $record")
            }
        }
    }

    internal fun verifyChainLengthDomain() {
        val record = select(chains, setOf("generic")).first { it[3] == "8" }
        val curve = curve(record[1])
        val kernel = point(record[2], 5)
        val maximum = field.parameters.torsionExponent

        for (exponent in listOf(-2, 0, 7, maximum + 2)) {
            assertFailsWith<IllegalArgumentException>("$exponent") {
                curve.twoIsogenyChain(kernel, exponent)
            }
        }
    }

    private fun select(records: List<String>, labels: Set<String>): List<List<String>> {
        val selected = records.map { it.split(':') }.filter { it[0] in labels }
        assertTrue(selected.isNotEmpty(), "Isogeny oracle selection must not be empty")
        return selected
    }

    private fun curve(encoded: String): MontgomeryCurve<Element> {
        val scale = small(7)
        return MontgomeryCurve.fromCoefficients(field, element(encoded).multiply(scale), scale)
    }

    private fun point(encoded: String, scale: Int): MontgomeryPoint<Element> =
        if (encoded == "-") MontgomeryPoint.infinity(field)
        else
            MontgomeryPoint.fromCoordinates(
                field,
                element(encoded).multiply(small(scale)),
                small(scale),
            )

    private fun element(encoded: String): Element = field.fromLittleEndian(encoded.hexToByteArray())

    private fun small(value: Int): Element =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also { it[0] = value.toByte() },
        )
}
