package org.sqisign

/**
 * SQIsign key generation, signing, and verification backed by the reference C implementation.
 *
 * All functions are blocking and thread-safe. Key generation and signing take tens to hundreds of milliseconds, so
 * call them off the main/UI thread.
 */
public object SqiSign {
    private const val OK = 0

    /** Generates a key pair using the operating system's random number generator. */
    @Throws(SqiSignException::class)
    public fun generateKeyPair(parameterSet: ParameterSet = ParameterSet.P324_3): SqiSignKeyPair {
        val pk = ByteArray(parameterSet.publicKeyBytes)
        val sk = ByteArray(parameterSet.secretKeyBytes)
        try {
            check(nativeKeypair(parameterSet.nativeId, pk, sk), "key generation")
            return SqiSignKeyPair(SqiSignPublicKey(parameterSet, pk), SqiSignSecretKey(parameterSet, sk))
        } finally {
            sk.fill(0)
        }
    }

    /**
     * Returns a detached signature of [message], exactly [ParameterSet.signatureBytes] long.
     *
     * @throws IllegalStateException if [secretKey] has been closed.
     */
    @Throws(SqiSignException::class, IllegalStateException::class)
    public fun sign(message: ByteArray, secretKey: SqiSignSecretKey): ByteArray {
        val set = secretKey.parameterSet
        val signature = ByteArray(set.signatureBytes)
        check(nativeSign(set.nativeId, signature, message, secretKey.bytesForNative()), "signing")
        return signature
    }

    /** Returns `true` if [signature] is a valid signature of [message] under [publicKey]. */
    public fun verify(message: ByteArray, signature: ByteArray, publicKey: SqiSignPublicKey): Boolean {
        val set = publicKey.parameterSet
        if (signature.size != set.signatureBytes) return false
        return nativeVerify(set.nativeId, signature, message, publicKey.bytesForNative()) == OK
    }

    private fun check(result: Int, operation: String) {
        if (result != OK) throw SqiSignException("SQIsign $operation failed (code $result)")
    }
}
