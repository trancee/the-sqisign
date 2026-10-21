package org.sqisign

/**
 * Seeds the test-only native library with the NIST CTR-DRBG, then runs keygen and sign; returns 0 on success. The
 * library is built with `-DSQISIGN_KMP_KAT=ON` and never ships in the published artifacts.
 *
 * The KAT test in src/katTest also runs in the Android device tests, which define this function without expect/actual.
 */
internal expect fun nativeKatKeypairSign(
    set: Int,
    seed: ByteArray,
    msg: ByteArray,
    pk: ByteArray,
    sk: ByteArray,
    sig: ByteArray,
): Int
