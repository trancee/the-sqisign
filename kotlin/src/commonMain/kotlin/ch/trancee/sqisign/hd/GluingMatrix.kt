// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.imaginaryUnit
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

private const val NO_SPECIAL_CASE = 255

/** Algorithm 4.115: `delta` and the translation actions `g1, g2, g3` as `(s00, s01, s10)`. */
private class SymmetricElement<Element : QuadraticFieldElement<Element>>(
    val delta: Element,
    val g1: List<Element>,
    val g2: List<Element>,
    val g3: List<Element>,
)

/**
 * Algorithm 4.116: the change of theta coordinates induced by the four-torsion couple points
 * [first] and [second], as rows of a 4x4 matrix. The special cases of algorithm 4.115 follow the
 * reference precedence: the last matching case wins.
 */
internal fun <Element : QuadraticFieldElement<Element>> gluingMatrix(
    field: QuadraticField<Element>,
    first: CouplePoint<Element>,
    second: CouplePoint<Element>,
): List<List<Element>> {
    val a = symmetricElement(field, first.first, second.first)
    val b = symmetricElement(field, first.second, second.second)
    val d12 = a.delta.multiply(b.delta)
    fun term(left: Int, right: Int): Element {
        val g1 = a.g1[left].multiply(b.g1[right])
        val g2 = a.g2[left].multiply(b.g2[right])
        return g1.add(g2).subtract(a.g3[left].multiply(b.g3[right]))
    }
    val row0 = listOf(d12.add(term(0, 0)), term(0, 1), term(1, 0), term(1, 1))
    val row1 = translate(b.g2, row0[0], row0[1]) + translate(b.g2, row0[2], row0[3])
    val row2 = translateColumns(a.g1, row0)
    val row3 = translateColumns(a.g1, row1)
    return listOf(
        row0.map { it.multiply(d12) },
        row1.map { it.multiply(a.delta) },
        row2.map { it.multiply(b.delta) },
        row3,
    )
}

/** The pair `(s00 u + s10 v, s01 u - s00 v)` for one translation action. */
private fun <Element : QuadraticFieldElement<Element>> translate(
    action: List<Element>,
    u: Element,
    v: Element,
): List<Element> =
    listOf(
        action[0].multiply(u).add(action[2].multiply(v)),
        action[1].multiply(u).subtract(action[0].multiply(v)),
    )

private fun <Element : QuadraticFieldElement<Element>> translateColumns(
    action: List<Element>,
    row: List<Element>,
): List<Element> {
    val (left, right) = row.chunked(2)
    val (first, second) = translate(action, left[0], right[0])
    val (third, fourth) = translate(action, left[1], right[1])
    return listOf(first, third, second, fourth)
}

private fun <Element : QuadraticFieldElement<Element>> symmetricElement(
    field: QuadraticField<Element>,
    first: MontgomeryPoint<Element>,
    second: MontgomeryPoint<Element>,
): SymmetricElement<Element> {
    val i = imaginaryUnit(field)
    val firstSum = first.x.add(first.z)
    val firstDifference = first.x.subtract(first.z)
    val secondSum = second.x.add(second.z)
    val secondDifference = second.x.subtract(second.z)
    val cross = i.multiply(firstSum.multiply(secondDifference))
    val other = secondSum.multiply(firstDifference)
    val tests =
        listOf(
            firstDifference,
            firstSum,
            secondDifference,
            secondSum,
            cross.add(other),
            cross.subtract(other),
        )
    val matching = tests.indexOfLast { it.isEqualTo(field.zero()) }
    val case = if (matching < 0) NO_SPECIAL_CASE else matching
    val selected = if (case shr 1 == 0) second else first
    val aa = selected.x.square()
    val bb = selected.z.square()
    val delta = aa.subtract(bb)
    val r1 = aa.add(bb)
    val r2 = selected.x.multiply(selected.z).let { it.add(it) }
    val signed = if (case and 1 == 1) delta else delta.negate()
    var g1 = listOf(r1.negate(), r2, r2.negate())
    var g2 = listOf(field.zero(), signed, signed)
    if (case shr 1 == 0) g1 = g2.also { g2 = g1 }
    val firstFlag = case in setOf(0, 3, 4)
    val secondFlag = case in setOf(1, 2, 5)
    fun rotate(value: Element, positive: Boolean): Element =
        value.multiply(if (positive) i else i.negate())
    var g3 = listOf(rotate(r2, firstFlag), rotate(r1, secondFlag), rotate(r1, firstFlag))
    if (case shr 1 == 2) g2 = g3.also { g3 = g2 }
    return SymmetricElement(delta, g1, g2, g3)
}
