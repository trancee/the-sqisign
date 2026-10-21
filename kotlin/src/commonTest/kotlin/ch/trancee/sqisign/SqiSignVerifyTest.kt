// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.ec.TorsionScalar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqiSignVerifyTest {
    @Test
    fun publicKatRecordZeroVerifies() {
        val record = KatRecordZero(ParameterSet.NIST_I)

        val valid = record.verify()

        assertTrue(valid)
    }

    @Test
    fun keyAndSignatureFromDifferentParameterSetsAreAProgrammingError() {
        val levelOne = KatRecordZero(ParameterSet.NIST_I)
        val levelThree = KatRecordZero(ParameterSet.NIST_III)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                SqiSign.verify(
                    levelOne.publicKey(),
                    levelOne.message,
                    WireFormat.signature(ParameterSet.NIST_III, levelThree.signature),
                )
            }

        assertEquals("Public key and signature must use the same parameter set", failure.message)
    }

    @Test
    fun aChangedMessageIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        record.message[0] = (record.message[0].toInt() xor 1).toByte()

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aChangedChallengeIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        val challengeStart =
            record.parameters.extensionFieldBytes + 4 * record.parameters.responseBytes
        record.signature[challengeStart] = (record.signature[challengeStart].toInt() xor 1).toByte()

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aNegatedResponseMatrixIsRejectedAsUnnormalized() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        val parameters = record.parameters
        for (entry in 0 until 4) {
            val start = parameters.extensionFieldBytes + entry * parameters.responseBytes
            val end = start + parameters.responseBytes
            val negated =
                TorsionScalar.fromLittleEndian(
                        parameters.responseBits + 2,
                        record.signature.copyOfRange(start, end),
                    )
                    .negate()
                    .toLittleEndian()
            negated.copyInto(record.signature, start)
        }

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aNonIsotropicKernelIsRejectedByTheThetaChain() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        val parameters = record.parameters
        val start = parameters.extensionFieldBytes + 3 * parameters.responseBytes
        val bottomRight =
            TorsionScalar.fromLittleEndian(
                parameters.responseBits + 2,
                record.signature.copyOfRange(start, start + parameters.responseBytes),
            )
        val four =
            TorsionScalar.fromLittleEndian(
                parameters.responseBits + 2,
                ByteArray(parameters.responseBytes) { if (it == 0) 4 else 0 },
            )
        bottomRight.add(four).toLittleEndian().copyInto(record.signature, start)

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aZeroResponseMatrixIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        val parameters = record.parameters
        record.signature.fill(
            0,
            parameters.extensionFieldBytes,
            parameters.extensionFieldBytes + 4 * parameters.responseBytes,
        )

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aSingularAuxiliaryCurveIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        record.signature.fill(0, 0, record.parameters.extensionFieldBytes)
        record.signature[0] = 2

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun theAuxiliaryCurveReplacedByThePublicCurveIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        val publicCoefficient = record.publicKeyBytes.copyOf(record.parameters.extensionFieldBytes)
        publicCoefficient.copyInto(record.signature)

        val valid = record.verify()

        assertFalse(valid)
    }

    @Test
    fun aSingularPublicKeyCurveIsRejected() {
        val record = KatRecordZero(ParameterSet.NIST_I)
        record.publicKeyBytes.fill(0, 0, record.parameters.extensionFieldBytes)
        record.publicKeyBytes[0] = 2

        val valid = record.verify()

        assertFalse(valid)
    }
}
