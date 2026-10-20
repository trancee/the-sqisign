package org.sqisign

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.sqisign.cinterop.sqisign_kmp_keypair
import org.sqisign.cinterop.sqisign_kmp_sign
import org.sqisign.cinterop.sqisign_kmp_sizes
import org.sqisign.cinterop.sqisign_kmp_verify
import platform.posix.size_tVar

// addressOf(0) is invalid for an empty array, so empty messages are passed as a one-byte buffer with length 0.
private val EMPTY = ByteArray(1)

private fun ByteArray.orNonEmpty(): ByteArray = if (isEmpty()) EMPTY else this

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeSizes(set: Int, out: IntArray): Int = memScoped {
    val pk = alloc<size_tVar>()
    val sk = alloc<size_tVar>()
    val sig = alloc<size_tVar>()
    val result = sqisign_kmp_sizes(set, pk.ptr, sk.ptr, sig.ptr)
    out[0] = pk.value.convert()
    out[1] = sk.value.convert()
    out[2] = sig.value.convert()
    result
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeKeypair(set: Int, pk: ByteArray, sk: ByteArray): Int =
    pk.usePinned { p ->
        sk.usePinned { s ->
            sqisign_kmp_keypair(set, p.addressOf(0).reinterpretU(), s.addressOf(0).reinterpretU())
        }
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeSign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int =
    sig.usePinned { g ->
        msg.orNonEmpty().usePinned { m ->
            sk.usePinned { s ->
                sqisign_kmp_sign(
                    set,
                    g.addressOf(0).reinterpretU(),
                    m.addressOf(0).reinterpretU(),
                    msg.size.convert(),
                    s.addressOf(0).reinterpretU(),
                )
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeVerify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int =
    sig.usePinned { g ->
        msg.orNonEmpty().usePinned { m ->
            pk.usePinned { p ->
                sqisign_kmp_verify(
                    set,
                    g.addressOf(0).reinterpretU(),
                    sig.size.convert(),
                    m.addressOf(0).reinterpretU(),
                    msg.size.convert(),
                    p.addressOf(0).reinterpretU(),
                )
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<ByteVar>.reinterpretU(): CPointer<UByteVar> = reinterpret()
