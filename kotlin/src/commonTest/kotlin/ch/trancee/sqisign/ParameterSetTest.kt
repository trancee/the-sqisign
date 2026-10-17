// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import kotlin.test.Test
import kotlin.test.assertEquals

class ParameterSetTest {
    @Test
    fun metadataMatchesTheThreeSpecificationParameterSets() {
        val expected =
            listOf(
                listOf("p324_3", 128, 324, 3, 196, 41),
                listOf("p500_27", 192, 500, 27, 302, 64),
                listOf("p664_17", 256, 664, 17, 400, 84),
            )

        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            assertEquals(expected[index][0], parameters.identifier, "Identifier $index")
            assertEquals(expected[index][1], parameters.securityBits, "Security bits $index")
            assertEquals(expected[index][2], parameters.torsionExponent, "Torsion exponent $index")
            assertEquals(expected[index][3], parameters.cofactor, "Cofactor $index")
            assertEquals(expected[index][4], parameters.responseBits, "Response bits $index")
            assertEquals(expected[index][5], parameters.fieldBytes, "Field bytes $index")
        }
    }
}
