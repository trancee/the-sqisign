// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CurveOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val records: List<String>,
) {
    internal fun doubling() = assertPoints(records.indices, 9) { it.curve.double(it.left) }

    internal fun addition() =
        assertPoints(records.indices, 10) {
            MontgomeryPoint.differentialAdd(it.left, it.right, it.difference)
        }

    internal fun coefficient() {
        for ((index, record) in records.withIndex()) {
            val case = CurveOracleCase(field, record)

            val actual = case.curve.montgomeryCoefficient()

            assertContentEquals(
                case.fields[12].hexToByteArray(),
                actual.toLittleEndian(),
                "${field.parameters.identifier} coefficient $index",
            )
        }
    }

    internal fun multiply(indices: IntRange) =
        assertPoints(indices, 11) { it.curve.multiply(it.left, it.scalarLeft) }

    internal fun threePoint(indices: IntRange) =
        assertPoints(indices, 13) {
            it.curve.threePointMultiply(it.left, it.right, it.difference, it.scalarLeft)
        }

    internal fun biscalar(indices: IntRange) =
        assertPoints(indices, 15) {
            it.curve.biscalarMultiply(
                it.left,
                it.right,
                it.difference,
                it.scalarLeft,
                it.scalarRight,
            )
        }

    internal fun difference() =
        assertPoints(records.indices, 16) { it.curve.deterministicDifference(it.left, it.right) }

    private fun assertPoints(
        indices: IntRange,
        expectedOffset: Int,
        operation: (CurveOracleCase<Element>) -> MontgomeryPoint<Element>,
    ) {
        for (index in indices) {
            val case = CurveOracleCase(field, records[index])
            val expected = case.fields[expectedOffset]
            val context = "${field.parameters.identifier} operation $expectedOffset, vector $index"

            val actual = operation(case)

            if (expected == "-") assertTrue(actual.isInfinity(), context)
            else {
                assertContentEquals(
                    expected.hexToByteArray(),
                    assertNotNull(actual.affineX(), context).toLittleEndian(),
                    context,
                )
            }
            case.assertInputsUnchanged(context)
        }
    }
}

private class CurveOracleCase<Element : QuadraticFieldElement<Element>>(
    field: QuadraticField<Element>,
    record: String,
) {
    val fields: List<String> = record.split(':')
    val curve: MontgomeryCurve<Element> =
        MontgomeryCurve.fromCoefficients(
            field,
            field.fromLittleEndian(fields[0].hexToByteArray()),
            field.fromLittleEndian(fields[1].hexToByteArray()),
        )
    val left: MontgomeryPoint<Element> = point(field, 2)
    val right: MontgomeryPoint<Element> = point(field, 4)
    val difference: MontgomeryPoint<Element> = point(field, 6)
    val scalarLeft: ByteArray = fields[8].hexToByteArray()
    val scalarRight: ByteArray = fields[14].hexToByteArray()

    fun assertInputsUnchanged(context: String) {
        for ((index, point) in listOf(left, right, difference).withIndex()) {
            assertContentEquals(
                fields[2 + 2 * index].hexToByteArray(),
                point.x.toLittleEndian(),
                context,
            )
            assertContentEquals(
                fields[3 + 2 * index].hexToByteArray(),
                point.z.toLittleEndian(),
                context,
            )
        }
        assertContentEquals(fields[8].hexToByteArray(), scalarLeft, context)
        assertContentEquals(fields[14].hexToByteArray(), scalarRight, context)
    }

    private fun point(field: QuadraticField<Element>, offset: Int): MontgomeryPoint<Element> =
        MontgomeryPoint.fromCoordinates(
            field,
            field.fromLittleEndian(fields[offset].hexToByteArray()),
            field.fromLittleEndian(fields[offset + 1].hexToByteArray()),
        )
}
