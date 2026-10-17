// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.fp2_664NonlinearVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp2P664NonlinearTest {
    @Test fun nonlinearVector0MatchesIndependentPythonValues() = assertNonlinearVector(0)

    @Test fun nonlinearVector1MatchesIndependentPythonValues() = assertNonlinearVector(1)

    @Test fun nonlinearVector2MatchesIndependentPythonValues() = assertNonlinearVector(2)

    @Test fun nonlinearVector3MatchesIndependentPythonValues() = assertNonlinearVector(3)

    @Test fun nonlinearVector4MatchesIndependentPythonValues() = assertNonlinearVector(4)

    @Test fun nonlinearVector5MatchesIndependentPythonValues() = assertNonlinearVector(5)

    @Test fun nonlinearVector6MatchesIndependentPythonValues() = assertNonlinearVector(6)

    @Test fun nonlinearVector7MatchesIndependentPythonValues() = assertNonlinearVector(7)

    @Test fun nonlinearVector8MatchesIndependentPythonValues() = assertNonlinearVector(8)

    @Test fun nonlinearVector9MatchesIndependentPythonValues() = assertNonlinearVector(9)
}

private fun assertNonlinearVector(index: Int) {
    val fields = fp2_664NonlinearVectors[index].split(':')
    val value = Fp2P664.fromLittleEndian(decodeComponents(fields[0], fields[1]))

    if (fields[2] == "-") {
        assertFailsWith<IllegalArgumentException>("inverse $index") { value.inverse() }
    } else {
        assertContentEquals(
            decodeComponents(fields[2], fields[3]),
            value.inverse().toLittleEndian(),
            "inverse $index",
        )
    }
    if (fields[5] == "-") {
        assertEquals("0", fields[4], "square status $index")
        assertFalse(value.isSquare(), "square $index")
        assertNull(value.squareRoot(), "root $index")
    } else {
        assertEquals("1", fields[4], "square status $index")
        assertTrue(value.isSquare(), "square $index")
        val root = assertNotNull(value.squareRoot(), "root $index")
        assertContentEquals(
            decodeComponents(fields[5], fields[6]),
            root.toLittleEndian(),
            "root $index",
        )
        assertTrue(root.square().isEqualTo(value), "root verifies $index")
    }
}

private fun decodeComponents(real: String, imaginary: String): ByteArray =
    real.hexToByteArray() + imaginary.hexToByteArray()
