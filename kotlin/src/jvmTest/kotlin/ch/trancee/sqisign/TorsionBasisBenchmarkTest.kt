// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.TorsionBasisBenchmark
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.e0BasisP324Vectors
import ch.trancee.sqisign.ec.e0BasisP500Vectors
import ch.trancee.sqisign.ec.e0BasisP664Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TorsionBasisBenchmarkTest {
    @Test
    fun unknownTorsionShapesAreRejectedBeforeTiming() {
        val benchmark = TorsionBasisBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParameterSetsAreRejectedBeforeTiming() {
        val benchmark = TorsionBasisBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    @Test
    fun canonicalE0WorkloadsMatchIndependentPythonAndPinnedC() {
        for (parameter in ParameterSet.entries) {
            val vectors =
                when (parameter) {
                    ParameterSet.NIST_I -> e0BasisP324Vectors
                    ParameterSet.NIST_III -> e0BasisP500Vectors
                    ParameterSet.NIST_V -> e0BasisP664Vectors
                }
            for ((shape, index) in listOf("TWO_TORSION" to 0, "FULL_TORSION" to 3)) {
                val expected = vectors[index].split(':')
                val benchmark =
                    TorsionBasisBenchmark().also {
                        it.parameterSet = parameter.name
                        it.torsionShape = shape
                        it.prepare()
                    }

                val actual = assertIs<TorsionBasis<*>>(benchmark.canonicalE0())

                assertEquals(expected[0].toInt(), actual.exponent)
                for ((point, encoded) in
                    listOf(actual.first, actual.second, actual.difference).zip(expected.drop(1))) {
                    assertContentEquals(
                        encoded.hexToByteArray(),
                        assertNotNull(point.affineX()).toLittleEndian(),
                        "${parameter.identifier} $shape",
                    )
                }
            }
        }
    }
}
