package org.sqisign

/** An encoded SQIsign public key. Instances are immutable; [encoded] returns a copy. */
public class SqiSignPublicKey(public val parameterSet: ParameterSet, encoded: ByteArray) {
    private val bytes: ByteArray = encoded.copyOf()

    init {
        require(bytes.size == parameterSet.publicKeyBytes) {
            "${parameterSet.algorithmName} public key must be ${parameterSet.publicKeyBytes} bytes, was ${bytes.size}"
        }
    }

    public val encoded: ByteArray get() = bytes.copyOf()

    internal fun bytesForNative(): ByteArray = bytes

    override fun equals(other: Any?): Boolean =
        other is SqiSignPublicKey && other.parameterSet == parameterSet && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * parameterSet.hashCode() + bytes.contentHashCode()

    override fun toString(): String = "SqiSignPublicKey(${parameterSet.algorithmName}, ${bytes.size} bytes)"
}

/**
 * An encoded SQIsign secret key.
 *
 * [close] overwrites the key material held by this instance. Copies returned by [encoded], and copies the runtime may
 * have made (garbage collector moves, JNI copies on the managed heap), cannot be reliably wiped.
 */
public class SqiSignSecretKey(public val parameterSet: ParameterSet, encoded: ByteArray) : AutoCloseable {
    private val bytes: ByteArray = encoded.copyOf()
    private var closed = false

    init {
        require(bytes.size == parameterSet.secretKeyBytes) {
            bytes.fill(0)
            "${parameterSet.algorithmName} secret key must be ${parameterSet.secretKeyBytes} bytes, was ${encoded.size}"
        }
    }

    /** Returns a copy of the encoded key. The caller is responsible for clearing it. */
    public val encoded: ByteArray get() = bytesForNative().copyOf()

    public val isClosed: Boolean get() = closed

    internal fun bytesForNative(): ByteArray {
        check(!closed) { "Secret key has been closed" }
        return bytes
    }

    override fun close() {
        bytes.fill(0)
        closed = true
    }

    override fun toString(): String = "SqiSignSecretKey(${parameterSet.algorithmName}, <redacted>)"
}

public class SqiSignKeyPair(public val publicKey: SqiSignPublicKey, public val secretKey: SqiSignSecretKey) {
    init {
        require(publicKey.parameterSet == secretKey.parameterSet) { "Key parameter sets differ" }
    }

    public val parameterSet: ParameterSet get() = publicKey.parameterSet

    override fun toString(): String = "SqiSignKeyPair($publicKey, $secretKey)"
}

/** Thrown when the native SQIsign core reports a failure. */
public class SqiSignException(message: String) : RuntimeException(message)
