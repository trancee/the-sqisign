import Foundation
@testable import SqiSignKit
import XCTest

final class SecretKeyHygieneTests: XCTestCase {
    private let message = Data("SQIsign from Swift".utf8)

    func testWithRawRepresentationRoundTripsThroughContiguousBytes() throws {
        let keyPair = try SqiSignKeyPair.generate(.level1)
        let bytes = try keyPair.secretKey.withRawRepresentation { Array($0) }
        XCTAssertEqual(bytes.count, Int(ParameterSet.level1.secretKeyBytes))

        let secretKey = try SqiSignSecretKey(parameterSet: .level1, bytes: bytes)
        XCTAssertTrue(keyPair.publicKey.isValidSignature(try secretKey.signature(for: message), for: message))
        XCTAssertEqual(try secretKey.withRawRepresentation { Array($0) }, bytes)
    }

    func testWithRawRepresentationOfClosedKeyThrows() throws {
        let keyPair = try SqiSignKeyPair.generate()
        keyPair.secretKey.close()
        XCTAssertThrowsError(try keyPair.secretKey.withRawRepresentation { _ in }) {
            XCTAssertEqual($0 as? SqiSignKitError, .secretKeyClosed)
        }
    }

    func testBytesOfWrongLengthThrow() {
        XCTAssertThrowsError(try SqiSignSecretKey(parameterSet: .level1, bytes: [UInt8](repeating: 1, count: 3)))
    }

    func testSecretBufferWipeZeroesEveryByte() {
        let buffer = SecretBuffer(count: 64)
        defer { buffer.deallocate() }
        buffer.bytes.initializeMemory(as: UInt8.self, repeating: 0xA5)
        buffer.wipe()
        XCTAssertEqual(Array(buffer.bytes), [UInt8](repeating: 0, count: 64))
    }
}

final class SqiSignKeychainTests: XCTestCase {
    // The data protection keychain needs a keychain entitlement, which `swift test` on macOS does not have.
    #if os(macOS)
    private let keychain = SqiSignKeychain(service: "org.sqisign.tests.\(UUID())", useDataProtectionKeychain: false)
    #else
    private let keychain = SqiSignKeychain(service: "org.sqisign.tests.\(UUID())")
    #endif
    private let message = Data("SQIsign from Swift".utf8)

    override func setUpWithError() throws {
        do {
            try keychain.delete(account: "key")
        } catch let error as NSError where error.code == Int(errSecMissingEntitlement) {
            // SwiftPM test bundles on iOS run without an app and so without keychain access.
            throw XCTSkip("Keychain needs an app host: run scheme SqiSignDeviceTests in swift/DeviceHost")
        }
    }

    override func tearDownWithError() throws {
        try keychain.delete(account: "key")
    }

    func testStoredKeyLoadsAndSigns() throws {
        let keyPair = try SqiSignKeyPair.generate(.level1)
        try keychain.store(keyPair.secretKey, account: "key")

        let loaded = try XCTUnwrap(try keychain.secretKey(.level1, account: "key"))
        XCTAssertTrue(keyPair.publicKey.isValidSignature(try loaded.signature(for: message), for: message))
    }

    func testStoreDoesNotOverwrite() throws {
        try keychain.store(try SqiSignKeyPair.generate().secretKey, account: "key")
        XCTAssertThrowsError(try keychain.store(try SqiSignKeyPair.generate().secretKey, account: "key")) {
            XCTAssertEqual(($0 as NSError).code, Int(errSecDuplicateItem))
        }
    }

    func testMissingAndDeletedKeysLoadAsNil() throws {
        XCTAssertNil(try keychain.secretKey(.level1, account: "key"))
        try keychain.store(try SqiSignKeyPair.generate().secretKey, account: "key")
        try keychain.delete(account: "key")
        XCTAssertNil(try keychain.secretKey(.level1, account: "key"))
        try keychain.delete(account: "key")
    }

    func testLoadingWithTheWrongParameterSetThrows() throws {
        try keychain.store(try SqiSignKeyPair.generate(.level1).secretKey, account: "key")
        XCTAssertThrowsError(try keychain.secretKey(.level3, account: "key"))
    }

    func testStoringAClosedKeyThrows() throws {
        let secretKey = try SqiSignKeyPair.generate().secretKey
        secretKey.close()
        XCTAssertThrowsError(try keychain.store(secretKey, account: "key")) {
            XCTAssertEqual($0 as? SqiSignKitError, .secretKeyClosed)
        }
    }
}
