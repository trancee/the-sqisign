@file:OptIn(ExperimentalForeignApi::class, ExperimentalObjCRefinement::class, BetaInteropApi::class)

package org.sqisign

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.ShouldRefineInSwift

/**
 * Copies [data] into a new [ByteArray] in one block copy.
 *
 * Swift callers use the `Data` API of the `SqiSignKit` Swift package instead; Swift sees this function as
 * `Data_appleKt.__byteArrayData(_:)`.
 *
 * @throws IllegalArgumentException if [data] is larger than a [ByteArray] can hold.
 */
@ShouldRefineInSwift
@Throws(IllegalArgumentException::class)
public fun byteArray(data: NSData): ByteArray {
    require(data.length <= Int.MAX_VALUE.toULong()) { "Data of ${data.length} bytes exceeds the ByteArray limit" }
    val result = ByteArray(data.length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return result
}

/**
 * Copies [bytes] into a new [NSData] in one block copy.
 *
 * Swift callers use the `Data` API of the `SqiSignKit` Swift package instead; Swift sees this function as
 * `Data_appleKt.__nsDataBytes(_:)`.
 */
@ShouldRefineInSwift
public fun nsData(bytes: ByteArray): NSData = if (bytes.isEmpty()) {
    NSData()
} else {
    bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
}
