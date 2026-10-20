package org.sqisign

// Platform bridges to the stable C API in native/include/sqisign_kmp.h. Callers validate buffer sizes; every function
// returns the C status code (0 = success).

/** Writes the public key, secret key, and signature sizes reported by the C core for [set]. */
internal expect fun nativeSizes(set: Int, out: IntArray): Int

internal expect fun nativeKeypair(set: Int, pk: ByteArray, sk: ByteArray): Int

internal expect fun nativeSign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int

internal expect fun nativeVerify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int
