// SPDX-License-Identifier: Apache-2.0

import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    kotlin("multiplatform") version "2.4.21"
    id("com.diffplug.spotless") version "8.10.4"
    id("dev.detekt") version "2.0.0-alpha.6"
    alias(libs.plugins.kotlinx.benchmark)
    jacoco
}

group = "ch.trancee.sqisign"

version = "0.1.0-SNAPSHOT"

val P664_TEST_PATTERN = "*P664*"
val KAT_TEST_PATTERN = "ch.trancee.sqisign.kat.*"
val KAT_TEST_FORKS = Runtime.getRuntime().availableProcessors()

kotlin {
    explicitApi()
    jvmToolchain(26)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        keepLocallyUnsupportedTargets.set(false)
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_26)
        }
        val benchmarkCompilation =
            compilations.create("benchmark") {
                associateWith(this@jvm.compilations.getByName("main"))
                defaultSourceSet.dependencies {
                    implementation(libs.kotlinx.benchmark.runtime)
                }
            }
        compilations.getByName("test").associateWith(benchmarkCompilation)
    }
    js {
        nodejs {
            testTask {
                // CI splits Node tests into complementary shards over one pattern.
                when (val shard = providers.gradleProperty("jsTestShard").orNull) {
                    null -> Unit
                    "p664" -> filter.includeTestsMatching(P664_TEST_PATTERN)
                    "other" -> filter.excludeTestsMatching(P664_TEST_PATTERN)
                    else -> error("Unknown jsTestShard '$shard'")
                }
                useMocha {
                    timeout = "30000"
                }
            }
        }
        compilerOptions {
            target.set("es2015")
        }
    }
    listOf(macosArm64(), linuxX64()).forEach { target ->
        target.binaries.withType<TestExecutable>().configureEach {
            optimized = true
            debuggable = false
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

benchmark {
    targets {
        register("jvmBenchmark") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
    }
    configurations {
        named("main") {
            mode = "avgt"
            outputTimeUnit = "ns"
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 3)
            reportFormat = "json"
        }
        register("smoke") {
            mode = "avgt"
            outputTimeUnit = "ns"
            warmups = 1
            iterations = 1
            iterationTime = 50
            iterationTimeUnit = "ms"
            advanced("jvmForks", 1)
            reportFormat = "json"
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude(
            "src/commonTest/kotlin/ch/trancee/sqisign/TestVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/CurveVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/TorsionVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/TorsionRingVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/PairingVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/BasisChangeVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/IsogenyVectors.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/BasisActionOracleTest.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/BasisChangeOracleTest.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/ec/BasisCorrespondenceOracleTest.kt",
            "src/commonMain/kotlin/ch/trancee/sqisign/ec/E0BasisCoordinates.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/CanonicalBasisInputs.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/DiscreteLogInputs.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/TatePairingInputs.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/BasisTransformInputs.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/IsogenyInputs.kt",
            "src/commonTest/kotlin/ch/trancee/sqisign/hd/ThetaChainVectors.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/ThetaChainInputs.kt",
            "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/VerifyInputs.kt",
        )
        ktfmt("0.65").kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt("0.65").kotlinlangStyle()
    }
}

detekt {
    toolVersion = "2.0.0-alpha.6"
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/jvmBenchmark/kotlin",
        "src/jvmTest/kotlin",
    )
    config.setFrom("config/detekt.yml")
    buildUponDefaultConfig = true
    basePath = projectDir
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude("**/TestVectors.kt")
    exclude("**/CurveVectors.kt")
    exclude("**/TorsionVectors.kt")
    exclude("**/TorsionRingVectors.kt")
    exclude("**/PairingVectors.kt")
    exclude("**/BasisChangeVectors.kt")
    exclude("**/IsogenyVectors.kt")
    exclude("**/BasisActionOracleTest.kt")
    exclude("**/BasisChangeOracleTest.kt")
    exclude("**/BasisCorrespondenceOracleTest.kt")
    exclude("**/E0BasisCoordinates.kt")
    exclude("**/CanonicalBasisInputs.kt")
    exclude("**/DiscreteLogInputs.kt")
    exclude("**/TatePairingInputs.kt")
    exclude("**/BasisTransformInputs.kt")
    exclude("**/IsogenyInputs.kt")
    exclude("**/ThetaChainVectors.kt")
    exclude("**/ThetaChainInputs.kt")
    exclude("**/VerifyInputs.kt")
}

jacoco {
    toolVersion = "0.8.15"
}

val jvmTest = tasks.named<Test>("jvmTest")
val katDirectory = layout.projectDirectory.dir("../KAT")

// Verifying all 300 upstream KAT signatures runs as its own task, so CI can shard it.
jvmTest.configure { filter.excludeTestsMatching(KAT_TEST_PATTERN) }

val jvmKatTest =
    tasks.register<Test>("jvmKatTest") {
        group = "verification"
        description =
            "Verifies every upstream KAT signature and the C-pinned mutation corpus on the JVM."
        testClassesDirs = jvmTest.get().testClassesDirs
        classpath = jvmTest.get().classpath
        filter.includeTestsMatching(KAT_TEST_PATTERN)
        inputs
            .dir(katDirectory)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("katDirectory")
        systemProperty("sqisign.katDirectory", katDirectory.asFile.absolutePath)
        val corpus = layout.projectDirectory.file("tools/verify_mutations.txt")
        inputs
            .file(corpus)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("verifyCorpus")
        systemProperty("sqisign.verifyCorpus", corpus.asFile.absolutePath)
        maxParallelForks = KAT_TEST_FORKS
    }
val jvmClasses = layout.buildDirectory.dir("classes/kotlin/jvm/main")
val coverageReport =
    tasks.register<JacocoReport>("jvmCoverageReport") {
        dependsOn(jvmTest)
        executionData(jvmTest.get())
        classDirectories.setFrom(jvmClasses)
        sourceDirectories.setFrom("src/commonMain/kotlin")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
val coverageVerification =
    tasks.register<JacocoCoverageVerification>("jvmCoverageVerification") {
        dependsOn(coverageReport)
        executionData(jvmTest.get())
        classDirectories.setFrom(jvmClasses)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "1.0".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "1.0".toBigDecimal()
                }
            }
        }
    }

