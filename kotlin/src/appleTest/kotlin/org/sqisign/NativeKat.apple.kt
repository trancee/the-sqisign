package org.sqisign

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import org.sqisign.katinterop.sqisign_kmp_kat_keypair_sign

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeKatKeypairSign(
    set: Int,
    seed: ByteArray,
    msg: ByteArray,
    pk: ByteArray,
    sk: ByteArray,
    sig: ByteArray,
): Int {
    // addressOf(0) is invalid for an empty array; the length argument keeps the extra byte unread.
    val message = if (msg.isEmpty()) ByteArray(1) else msg
    return seed.usePinned { d ->
        message.usePinned { m ->
            pk.usePinned { p ->
                sk.usePinned { s ->
                    sig.usePinned { g ->
                        sqisign_kmp_kat_keypair_sign(
                            set,
                            d.addressOf(0).reinterpret<UByteVar>(),
                            m.addressOf(0).reinterpret<UByteVar>(),
                            msg.size.convert(),
                            p.addressOf(0).reinterpret<UByteVar>(),
                            s.addressOf(0).reinterpret<UByteVar>(),
                            g.addressOf(0).reinterpret<UByteVar>(),
                        )
                    }
                }
            }
        }
    }
}
