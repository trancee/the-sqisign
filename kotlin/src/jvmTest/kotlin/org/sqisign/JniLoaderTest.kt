package org.sqisign

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JniLoaderTest {
    @Test
    fun platformDirectoryNamesAreNormalized() {
        val cases = mapOf(
            ("Mac OS X" to "aarch64") to "macos-aarch64",
            ("Mac OS X" to "x86_64") to "macos-x86_64",
            ("Windows 11" to "amd64") to "windows-x86_64",
            ("Linux" to "arm64") to "linux-aarch64",
            ("Linux" to "amd64") to "linux-x86_64",
            ("FreeBSD" to "riscv64") to "linux-riscv64",
        )

        val actual = cases.keys.associateWith { (osName, osArch) -> jniPlatform(osName, osArch) }

        assertEquals(cases, actual)
    }

    @Test
    fun libraryPathOverrideIsLoadedInsteadOfTheBundledLibrary() {
        val missing = File("build/no-such-dir/libmissing.so")

        val failure = assertFailsWith<UnsatisfiedLinkError> { SqiSignJni.loadJni(missing.path) }

        assertTrue(missing.absolutePath in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun unbundledLibraryFallsBackToJavaLibraryPath() {
        val failure = assertFailsWith<UnsatisfiedLinkError> { loadBundledJni("sqisign_missing", "org/sqisign/native") }

        assertTrue("sqisign_missing" in failure.message.orEmpty(), failure.message)
    }
}