val checkTestVectors =
    tasks.register<Exec>("checkTestVectors") {
        group = "verification"
        description = "Checks generated public fixtures against hashlib and the upstream KAT files."
        commandLine("python3", "tools/generate_test_vectors.py", "--check")
    }

val checkUnsignedReference =
    tasks.register<Exec>("checkUnsignedReference") {
        group = "verification"
        description =
            "Cross-checks public unsigned arithmetic vectors against pinned C and Python oracles."
        commandLine("python3", "-B", "tools/check_unsigned_reference.py")
    }

val checkCurveReference =
    tasks.register<Exec>("checkCurveReference") {
        group = "verification"
        description =
            "Cross-checks affine curve and reference-sign root vectors against the pinned C reference."
        commandLine("python3", "-B", "tools/check_curve_reference.py")
    }

val checkTorsionRingReference =
    tasks.register<Exec>("checkTorsionRingReference") {
        group = "verification"
        description =
            "Cross-checks torsion scalars and matrices against pinned C and Python integer oracles."
        commandLine("python3", "-B", "tools/check_torsion_ring_reference.py")
    }

val checkHdReference =
    tasks.register<Exec>("checkHdReference") {
        group = "verification"
        description =
            "Cross-checks theta-chain kernels, vectors, and the Python oracle against the pinned C reference."
        commandLine("python3", "-B", "tools/check_hd_reference.py")
    }

val checkVerifyReference =
    tasks.register<Exec>("checkVerifyReference") {
        group = "verification"
        description =
            "Cross-checks the verification mutation corpus against pinned C protocols_verify verdicts."
        commandLine("python3", "-B", "tools/check_verify_reference.py")
    }

tasks.named("check") {
    dependsOn(
        coverageVerification,
        checkTestVectors,
        checkUnsignedReference,
        checkCurveReference,
        checkTorsionRingReference,
        checkHdReference,
        checkVerifyReference,
        jvmKatTest,
        "detektMainJvm",
        "detektBenchmarkJvm",
        "detektTestJvm",
    )
}

plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version = "26.12.0"
}

tasks.wrapper {
    gradleVersion = "9.7.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae"
}
