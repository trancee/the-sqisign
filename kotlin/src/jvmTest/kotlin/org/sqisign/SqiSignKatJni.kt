package org.sqisign

/** JNI entry point implemented in native/src/sqisign_kat_jni.c. The class name is part of the JNI symbol name. */
internal object SqiSignKatJni {
    init {
        loadBundledJni("sqisign_kat_jni", "org/sqisign/katnative")
    }

    @JvmStatic external fun keypairSign(
        set: Int,
        seed: ByteArray,
        msg: ByteArray,
        pk: ByteArray,
        sk: ByteArray,
        sig: ByteArray,
    ): Int
}

internal actual fun nativeKatKeypairSign(
    set: Int,
    seed: ByteArray,
    msg: ByteArray,
    pk: ByteArray,
    sk: ByteArray,
    sig: ByteArray,
): Int = SqiSignKatJni.keypairSign(set, seed, msg, pk, sk, sig)
