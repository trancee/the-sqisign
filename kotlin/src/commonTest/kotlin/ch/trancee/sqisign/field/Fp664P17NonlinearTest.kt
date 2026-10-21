// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.fp664_17NonlinearVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp664P17NonlinearTest {
    @Test fun nonlinearVector0MatchesIndependentPythonValues() = assertNonlinearVector(0)

    @Test fun nonlinearVector1MatchesIndependentPythonValues() = assertNonlinearVector(1)

    @Test fun nonlinearVector2MatchesIndependentPythonValues() = assertNonlinearVector(2)

    @Test fun nonlinearVector3MatchesIndependentPythonValues() = assertNonlinearVector(3)

    @Test fun nonlinearVector4MatchesIndependentPythonValues() = assertNonlinearVector(4)
}

private fun assertNonlinearVector(index: Int) {
    val fields = fp664_17NonlinearVectors[index].split(':')
    val value = Fp664P17.fromLittleEndian(fields[0].hexToByteArray())

    if (fields[1] == "-") {
        assertFailsWith<IllegalArgumentException>("inverse $index") { value.inverse() }
    } else {
        assertContentEquals(
            fields[1].hexToByteArray(),
            value.inverse().toLittleEndian(),
            "inverse $index",
        )
    }
    if (fields[3] == "-") {
        assertEquals("0", fields[2], "square status $index")
        assertFalse(value.isSquare(), "square $index")
        assertNull(value.squareRoot(), "root $index")
    } else {
        assertEquals("1", fields[2], "square status $index")
        assertTrue(value.isSquare(), "square $index")
        assertContentEquals(
            fields[3].hexToByteArray(),
            assertNotNull(value.squareRoot(), "root $index").toLittleEndian(),
            "root $index",
        )
    }
}
