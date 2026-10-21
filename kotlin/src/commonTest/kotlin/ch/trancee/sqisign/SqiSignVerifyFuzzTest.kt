// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FUZZ_SEED = 0x5157_1597
private const val FUZZ_CASES = 32

class SqiSignVerifyFuzzTest {
    @Test
    fun everyParseableSingleBitMutationIsRejectedWithoutThrowing() {
        val random = Random(FUZZ_SEED)
        var verified = 0

        repeat(FUZZ_CASES) {
            val record = KatRecordZero(ParameterSet.NIST_I)
            val target = if (random.nextBoolean()) record.signature else record.publicKeyBytes
            val bit = random.nextInt(target.size * Byte.SIZE_BITS)
            target[bit / Byte.SIZE_BITS] =
                (target[bit / Byte.SIZE_BITS].toInt() xor (1 shl bit % Byte.SIZE_BITS)).toByte()
            val parseable = runCatching {
                WireFormat.publicKey(record.parameters, record.publicKeyBytes)
                WireFormat.signature(record.parameters, record.signature)
            }
                .isSuccess

            if (parseable) {
                assertFalse(record.verify(), "bit $bit of a ${target.size}-byte encoding")
                verified++
            }
        }

        assertTrue(verified >= FUZZ_CASES / 2, "only $verified parseable mutations")
    }
}
