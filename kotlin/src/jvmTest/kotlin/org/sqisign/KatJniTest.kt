package org.sqisign

import kotlin.test.Test
import kotlin.test.assertEquals

/** JNI-only guard of the KAT bridge (native/src/sqisign_kat_jni.c); cinterop callers pass a fixed-size pointer. */
class KatJniTest {
    @Test
    fun seedOfWrongLengthIsRejected() {
        val seed = ByteArray(47)

        val result = SqiSignKatJni.keypairSign(0, seed, ByteArray(1), ByteArray(1), ByteArray(1), ByteArray(1))

        assertEquals(-1, result)
    }
}
