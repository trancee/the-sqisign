package org.sqisign

import kotlin.test.Test
import kotlin.test.assertEquals

/** Calls the native bridge directly with inputs the public API never passes, so the C glue's guards are exercised. */
class NativeBridgeTest {
    private val unknownSet = ParameterSet.entries.size

    @Test
    fun sizesRejectUnknownParameterSet() {
        val result = nativeSizes(unknownSet, IntArray(3))

        assertEquals(INVALID_SET, result)
    }

    @Test
    fun keypairRejectsUnknownParameterSet() {
        val result = nativeKeypair(unknownSet, ByteArray(1), ByteArray(1))

        assertEquals(INVALID_SET, result)
    }

    @Test
    fun signRejectsUnknownParameterSet() {
        val result = nativeSign(unknownSet, ByteArray(1), ByteArray(1), ByteArray(1))

        assertEquals(INVALID_SET, result)
    }

    @Test
    fun verifyRejectsUnknownParameterSet() {
        val result = nativeVerify(unknownSet, ByteArray(1), ByteArray(1), ByteArray(1))

        assertEquals(INVALID_SET, result)
    }

    private companion object {
        /** `SQISIGN_KMP_INVALID_SET` in native/include/sqisign_kmp.h. */
        const val INVALID_SET = -2
    }
}
