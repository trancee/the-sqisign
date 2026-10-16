// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    kotlin("multiplatform") version "2.4.21"
}

group = "org.sqisign"
version = "0.1.0-SNAPSHOT"

kotlin {
    explicitApi()
    jvmToolchain(27)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_26)
        }
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

plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version = "26.12.0"
}

tasks.wrapper {
    gradleVersion = "9.8.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f"
}
