// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.ec.BasisHint
import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.TorsionMatrix
import ch.trancee.sqisign.ec.TorsionScalar
import ch.trancee.sqisign.ec.twoIsogenyChain
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import ch.trancee.sqisign.hd.CoupleCurve
import ch.trancee.sqisign.hd.CouplePoint
import ch.trancee.sqisign.hd.ThetaChainCodomain
import ch.trancee.sqisign.hd.ThetaChainKernel
import ch.trancee.sqisign.hd.ThetaChainMode
import ch.trancee.sqisign.hd.thetaChain

/** Torsion beyond the isogeny lengths that every kernel and basis carries (`[4]` in 3.19-3.20). */
private const val EXTRA_TORSION = 2

/**
 * Algorithm 3.24 over [field]. Internal checks reject through [IllegalArgumentException] or a
 * rejected theta-chain outcome; both mean an invalid signature here.
 */
internal fun <Element : QuadraticFieldElement<Element>> verifySignature(
    field: QuadraticField<Element>,
    publicKey: EncodedPublicKey,
    message: ByteArray,
    signature: EncodedSignature,
): Boolean {
    val parameters = field.parameters
    val matrix = responseMatrix(signature)
    if (!matrix.isNormalized()) return false
    return try {
        val publicCurve = curve(field, publicKey.montgomeryCoefficient())
        val challengeLength = parameters.securityBits
        val publicBasis =
            TorsionBasis.fromHint(
                publicCurve,
                challengeLength + EXTRA_TORSION,
                BasisHint.fromEncoded(publicKey.hint.toByte()),
            )
        val kernel =
            publicCurve.threePointMultiply(
                publicBasis.first,
                publicBasis.second,
                publicBasis.difference,
                signature.challenge().copyOf(parameters.fieldBytes),
            )
        val challengeCurve = publicCurve.twoIsogenyChain(kernel, challengeLength)
        val auxiliaryCurve = curve(field, signature.auxiliaryMontgomeryCoefficient())
        val responseExponent = parameters.responseBits + EXTRA_TORSION
        val auxiliaryBasis =
            TorsionBasis.fromHint(
                auxiliaryCurve,
                responseExponent,
                BasisHint.fromEncoded(signature.auxiliaryHint.toByte()),
            )
        val challengeBasis =
            TorsionBasis.fromHint(
                    challengeCurve,
                    responseExponent,
                    BasisHint.fromEncoded(signature.challengeHint.toByte()),
                )
                .applyMatrix(challengeCurve, matrix)
        val outcome =
            thetaChain(
                parameters.responseBits,
                CoupleCurve(challengeCurve, auxiliaryCurve),
                ThetaChainKernel(
                    CouplePoint(challengeBasis.first, auxiliaryBasis.first),
                    CouplePoint(challengeBasis.second, auxiliaryBasis.second),
                ),
                emptyList(),
                ThetaChainMode.VERIFY,
            )
        val commitment = (outcome as? ThetaChainCodomain)?.first
        commitment != null &&
            ChallengeHash.compute(
                    publicKey,
                    commitment.curve.montgomeryCoefficient().toLittleEndian(),
                    message,
                )
                .contentEquals(signature.challenge())
    } catch (expectedRejection: IllegalArgumentException) {
        false
    }
}

private fun responseMatrix(signature: EncodedSignature): TorsionMatrix {
    val exponent = signature.parameters.responseBits + EXTRA_TORSION
    fun entry(row: Int, column: Int): TorsionScalar =
        TorsionScalar.fromLittleEndian(exponent, signature.responseEntry(row, column))
    return TorsionMatrix.of(entry(0, 0), entry(0, 1), entry(1, 0), entry(1, 1))
}

private fun TorsionMatrix.isNormalized(): Boolean =
    normalized().rowMajor().zip(rowMajor()).all { (normal, entry) -> normal.compareTo(entry) == 0 }

private fun <Element : QuadraticFieldElement<Element>> curve(
    field: QuadraticField<Element>,
    coefficient: ByteArray,
): MontgomeryCurve<Element> =
    MontgomeryCurve.fromCoefficients(field, field.fromLittleEndian(coefficient), field.one())
