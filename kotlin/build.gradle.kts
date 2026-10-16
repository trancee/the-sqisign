// SPDX-License-Identifier: Apache-2.0

import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
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
        nodejs()
        compilerOptions {
            target.set("es2015")
        }
    }
    macosArm64()
    linuxX64()

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
        targetExclude("src/commonTest/kotlin/ch/trancee/sqisign/TestVectors.kt")
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
}

jacoco {
    toolVersion = "0.8.15"
}

val jvmTest = tasks.named<Test>("jvmTest")
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

tasks.named("check") {
    dependsOn(
        coverageVerification,
        checkTestVectors,
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
