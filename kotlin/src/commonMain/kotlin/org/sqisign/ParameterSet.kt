package org.sqisign

/**
 * SQIsign (NIST round 3) parameter sets.
 *
 * Sizes are in bytes and match the reference implementation's `api.h`.
 */
public enum class ParameterSet(
    /** Algorithm name as used by the NIST API, for example `SQIsign_p324_3`. */
    public val algorithmName: String,
    /** Claimed NIST security category. */
    public val nistLevel: Int,
    /** Length of an encoded public key. */
    public val publicKeyBytes: Int,
    /** Length of an encoded secret key. */
    public val secretKeyBytes: Int,
    /** Length of a detached signature. */
    public val signatureBytes: Int,
    internal val nativeId: Int,
) {
    P324_3("SQIsign_p324_3", 1, 83, 270, 200, 0),
    P500_27("SQIsign_p500_27", 3, 129, 417, 306, 1),
    P664_17("SQIsign_p664_17", 5, 169, 549, 406, 2),
}
