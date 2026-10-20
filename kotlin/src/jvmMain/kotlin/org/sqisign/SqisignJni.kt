package org.sqisign

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** JNI entry points implemented in native/src/sqisign_jni.c. The class name is part of the JNI symbol names. */
internal object SqisignJni {
    private const val LIBRARY = "sqisign_jni"

    /** System property naming an absolute path to a prebuilt JNI library, overriding the bundled one. */
    private const val LIBRARY_PATH_PROPERTY = "sqisign.library.path"

    init {
        load()
    }

    @JvmStatic external fun sizes(set: Int, out: IntArray): Int

    @JvmStatic external fun keypair(set: Int, pk: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun sign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun verify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int

    private fun load() {
        System.getProperty(LIBRARY_PATH_PROPERTY)?.let {
            System.load(File(it).absolutePath)
            return
        }
        val fileName = System.mapLibraryName(LIBRARY)
        val resource = "/org/sqisign/native/${platform()}/$fileName"
        val stream = SqisignJni::class.java.getResourceAsStream(resource)
        if (stream == null) {
            // Not bundled for this platform; fall back to java.library.path.
            System.loadLibrary(LIBRARY)
            return
        }
        val dir = Files.createTempDirectory("sqisign-jni").toFile()
        val file = File(dir, fileName)
        stream.use { Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        file.deleteOnExit()
        dir.deleteOnExit()
        System.load(file.absolutePath)
    }

    private fun platform(): String {
        val os = System.getProperty("os.name").lowercase().let {
            when {
                it.contains("mac") -> "macos"
                it.contains("win") -> "windows"
                else -> "linux"
            }
        }
        val arch = when (val a = System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "aarch64"
            "amd64", "x86_64" -> "x86_64"
            else -> a
        }
        return "$os-$arch"
    }
}

internal actual fun nativeSizes(set: Int, out: IntArray): Int = SqisignJni.sizes(set, out)

internal actual fun nativeKeypair(set: Int, pk: ByteArray, sk: ByteArray): Int = SqisignJni.keypair(set, pk, sk)

internal actual fun nativeSign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int =
    SqisignJni.sign(set, sig, msg, sk)

internal actual fun nativeVerify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int =
    SqisignJni.verify(set, sig, msg, pk)
