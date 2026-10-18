// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/** Typed arithmetic shared by the three existing, immutable extension-field representations. */
internal interface QuadraticFieldElement<Element : QuadraticFieldElement<Element>> {
    fun toLittleEndian(): ByteArray

    fun add(other: Element): Element

    fun subtract(other: Element): Element

    fun negate(): Element

    fun compareTo(other: Element): Int

    fun multiply(other: Element): Element

    fun square(): Element

    fun inverse(): Element

    fun isSquare(): Boolean

    fun squareRoot(): Element?

    fun isEqualTo(other: Element): Boolean
}

/** Field-specific construction and exact root choice; all operations remain variable-time. */
internal interface QuadraticField<Element : QuadraticFieldElement<Element>> {
    val parameters: ParameterSet

    fun zero(): Element

    fun one(): Element

    fun fromLittleEndian(encoded: ByteArray): Element

    fun conjugate(value: Element): Element

    /** Requires a zero imaginary component; this is not extension-field residuosity. */
    fun isSquareInBaseField(value: Element): Boolean

    fun squareRootWithReferenceSign(value: Element): Element?
}
