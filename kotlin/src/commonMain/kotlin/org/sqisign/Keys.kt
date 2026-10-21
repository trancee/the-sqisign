package org.sqisign

/**
 * An encoded SQIsign public key. Instances are immutable; [encoded] returns a copy.
 *
 * @throws IllegalArgumentException if `encoded` is not [ParameterSet.publicKeyBytes] long.
 */
public class SqiSignPublicKey
@Throws(IllegalArgumentException::class)
constructor(
    /** The parameter set this key belongs to. */
    public val parameterSet: ParameterSet,
    encoded: ByteArray,
) {
    private val bytes: ByteArray = encoded.copyOf()

    init {
        require(bytes.size == parameterSet.publicKeyBytes) {
            "${parameterSet.algorithmName} public key must be ${parameterSet.publicKeyBytes} bytes, was ${bytes.size}"
        }
    }

    /** Returns a copy of the encoded key. */
    public val encoded: ByteArray get() = bytes.copyOf()

    internal fun bytesForNative(): ByteArray = bytes

    /** Keys are equal when their parameter sets and encodings are equal. */
    override fun equals(other: Any?): Boolean =
        other is SqiSignPublicKey && other.parameterSet == parameterSet && other.bytes.contentEquals(bytes)

    /** Consistent with [equals]. */
    override fun hashCode(): Int = 31 * parameterSet.hashCode() + bytes.contentHashCode()

    /** Returns the algorithm name and key size, not the key bytes. */
    override fun toString(): String = "SqiSignPublicKey(${parameterSet.algorithmName}, ${bytes.size} bytes)"
}

/**
 * An encoded SQIsign secret key.
 *
 * [close] overwrites the key material held by this instance. Copies returned by [encoded], and copies the runtime may
 * have made (garbage collector moves, JNI copies on the managed heap), cannot be reliably wiped.
 *
 * @throws IllegalArgumentException if `encoded` is not [ParameterSet.secretKeyBytes] long.
 */
public class SqiSignSecretKey
@Throws(IllegalArgumentException::class)
constructor(
    /** The parameter set this key belongs to. */
    public val parameterSet: ParameterSet,
    encoded: ByteArray,
) : AutoCloseable {
    private val bytes: ByteArray = encoded.copyOf()
    private var closed = false

    init {
        require(bytes.size == parameterSet.secretKeyBytes) {
            bytes.fill(0)
            "${parameterSet.algorithmName} secret key must be ${parameterSet.secretKeyBytes} bytes, was ${encoded.size}"
        }
    }

    /**
     * Returns a copy of the encoded key. The caller is responsible for clearing it.
     *
     * @throws IllegalStateException if the key has been closed.
     */
    public val encoded: ByteArray get() = bytesForNative().copyOf()

    /** Whether [close] has been called. */
    public val isClosed: Boolean get() = closed

    internal fun bytesForNative(): ByteArray {
        check(!closed) { "Secret key has been closed" }
        return bytes
    }

    /** Overwrites the key bytes held by this instance with zeros. Later use throws [IllegalStateException]. */
    override fun close() {
        bytes.fill(0)
        closed = true
    }

    /** Returns the algorithm name only; the key bytes are redacted. */
    override fun toString(): String = "SqiSignSecretKey(${parameterSet.algorithmName}, <redacted>)"
}

/**
 * A public key and its secret key.
 *
 * @throws IllegalArgumentException if the two keys belong to different parameter sets.
 */
public class SqiSignKeyPair
@Throws(IllegalArgumentException::class)
constructor(
    /** The public (verification) key. */
    public val publicKey: SqiSignPublicKey,
    /** The secret (signing) key. */
    public val secretKey: SqiSignSecretKey,
) {
    init {
        require(publicKey.parameterSet == secretKey.parameterSet) { "Key parameter sets differ" }
    }

    /** The parameter set shared by both keys. */
    public val parameterSet: ParameterSet get() = publicKey.parameterSet

    /** Describes both keys without revealing the secret key. */
    override fun toString(): String = "SqiSignKeyPair($publicKey, $secretKey)"
}

/** Thrown when the native SQIsign core reports a failure. */
public class SqiSignException(message: String) : RuntimeException(message)
