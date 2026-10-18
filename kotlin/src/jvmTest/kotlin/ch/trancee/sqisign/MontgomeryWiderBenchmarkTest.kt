// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.MontgomeryBenchmark
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.curveP664Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MontgomeryWiderBenchmarkTest {
    @Test
    fun widestFullScalarWorkloadMatchesIndependentAffinePythonAndPinnedC() {
        val benchmark = MontgomeryBenchmark()
        benchmark.parameterSet = "NIST_V"
        benchmark.scalarShape = "FULL_WIDTH"
        benchmark.prepare()
        val expected = curveP664Vectors[13].split(':')[11].hexToByteArray()

        val actual = benchmark.multiply()

        val coordinate = assertNotNull(assertIs<MontgomeryPoint<*>>(actual).affineX())
        assertContentEquals(expected, coordinate.toLittleEndian())
    }
}
