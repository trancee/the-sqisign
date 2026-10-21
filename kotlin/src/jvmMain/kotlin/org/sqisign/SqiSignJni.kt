package org.sqisign

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** JNI entry points implemented in native/src/sqisign_jni.c. The class name is part of the JNI symbol names. */
internal object SqiSignJni {
    private const val LIBRARY = "sqisign_jni"

    /** System property naming an absolute path to a prebuilt JNI library, overriding the bundled one. */
    private const val LIBRARY_PATH_PROPERTY = "sqisign.library.path"

    init {
        loadJni(System.getProperty(LIBRARY_PATH_PROPERTY))
    }

    @JvmStatic external fun sizes(set: Int, out: IntArray): Int

    @JvmStatic external fun keypair(set: Int, pk: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun sign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int

    @JvmStatic external fun verify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int

    /** Loads the library at [libraryPath] if given, otherwise the bundled one. */
    internal fun loadJni(libraryPath: String?) {
        if (libraryPath != null) {
            System.load(File(libraryPath).absolutePath)
        } else {
            loadBundledJni(LIBRARY, "org/sqisign/native")
        }
    }
}

/**
 * Loads [library] from the classpath resource `/<resourceRoot>/<os>-<arch>/<mapped name>` by copying it to a temporary
 * file, falling back to `java.library.path` when it is not bundled for this platform.
 */
internal fun loadBundledJni(library: String, resourceRoot: String) {
    val fileName = System.mapLibraryName(library)
    val stream = SqiSignJni::class.java.getResourceAsStream(
        "/$resourceRoot/${jniPlatform(System.getProperty("os.name"), System.getProperty("os.arch"))}/$fileName",
    )
    if (stream == null) {
        System.loadLibrary(library)
        return
    }
    val dir = Files.createTempDirectory(library).toFile()
    val file = File(dir, fileName)
    stream.use { Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    file.deleteOnExit()
    dir.deleteOnExit()
    System.load(file.absolutePath)
}

/** Maps the `os.name` and `os.arch` system properties to the bundled resource directory, e.g. `macos-aarch64`. */
internal fun jniPlatform(osName: String, osArch: String): String {
    val os = osName.lowercase().let {
        when {
            it.contains("mac") -> "macos"
            it.contains("win") -> "windows"
            else -> "linux"
        }
    }
    val arch = when (osArch) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x86_64"
        else -> osArch
    }
    return "$os-$arch"
}

internal actual fun nativeSizes(set: Int, out: IntArray): Int = SqiSignJni.sizes(set, out)

internal actual fun nativeKeypair(set: Int, pk: ByteArray, sk: ByteArray): Int = SqiSignJni.keypair(set, pk, sk)

internal actual fun nativeSign(set: Int, sig: ByteArray, msg: ByteArray, sk: ByteArray): Int =
    SqiSignJni.sign(set, sig, msg, sk)

internal actual fun nativeVerify(set: Int, sig: ByteArray, msg: ByteArray, pk: ByteArray): Int =
    SqiSignJni.verify(set, sig, msg, pk)
