// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.QuaternionAlgebraBenchmark
import ch.trancee.sqisign.quaternion.QuaternionElement
import ch.trancee.sqisign.quaternion.QuaternionNorm
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QuaternionAlgebraBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = QuaternionAlgebraBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val p = big(benchmark.prime)
            val a = benchmark.left.coordinates.map(::big)
            val b = benchmark.right.coordinates.map(::big)
            val d = big(benchmark.left.denominator)

            val product = assertIs<QuaternionElement>(benchmark.multiply())
            val norm = assertIs<QuaternionNorm>(benchmark.norm())
            val form = big(assertIs<Ibz>(benchmark.quadraticForm()))

            assertEquals(
                d * big(benchmark.right.denominator),
                big(product.denominator),
                "$parameterSet denominator",
            )
            assertEquals(hamilton(a, b, p), product.coordinates.map(::big), "$parameterSet product")
            val exact = a[0] * a[0] + a[1] * a[1] + p * (a[2] * a[2] + a[3] * a[3])
            val numerator = big(norm.numerator)
            val denominator = big(norm.denominator)
            assertEquals(exact * denominator, numerator * d * d, "$parameterSet norm value")
            assertEquals(BigInteger.ONE, numerator.gcd(denominator), "$parameterSet lowest terms")
            assertEquals(1, denominator.signum(), "$parameterSet positive denominator")
            val gram = benchmark.gram.map { row -> row.map(::big) }
            val x = benchmark.vector.map(::big)
            val expected =
                x.indices.sumOf { i -> x.indices.sumOf { j -> x[i] * gram[i][j] * x[j] } }
            assertEquals(expected, form, "$parameterSet quadratic form")
        }
    }

    /** `(a0 + a1 i + a2 j + a3 ij)(b0 + b1 i + b2 j + b3 ij)` with `i^2 = -1`, `j^2 = -p`. */
    private fun hamilton(a: List<BigInteger>, b: List<BigInteger>, p: BigInteger) =
        listOf(
            a[0] * b[0] - a[1] * b[1] - p * (a[2] * b[2] + a[3] * b[3]),
            a[0] * b[1] + a[1] * b[0] + p * (a[2] * b[3] - a[3] * b[2]),
            a[0] * b[2] + a[2] * b[0] - a[1] * b[3] + a[3] * b[1],
            a[0] * b[3] + a[3] * b[0] + a[1] * b[2] - a[2] * b[1],
        )

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)
}
