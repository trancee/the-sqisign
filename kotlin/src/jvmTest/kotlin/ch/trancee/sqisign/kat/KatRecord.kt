// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.kat

import ch.trancee.sqisign.ParameterSet
import java.io.File

/** One public record of an upstream `PQCsignKAT_*.rsp` file. */
internal class KatRecord(
    val count: Int,
    val message: ByteArray,
    val publicKey: ByteArray,
    val signedMessage: ByteArray,
)

/** Reads every record of the upstream KAT file for [parameters] from `sqisign.katDirectory`. */
internal fun katRecords(parameters: ParameterSet): List<KatRecord> {
    val directory = File(System.getProperty("sqisign.katDirectory"))
    val file =
        directory.listFiles().orEmpty().single {
            it.name.endsWith("_SQIsign_${parameters.identifier}.rsp")
        }
    return file
        .readText()
        .split("\n\n")
        .map { block ->
            block
                .lines()
                .mapNotNull { line ->
                    line.split(" = ", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
                }
                .toMap()
        }
        .filter { "sm" in it }
        .map {
            KatRecord(
                it.getValue("count").toInt(),
                it.getValue("msg").hexToByteArray(),
                it.getValue("pk").hexToByteArray(),
                it.getValue("sm").hexToByteArray(),
            )
        }
}
