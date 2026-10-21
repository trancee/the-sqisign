// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.DualBenchmark
import ch.trancee.sqisign.quaternion.DualReduction
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DualBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = DualBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val hnf = benchmark.hnf.map { row -> row.map(::big) }
            val p = big(benchmark.prime)
            val security = ParameterSet.valueOf(parameterSet).securityBits

            val reduction = assertIs<DualReduction>(benchmark.dualReduceIdeal())

            val label = parameterSet
            val transform = reduction.transform.map { row -> row.map(::big) }
            val basis = reduction.basis.map { row -> row.map(::big) }
            assertEquals(BigInteger.ONE, determinant(transform).abs(), label)
            assertEquals(product(hnf, transform), basis, label)
            val weights = listOf(BigInteger.ONE, BigInteger.ONE, p, p)
            val gram =
                List(DIMENSION) { i ->
                    List(DIMENSION) { j ->
                        (0 until DIMENSION).sumOf { m -> basis[m][i] * weights[m] * basis[m][j] }
                    }
                }
            val det = determinant(gram)
            val n = hnf[0][0].shiftRight(1)
            val diagonal = reduction.gramDiagonal.map(::big)
            for (i in 0 until DIMENSION) {
                val exact = n * n * p * FOUR * cofactor(gram, i) // times det
                val error = (exact - diagonal[i] * det).abs()
                assertTrue(error.shiftLeft(security) <= exact, "$label diagonal $i")
            }
            assertTrue((1 until DIMENSION).all { diagonal[it] <= diagonal[it - 1] }, label)
        }
    }

    private fun product(
        left: List<List<BigInteger>>,
        right: List<List<BigInteger>>,
    ): List<List<BigInteger>> =
        List(DIMENSION) { r ->
            List(DIMENSION) { c -> (0 until DIMENSION).sumOf { m -> left[r][m] * right[m][c] } }
        }

    private fun cofactor(matrix: List<List<BigInteger>>, index: Int): BigInteger =
        determinant(
            matrix
                .filterIndexed { r, _ -> r != index }
                .map { it.filterIndexed { c, _ -> c != index } },
        )

    private fun determinant(matrix: List<List<BigInteger>>): BigInteger =
        if (matrix.size == 1) {
            matrix[0][0]
        } else {
            matrix[0].indices.sumOf { c ->
                val sign = if (c % 2 == 0) BigInteger.ONE else BigInteger.ONE.negate()
                val minor = matrix.drop(1).map { it.filterIndexed { column, _ -> column != c } }
                sign * matrix[0][c] * determinant(minor)
            }
        }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        const val DIMENSION = 4
        val FOUR: BigInteger = BigInteger.valueOf(4)
    }
}
