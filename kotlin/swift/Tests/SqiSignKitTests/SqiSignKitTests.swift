import Foundation
import SqiSignKit
import XCTest

final class SqiSignKitTests: XCTestCase {
    private let message = Data("SQIsign from Swift".utf8)

    func testRoundTripAllParameterSets() throws {
        for set in ParameterSet.entries {
            let keyPair = try SqiSignKeyPair.generate(set)
            XCTAssertEqual(keyPair.publicKey.rawRepresentation.count, Int(set.publicKeyBytes))

            let signature = try keyPair.secretKey.signature(for: message)
            XCTAssertEqual(signature.count, Int(set.signatureBytes))
            XCTAssertTrue(keyPair.publicKey.isValidSignature(signature, for: message), set.algorithmName)

            var tampered = signature
            tampered[0] ^= 0x01
            XCTAssertFalse(keyPair.publicKey.isValidSignature(tampered, for: message))
            XCTAssertFalse(keyPair.publicKey.isValidSignature(signature, for: Data("other".utf8)))
            XCTAssertFalse(keyPair.publicKey.isValidSignature(signature.dropLast(), for: message))
        }
    }

    func testLevelsNameTheParameterSets() {
        XCTAssertEqual(ParameterSet.level1, .p3243)
        XCTAssertEqual(ParameterSet.level3, .p50027)
        XCTAssertEqual(ParameterSet.level5, .p66417)
        XCTAssertEqual([ParameterSet.level1, .level3, .level5].map(\.nistLevel), [1, 3, 5])
    }

    func testEmptyMessageIsSupported() throws {
        let keyPair = try SqiSignKeyPair.generate()
        let signature = try keyPair.secretKey.signature(for: Data())
        XCTAssertTrue(keyPair.publicKey.isValidSignature(signature, for: Data()))
    }

    func testKeysRoundTripThroughRawRepresentation() throws {
        let keyPair = try SqiSignKeyPair.generate(.level1)
        let publicKey = try SqiSignPublicKey(parameterSet: .level1, rawRepresentation: keyPair.publicKey.rawRepresentation)
        XCTAssertEqual(publicKey, keyPair.publicKey)

        let secretKey = try SqiSignSecretKey(parameterSet: .level1, rawRepresentation: keyPair.secretKey.rawRepresentation)
        XCTAssertEqual(try secretKey.rawRepresentation, try keyPair.secretKey.rawRepresentation)
        XCTAssertTrue(publicKey.isValidSignature(try secretKey.signature(for: message), for: message))
    }

    func testWrongKeyLengthThrows() {
        XCTAssertThrowsError(try SqiSignPublicKey(parameterSet: .level1, rawRepresentation: Data(count: 3)))
        XCTAssertThrowsError(try SqiSignSecretKey(parameterSet: .level1, rawRepresentation: Data(count: 3)))
    }

    func testClosedSecretKeyThrows() throws {
        let keyPair = try SqiSignKeyPair.generate()
        keyPair.secretKey.close()
        XCTAssertThrowsError(try keyPair.secretKey.signature(for: message)) {
            XCTAssertEqual($0 as? SqiSignKitError, .secretKeyClosed)
        }
        XCTAssertThrowsError(try keyPair.secretKey.rawRepresentation) {
            XCTAssertEqual($0 as? SqiSignKitError, .secretKeyClosed)
        }
    }

    func testKotlinErrorsReachSwift() {
        let keyPair = try? SqiSignKeyPair.generate()
        let level3Key = try? SqiSignKeyPair.generate(.level3)
        XCTAssertThrowsError(try SqiSignKeyPair(publicKey: keyPair!.publicKey, secretKey: level3Key!.secretKey))
    }

    func testAsyncRoundTrip() async throws {
        let keyPair = try await SqiSignKeyPair.generate(.level1)
        let signature = try await keyPair.secretKey.signature(for: message)
        let valid = try await keyPair.publicKey.isValidSignature(signature, for: message)
        XCTAssertTrue(valid)
    }

    func testAsyncChecksCancellationBeforeStarting() async {
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await SqiSignKeyPair.generate(.level5)
        }
        do {
            _ = try await task.value
            XCTFail("expected CancellationError")
        } catch {
            XCTAssertTrue(error is CancellationError, "\(error)")
        }
    }
}
