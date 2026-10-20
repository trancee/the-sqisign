package org.sqisign

/** JNI entry points implemented in native/src/sqisign_jni.c. The class name is part of the JNI symbol names. */
internal object SqisignJni {
    init {
        System.loadLibrary("sqisign_jni")
    }

    @JvmStatic external fun sizes(set: Int, out: IntArray): Int

    @JvmStatic external fun keypair(set: Int, pk: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun sign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun verify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int
}

internal actual fun nativeSizes(set: Int, out: IntArray): Int = SqisignJni.sizes(set, out)

internal actual fun nativeKeypair(set: Int, pk: ByteArray, sk: ByteArray): Int = SqisignJni.keypair(set, pk, sk)

internal actual fun nativeSign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int =
    SqisignJni.sign(set, sig, msg, sk)

internal actual fun nativeVerify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int =
    SqisignJni.verify(set, sig, msg, pk)
