// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class Dim2LatticeOracleTest {
    @Test
    fun matrixEvaluationMatchesTheReference() =
        each("m2eval") { record -> text(record.matrix(0).evaluate2x2(record.pair(MATRIX))) }

    @Test
    fun matrixProductMatchesTheReference() =
        each("m2mul") { record ->
            text(record.matrix(0).times2x2(record.matrix(MATRIX)).flatten())
        }

    @Test
    fun adjugateMatchesTheReference() =
        each("m2inv") { record ->
            val result = record.matrix(0).adjugate2x2()
            val adjugate = result.adjugate
            if (adjugate == null) "0 ${encode(result.determinant)}"
            else "1 ${encode(result.determinant)} ${text(adjugate.flatten())}"
        }

    @Test
    fun modularInverseMatchesTheReference() =
        each("m2invmod") { record ->
            val result = record.matrix(0).inverse2x2Mod(record.ibz(MATRIX))
            "${if (result.exists) 1 else 0} ${text(result.inverse.flatten())}"
        }

    @Test
    fun normalizationMatchesTheReference() =
        each("m2norm") { record ->
            text(record.matrix(1).normalized2x2(record.int(0)).flatten())
        }

    @Test
    fun gaussianProductMatchesTheReference() =
        each("gmul") { record -> text(gaussianProduct(record.pair(0), record.pair(PAIR))) }

    @Test
    fun gaussianGcdMatchesTheReference() =
        each("ggcd") { record -> text(gaussianGcd(record.pair(0), record.pair(PAIR))) }

    @Test
    fun latticeCoordinatesMatchTheReference() =
        each("lcontains") { record ->
            val result = record.lattice().coordinatesOf(record.element(LATTICE))
            "${if (result.isMember) 1 else 0} ${text(result.coordinates)}"
        }

    @Test
    fun primitiveElementsMatchTheReference() =
        each("primitive") { record ->
            val result = record.element(LATTICE).primitiveIn(record.lattice())
            "${encode(result.content)} ${text(result.coordinates)}"
        }

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, Dim2LatticeVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private fun IbzRecord.pair(first: Int): List<Ibz> = listOf(ibz(first), ibz(first + 1))

    private fun IbzRecord.matrix(first: Int): List<List<Ibz>> =
        listOf(pair(first), pair(first + PAIR))

    private fun IbzRecord.vector(first: Int): List<Ibz> = (0 until VECTOR).map { ibz(first + it) }

    private fun IbzRecord.lattice(): QuaternionLattice =
        QuaternionLattice(ibz(0), (0 until VECTOR).map { vector(1 + VECTOR * it) })

    private fun IbzRecord.element(first: Int): QuaternionElement =
        QuaternionElement(ibz(first), vector(first + 1))

    private fun text(values: List<Ibz>): String = values.joinToString(" ") { encode(it) }

    private companion object {
        const val PAIR = 2
        const val MATRIX = 4
        const val VECTOR = 4
        const val LATTICE = 17
    }
}
