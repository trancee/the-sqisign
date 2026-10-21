// AndroidX Microbenchmark of keygen/sign/verify on a physical Android device:
// ./gradlew :benchmark-android:connectedReleaseAndroidTest (see benchmarks/README.md).
// The androidx.benchmark plugin builds the test APK in release (non-debuggable) and uses AndroidBenchmarkRunner.
plugins {
    // AGP is already on the build classpath through the root project's KMP Android library plugin.
    id("com.android.library")
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "org.sqisign.benchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    androidTestImplementation(project(":"))
    androidTestImplementation(libs.androidx.benchmark.junit4)
}
