# Module sqisign-kotlin

Kotlin Multiplatform bindings for the SQIsign post-quantum signature scheme (NIST round 3). The cryptography runs in
the reference C implementation, through JNI on the JVM and Android and through cinterop on Apple platforms.

Platforms: JVM 21 on Linux, macOS and Windows; Android 7.0+ (`arm64-v8a`, `x86_64`); iOS 14+ devices and Simulator
and macOS 11+, on Apple Silicon (`iosArm64`, `iosSimulatorArm64`, `macosArm64`).

```kotlin
val keyPair = SqiSign.generateKeyPair(ParameterSet.P324_3)
val signature = SqiSign.sign(message, keyPair.secretKey)
check(SqiSign.verify(message, signature, keyPair.publicKey))
keyPair.secretKey.close()
```

# Package org.sqisign

Key generation, signing and verification ([SqiSign]), parameter sets ([ParameterSet]) and typed key encodings.
