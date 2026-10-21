# ADR 0003: SqiSignKit Swift layer over the XCFramework

- Status: accepted
- Version: 1.0
- Date: 2026-10-21
- Constitution: C2 (platform adapters), C4 (error model), E1 (dependencies), O3 (decision record)

## Context

Kotlin/Native exports the shared API to Swift through Objective-C. Swift callers therefore see `KotlinByteArray`
instead of `Data` and copied bytes in a Swift loop, one element at a time. They also see `SqiSign.shared`,
parameter-set names such as `.p3243` and no default arguments. Key generation and signing block the calling thread
for tens to hundreds of milliseconds: on an iPhone 15, signing takes 21–157 ms (median) depending on the parameter set.
Swift code expects `async` functions for that kind of work.

The Kotlin API has no `suspend` functions, flows or sealed classes, which are what Kotlin-to-Swift tools mainly improve.

## Decision

1. `appleMain` exports two block-copy conversions, `byteArray(NSData)` and `nsData(ByteArray)`. They are marked
   `@ShouldRefineInSwift`, so Swift sees them only as `Data_appleKt.__byteArrayData(_:)` and
   `Data_appleKt.__nsDataBytes(_:)`.
2. `kotlin/swift` is a SwiftPM package with a `SqiSignKit` library target over the `SqiSign` binary target. It
   re-exports `SqiSign` and adds a CryptoKit-style API:
   - `SqiSignKeyPair.generate(_:)`, `SqiSignSecretKey.signature(for:)` and
     `SqiSignPublicKey.isValidSignature(_:for:)`, each in a synchronous and an `async` variant;
   - `init(parameterSet:rawRepresentation:)` and `rawRepresentation` on both key types;
   - `ParameterSet.level1`, `.level3` and `.level5`.
3. The `async` variants run the synchronous call in a detached task at the caller's priority. They check
   cancellation before starting; the native call cannot be interrupted. Kotlin objects cross the task boundary in
   an internal `@unchecked Sendable` box. This is sound because the Kotlin/Native objects can be used from any
   thread and the SQIsign functions are thread-safe. Closing a secret key while it signs remains the caller's error,
   as in Kotlin.
4. Error model: Kotlin exceptions still arrive as the `NSError` that Kotlin/Native creates. The Swift layer adds
   `SqiSignKitError.secretKeyClosed`. It checks `isClosed` first, because Kotlin's `encoded` getter cannot be
   annotated with `@Throws`, and an exception thrown from it would terminate the process.
5. The package targets `swift-tools-version` 5.9 and builds `SqiSignKit` with complete strict concurrency checking.

## Alternatives

- SKIE: rejected for now. It would add a compiler plugin whose releases follow Kotlin versions (0.10.15 lists Kotlin
  2.4.20; this project uses 2.4.21). Its main features (suspend, Flow, sealed classes) do not apply to this API, and
  it would still not provide `Data` or `async` without bundled Swift code.
- Kotlin-only `NSData` overloads with `@ObjCName`: rejected. Kotlin cannot generate `async` Swift functions, and
  the `SqiSign` object cannot gain Apple-only members.
- Kotlin `suspend` functions: rejected. They need kotlinx-coroutines as a new runtime dependency on every target
  (E1), and Swift would see them as completion handlers unless SKIE is added.

## Risks

- `Data` copies of secret keys cannot be reliably wiped. `SqiSignKit` wipes its own `KotlinByteArray` copies, and
  the documentation says so.
- The `async` functions occupy a thread of Swift's cooperative pool for the whole native call. That is acceptable for
  CPU-bound work, but many concurrent signatures can starve other tasks.
- The binary target points at the local build output. Distributing `SqiSignKit` needs a released XCFramework URL
  and checksum.

## Migration

Swift callers of the raw framework keep working, because `SqiSignKit` re-exports `SqiSign`. The Swift test target was
renamed to `SqiSignKitTests`, and the Xcode scheme is now `SqiSignKit`.
