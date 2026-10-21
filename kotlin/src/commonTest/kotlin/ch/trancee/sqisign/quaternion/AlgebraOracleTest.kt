// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class AlgebraOracleTest {
    @Test
    fun multiplicationMatchesTheReference() =
        each("qmul") { record ->
            val algebra = QuaternionAlgebra(record.ibz(0))
            text(algebra.multiply(record.element(1), record.element(1 + ELEMENT)))
        }

    @Test
    fun additionMatchesTheReference() =
        each("qadd") { record -> text(record.element(0) + record.element(ELEMENT)) }

    @Test
    fun reducedNormMatchesTheReference() =
        each("qnorm") { record ->
            val norm = QuaternionAlgebra(record.ibz(0)).norm(record.element(1))
            "${encode(norm.numerator)} ${encode(norm.denominator)}"
        }

    @Test
    fun normalizationMatchesTheReference() =
        each("qnormalize") { record -> text(record.element(0).normalized()) }

    @Test
    fun conjugationMatchesTheReference() =
        each("qconj") { record -> text(record.element(0).conjugate()) }

    @Test
    fun elementScalingMatchesTheReference() =
        each("qscale") { record -> text(record.element(1).scaledBy(record.ibz(0))) }

    @Test
    fun vectorContentMatchesTheReference() =
        each("vcontent") { record -> encode(record.vector(0).content()) }

    @Test
    fun vectorDivisionMatchesTheReference() =
        each("vdiv") { record -> text(record.vector(1).dividedBy(record.ibz(0))) }

    @Test
    fun vectorDifferenceMatchesTheReference() =
        each("vsub") { record -> text(record.vector(0).vectorMinus(record.vector(VECTOR))) }

    @Test
    fun matrixEvaluationMatchesTheReference() =
        each("meval") { record -> text(record.matrix(0).evaluate(record.vector(MATRIX))) }

    @Test
    fun transposedEvaluationMatchesTheReference() =
        each("mevalt") { record ->
            text(record.matrix(VECTOR).evaluateTransposed(record.vector(0)))
        }

    @Test
    fun quadraticFormMatchesTheReference() =
        each("qfeval") { record ->
            encode(record.matrix(0).quadraticForm(record.vector(MATRIX)))
        }

    @Test
    fun matrixGcdMatchesTheReference() =
        each("mgcd") { record -> encode(record.matrix(0).entryGcd()) }

    @Test
    fun matrixDivisionMatchesTheReference() =
        each("mdiv") { record ->
            text(record.matrix(1).matrixDividedBy(record.ibz(0)).flatten())
        }

    @Test
    fun matrixScalingMatchesTheReference() =
        each("mscale") { record ->
            text(record.matrix(1).matrixScaledBy(record.ibz(0)).flatten())
        }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, AlgebraVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private fun IbzRecord.vector(first: Int): List<Ibz> = (0 until VECTOR).map { ibz(first + it) }

    private fun IbzRecord.matrix(first: Int): List<List<Ibz>> =
        (0 until VECTOR).map { vector(first + VECTOR * it) }

    private fun IbzRecord.element(first: Int): QuaternionElement =
        QuaternionElement(ibz(first), vector(first + 1))

    private fun text(values: List<Ibz>): String = values.joinToString(" ") { encode(it) }

    private fun text(element: QuaternionElement): String =
        text(listOf(element.denominator) + element.coordinates)

    private companion object {
        const val VECTOR = 4
        const val ELEMENT = 5
        const val MATRIX = 16
    }
}
