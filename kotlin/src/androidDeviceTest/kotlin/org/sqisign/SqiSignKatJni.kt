package org.sqisign

/**
 * JNI entry point implemented in native/src/sqisign_kat_jni.c and packaged only into the device-test APK.
 * The class name is part of the JNI symbol name.
 */
internal object SqiSignKatJni {
    init {
        System.loadLibrary("sqisign_kat_jni")
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

internal fun nativeKatKeypairSign(
    set: Int,
    seed: ByteArray,
    msg: ByteArray,
    pk: ByteArray,
    sk: ByteArray,
    sig: ByteArray,
): Int = SqiSignKatJni.keypairSign(set, seed, msg, pk, sk, sig)
