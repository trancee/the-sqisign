// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.SqiSign
import ch.trancee.sqisign.WireFormat
import java.io.File
import kotlin.test.assertEquals

/** One `tools/verify_mutations.txt` line: a canonical patch of a KAT record and the C verdict. */
private class CorpusCase(line: String) {
    private val fields = line.split(' ')
    val identifier = fields[0]
    val count = fields[1].toInt()
    val target = fields[2]
    val offset = fields[3]
    val replacement = fields[4]
    val accepted = fields[5] == "1"
}

/** Replays the C-pinned `tools/verify_mutations.txt` corpus of [parameters] through Kotlin. */
internal fun verifyCorpus(parameters: ParameterSet) {
    val records = katRecords(parameters).associateBy { it.count }
    val cases =
        File(System.getProperty("sqisign.verifyCorpus")).readLines().map(::CorpusCase).filter {
            it.identifier == parameters.identifier
        }
    val disagreements = cases.filterNot { case ->
        val record = records.getValue(case.count)
        val encodings =
            mutableMapOf(
                "pk" to record.publicKey.copyOf(),
                "sig" to record.signedMessage.copyOf(parameters.signatureBytes),
                "msg" to record.message.copyOf(),
            )
        if (case.target != "-") {
            case.replacement
                .hexToByteArray()
                .copyInto(encodings.getValue(case.target), case.offset.toInt())
        }
        val valid =
            SqiSign.verify(
                WireFormat.publicKey(parameters, encodings.getValue("pk")),
                encodings.getValue("msg"),
                WireFormat.signature(parameters, encodings.getValue("sig")),
            )
        valid == case.accepted
    }
    assertEquals(0, disagreements.size, "corpus cases where Kotlin and C disagree")
    assertEquals(CORPUS_CASES_PER_LEVEL, cases.size)
}

private const val CORPUS_CASES_PER_LEVEL = 56
