// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PowerOfTwoDiscreteLogOracleTest {
    @Test fun p324SmallOrders() = assertSmallOrders(Fp2P324, PairingVectors.p324DiscreteLogs)

    @Test fun p500SmallOrders() = assertSmallOrders(Fp2P500, PairingVectors.p500DiscreteLogs)

    @Test fun p664SmallOrders() = assertSmallOrders(Fp2P664, PairingVectors.p664DiscreteLogs)

    @Test fun p324ReducedZero() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[12])

    @Test fun p324ReducedMaximum() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[13])

    @Test fun p324ReducedDense() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[14])

    @Test fun p324FullZero() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[15])

    @Test fun p324FullHighBit() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[16])

    @Test fun p324FullMaximum() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[17])

    @Test fun p324FullDense() = assertLog(Fp2P324, PairingVectors.p324DiscreteLogs[18])

    @Test fun p500ReducedZero() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[12])

    @Test fun p500ReducedMaximum() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[13])

    @Test fun p500ReducedDense() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[14])

    @Test fun p500FullZero() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[15])

    @Test fun p500FullHighBit() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[16])

    @Test fun p500FullMaximum() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[17])

    @Test fun p500FullDense() = assertLog(Fp2P500, PairingVectors.p500DiscreteLogs[18])

    @Test fun p664ReducedZero() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[12])

    @Test fun p664ReducedMaximum() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[13])

    @Test fun p664ReducedDense() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[14])

    @Test fun p664FullZero() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[15])

    @Test fun p664FullHighBit() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[16])

    @Test fun p664FullMaximum() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[17])

    @Test fun p664FullDense() = assertLog(Fp2P664, PairingVectors.p664DiscreteLogs[18])

    private fun <Element : QuadraticFieldElement<Element>> assertSmallOrders(
        field: QuadraticField<Element>,
        records: List<String>,
    ) {
        for (record in records.take(12)) assertLog(field, record)
    }

    private fun <Element : QuadraticFieldElement<Element>> assertLog(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val fields = record.split(":")
        val exponent = fields[0].toInt()
        val generator = field.fromLittleEndian(fields[1].hexToByteArray())
        val value = field.fromLittleEndian(fields[2].hexToByteArray())
        val solver = PowerOfTwoDiscreteLog.prepare(field, exponent, generator)

        val logarithm = solver.solve(value)

        assertEquals(exponent, logarithm.exponent, record)
        assertContentEquals(fields[3].hexToByteArray(), logarithm.toLittleEndian(), record)
        assertContentEquals(fields[1].hexToByteArray(), generator.toLittleEndian(), record)
        assertContentEquals(fields[2].hexToByteArray(), value.toLittleEndian(), record)
    }
}
