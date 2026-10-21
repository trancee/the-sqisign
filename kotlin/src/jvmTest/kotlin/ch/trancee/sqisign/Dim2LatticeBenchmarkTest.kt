// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.Dim2LatticeBenchmark
import ch.trancee.sqisign.quaternion.ModularInverse2x2
import ch.trancee.sqisign.quaternion.PrimitiveElement
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Dim2LatticeBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            val benchmark = Dim2LatticeBenchmark()
            benchmark.parameterSet = parameterSet
            benchmark.prepare()
            val a = benchmark.first.map(::big)
            val b = benchmark.second.map(::big)

            val gcd = assertIs<List<*>>(benchmark.gaussianGcd()).map { big(assertIs<Ibz>(it)) }
            val primitive = assertIs<PrimitiveElement>(benchmark.makePrimitive())
            val inverse = assertIs<ModularInverse2x2>(benchmark.inverse2x2Mod())

            assertEquals(norm(euclid(a, b)), norm(gcd), "$parameterSet gcd norm")
            assertTrue(divides(gcd, a) && divides(gcd, b), "$parameterSet gcd divides")
            val coordinates = primitive.coordinates.map(::big)
            val content = big(primitive.content)
            val basis = benchmark.orderBasis.map { row -> row.map(::big) }
            val rebuilt = basis.map { row ->
                row.indices.sumOf { j -> row[j] * coordinates[j] } * content
            }
            assertEquals(benchmark.element.coordinates.map(::big), rebuilt, "$parameterSet element")
            assertEquals(
                BigInteger.ONE,
                coordinates.fold(BigInteger.ZERO, BigInteger::gcd),
                "$parameterSet primitive",
            )
            val modulus = big(benchmark.modulus)
            val m = benchmark.matrix.map { row -> row.map(::big) }
            val n = inverse.inverse.map { row -> row.map(::big) }
            val identity =
                List(2) { i -> List(2) { j -> if (i == j) BigInteger.ONE else BigInteger.ZERO } }
            val product =
                List(2) { i ->
                    List(2) { j -> (m[i][0] * n[0][j] + m[i][1] * n[1][j]).mod(modulus) }
                }
            assertTrue(inverse.exists, "$parameterSet invertible")
            assertEquals(identity, product, "$parameterSet inverse")
        }
    }

    private fun times(x: List<BigInteger>, y: List<BigInteger>) =
        listOf(x[0] * y[0] - x[1] * y[1], x[0] * y[1] + x[1] * y[0])

    private fun norm(x: List<BigInteger>): BigInteger = x[0] * x[0] + x[1] * x[1]

    private fun divides(d: List<BigInteger>, x: List<BigInteger>): Boolean {
        val numerator = times(x, listOf(d[0], -d[1]))
        return numerator.all { it.mod(norm(d)).signum() == 0 }
    }

    private fun euclid(x: List<BigInteger>, y: List<BigInteger>): List<BigInteger> {
        var q = x
        var r = y
        while (r.any { it.signum() != 0 }) {
            val numerator = times(q, listOf(r[0], -r[1]))
            val n = norm(r)
            val quotient = numerator.map { floor(it.shiftLeft(1) + n, n.shiftLeft(1)) }
            val product = times(quotient, r)
            val remainder = listOf(q[0] - product[0], q[1] - product[1])
            q = r
            r = remainder
        }
        return q
    }

    private fun floor(x: BigInteger, y: BigInteger): BigInteger {
        val (q, r) = x.divideAndRemainder(y)
        return if (r.signum() < 0) q - BigInteger.ONE else q
    }

    private fun big(value: Ibz): BigInteger = BigInteger(value.toString(16), 16)
}
