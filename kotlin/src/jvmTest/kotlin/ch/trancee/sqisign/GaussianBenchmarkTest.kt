// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.GaussianBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GaussianBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = GaussianBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val hnf = benchmark.hnf.map { row -> row.map(::big) }
            // The reference weights the second coordinate by floor(sqrt(p))^2, not p.
            val weight = big(benchmark.prime).sqrt().pow(2)
            val basis =
                assertIs<List<*>>(benchmark.reduceO0Ideal()).map { row ->
                    assertIs<List<*>>(row).map { big(assertIs<Ibz>(it)) }
                }

            val label = parameterSet
            assertTrue(isIntegral(solveUpper(hnf, basis)), "$label integral transform")
            assertEquals(determinant(hnf).abs(), determinant(basis).abs(), "$label unimodular")
            val v1 = gaussianColumn(basis, 0)
            val v2 = gaussianColumn(basis, 2)
            assertEquals(v1.timesI(), gaussianColumn(basis, 1), "$label i v1")
            assertEquals(v2.timesI(), gaussianColumn(basis, 3), "$label i v2")
            assertTrue(v1.first.re.signum() >= 0 && v1.first.im.signum() >= 0, "$label key")
            val q1 = v1.norm(weight)
            val q2 = v2.norm(weight)
            assertTrue(q1 <= q2, "$label ordered")
            val h = hermitian(v1, v2, weight)
            // Truncated quotients: the neighbourhood below covers the rounded Gaussian quotient.
            val centre = Gaussian(h.re / q1, h.im / q1)
            for (dr in -1..1) {
                for (di in -1..1) {
                    val mu = Gaussian(centre.re + dr.big, centre.im + di.big)
                    val shorter = v2.minus(v1.times(mu)).norm(weight)
                    assertTrue(shorter >= q2 - q2.shiftRight(WINDOW_SLACK), "$label $dr $di")
                }
            }
        }
    }

    private class Gaussian(val re: BigInteger, val im: BigInteger) {
        operator fun times(o: Gaussian) = Gaussian(re * o.re - im * o.im, re * o.im + im * o.re)

        operator fun minus(o: Gaussian) = Gaussian(re - o.re, im - o.im)

        fun conjugate() = Gaussian(re, -im)

        fun abs2(): BigInteger = re * re + im * im

        override fun equals(other: Any?) = other is Gaussian && re == other.re && im == other.im

        override fun hashCode() = re.hashCode() * 31 + im.hashCode()
    }

    private data class Vector(val first: Gaussian, val second: Gaussian) {
        fun timesI() = times(Gaussian(BigInteger.ZERO, BigInteger.ONE))

        fun times(mu: Gaussian) = Vector(first * mu, second * mu)

        fun minus(o: Vector) = Vector(first - o.first, second - o.second)

        fun norm(weight: BigInteger): BigInteger = first.abs2() + weight * second.abs2()
    }

    private fun gaussianColumn(basis: List<List<BigInteger>>, c: Int) =
        Vector(Gaussian(basis[0][c], basis[1][c]), Gaussian(basis[2][c], basis[3][c]))

    private fun hermitian(v: Vector, w: Vector, weight: BigInteger): Gaussian {
        val first = v.first.conjugate() * w.first
        val second = v.second.conjugate() * w.second
        return Gaussian(first.re + weight * second.re, first.im + weight * second.im)
    }

    /** `hnf^-1 * basis` by back substitution over the rationals, as numerator/denominator. */
    private fun solveUpper(
        hnf: List<List<BigInteger>>,
        basis: List<List<BigInteger>>,
    ): List<List<Pair<BigInteger, BigInteger>>> =
        List(DIMENSION) { c ->
            val x = Array(DIMENSION) { BigInteger.ZERO to BigInteger.ONE }
            for (r in DIMENSION - 1 downTo 0) {
                var numerator = basis[r][c]
                var denominator = BigInteger.ONE
                for (k in r + 1 until DIMENSION) {
                    val (n, d) = x[k]
                    numerator = numerator * d - hnf[r][k] * n * denominator
                    denominator *= d
                }
                x[r] = numerator to denominator * hnf[r][r]
            }
            x.toList()
        }

    private fun isIntegral(columns: List<List<Pair<BigInteger, BigInteger>>>): Boolean =
        columns.all { column ->
            column.all { (n, d) -> n.mod(d.abs()).signum() == 0 }
        }

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

    private val Int.big: BigInteger
        get() = BigInteger.valueOf(toLong())

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)

    private companion object {
        const val DIMENSION = 4

        /** The window precision of the second norm documented in ADR 0026. */
        const val WINDOW_SLACK = 64
    }
}
