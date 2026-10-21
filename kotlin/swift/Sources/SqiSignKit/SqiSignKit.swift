@_exported import SqiSign
import Foundation

/// Errors raised by the `SqiSignKit` layer itself. Failures of the Kotlin layer or the native core arrive as the
/// `NSError` that Kotlin/Native maps their exception to.
public enum SqiSignKitError: Error, Equatable {
    /// The secret key was closed with `close()`, so its key material is gone.
    case secretKeyClosed
}

// MARK: - Parameter sets

public extension ParameterSet {
    /// `SQIsign_lvl1` (Kotlin `ParameterSet.P324_3`), NIST security level 1.
    static var level1: ParameterSet { .p3243 }

    /// `SQIsign_lvl3` (Kotlin `ParameterSet.P500_27`), NIST security level 3.
    static var level3: ParameterSet { .p50027 }

    /// `SQIsign_lvl5` (Kotlin `ParameterSet.P664_17`), NIST security level 5.
    static var level5: ParameterSet { .p66417 }
}

// MARK: - Key generation

public extension SqiSignKeyPair {
    /// Generates a key pair with the operating system's random number generator.
    ///
    /// Blocks the calling thread for tens to hundreds of milliseconds; prefer the `async` variant on the main thread.
    static func generate(_ parameterSet: ParameterSet = .level1) throws -> SqiSignKeyPair {
        try SqiSign.shared.generateKeyPair(parameterSet: parameterSet)
    }

    /// Generates a key pair off the calling task's executor.
    ///
    /// Cancellation is checked before the work starts; the native key generation itself cannot be interrupted.
    static func generate(_ parameterSet: ParameterSet = .level1) async throws -> SqiSignKeyPair {
        try await runDetached { try generate(parameterSet) }
    }
}

// MARK: - Signing

public extension SqiSignSecretKey {
    /// Creates a secret key from its encoding, which must be `parameterSet.secretKeyBytes` long.
    ///
    /// The key copies `rawRepresentation`. Swift cannot reliably wipe the caller's `Data`.
    convenience init(parameterSet: ParameterSet, rawRepresentation: Data) throws {
        let bytes = try rawRepresentation.kotlinBytes()
        defer { bytes.wipe() }
        try self.init(parameterSet: parameterSet, encoded: bytes)
    }

    /// A copy of the encoded key. The returned `Data` cannot be reliably wiped.
    ///
    /// - Throws: `SqiSignKitError.secretKeyClosed` if the key has been closed.
    var rawRepresentation: Data {
        get throws {
            guard !isClosed else { throw SqiSignKitError.secretKeyClosed }
            let bytes = encoded
            defer { bytes.wipe() }
            return bytes.data
        }
    }

    /// Returns a detached signature of `message`, exactly `parameterSet.signatureBytes` long.
    ///
    /// Blocks the calling thread for tens to hundreds of milliseconds; prefer the `async` variant on the main thread.
    /// - Throws: `SqiSignKitError.secretKeyClosed` if the key has been closed.
    func signature(for message: Data) throws -> Data {
        guard !isClosed else { throw SqiSignKitError.secretKeyClosed }
        return try SqiSign.shared.sign(message: message.kotlinBytes(), secretKey: self).data
    }

    /// Signs `message` off the calling task's executor. Do not close the key while signing is in progress.
    ///
    /// Cancellation is checked before the work starts; native signing itself cannot be interrupted.
    func signature(for message: Data) async throws -> Data {
        try await runDetached { try self.signature(for: message) }
    }
}

// MARK: - Verification

public extension SqiSignPublicKey {
    /// Creates a public key from its encoding, which must be `parameterSet.publicKeyBytes` long.
    convenience init(parameterSet: ParameterSet, rawRepresentation: Data) throws {
        try self.init(parameterSet: parameterSet, encoded: rawRepresentation.kotlinBytes())
    }

    /// A copy of the encoded key.
    var rawRepresentation: Data { encoded.data }

    /// Returns `true` if `signature` is a valid signature of `message` under this key.
    func isValidSignature(_ signature: Data, for message: Data) -> Bool {
        guard let signatureBytes = try? signature.kotlinBytes(), let messageBytes = try? message.kotlinBytes() else {
            return false
        }
        return SqiSign.shared.verify(message: messageBytes, signature: signatureBytes, publicKey: self)
    }

    /// Verifies off the calling task's executor.
    ///
    /// Cancellation is checked before the work starts; native verification itself cannot be interrupted.
    func isValidSignature(_ signature: Data, for message: Data) async throws -> Bool {
        try await runDetached { self.isValidSignature(signature, for: message) }
    }
}

// MARK: - Bridging

private extension Data {
    /// Copies the bytes into a `KotlinByteArray` with one `memcpy` on the Kotlin side.
    func kotlinBytes() throws -> KotlinByteArray {
        try Data_appleKt.__byteArrayData(self)
    }
}

private extension KotlinByteArray {
    var data: Data { Data_appleKt.__nsDataBytes(self) }

    func wipe() {
        for index in 0..<size { set(index: index, value: 0) }
    }
}

/// Carries Kotlin objects across the detached task boundary. Kotlin/Native objects can be used from any thread; the
/// SQIsign functions are thread-safe, and a key must not be closed while an operation on it is running.
private struct UncheckedSendable<Value>: @unchecked Sendable {
    let value: Value
}

private func runDetached<Value>(_ work: @escaping () throws -> Value) async throws -> Value {
    try Task.checkCancellation()
    let box = UncheckedSendable(value: work)
    let result = try await Task.detached(priority: Task.currentPriority) {
        UncheckedSendable(value: try box.value())
    }.value
    return result.value
}
