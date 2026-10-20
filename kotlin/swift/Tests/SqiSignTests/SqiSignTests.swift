import Foundation
import SqiSign
import XCTest

private extension KotlinByteArray {
    convenience init(_ data: Data) {
        self.init(size: Int32(data.count))
        for (i, byte) in data.enumerated() { set(index: Int32(i), value: Int8(bitPattern: byte)) }
    }

    var data: Data { Data((0..<size).map { UInt8(bitPattern: get(index: $0)) }) }
}

final class SqiSignTests: XCTestCase {
    private let message = Data("SQIsign from Swift".utf8)

    func testRoundTripAllParameterSets() throws {
        for set in ParameterSet.entries {
            let keyPair = try SqiSign.shared.generateKeyPair(parameterSet: set)
            XCTAssertEqual(keyPair.publicKey.encoded.size, set.publicKeyBytes)

            let signature = try SqiSign.shared.sign(message: KotlinByteArray(message), secretKey: keyPair.secretKey)
            XCTAssertEqual(signature.size, set.signatureBytes)
            XCTAssertTrue(
                SqiSign.shared.verify(
                    message: KotlinByteArray(message), signature: signature, publicKey: keyPair.publicKey),
                set.algorithmName)

            var tampered = signature.data
            tampered[0] ^= 0x01
            XCTAssertFalse(
                SqiSign.shared.verify(
                    message: KotlinByteArray(message), signature: KotlinByteArray(tampered),
                    publicKey: keyPair.publicKey))
            XCTAssertFalse(
                SqiSign.shared.verify(
                    message: KotlinByteArray(Data("other".utf8)), signature: signature, publicKey: keyPair.publicKey))
        }
    }

    func testPublicKeyRoundTripsThroughData() throws {
        let keyPair = try SqiSign.shared.generateKeyPair(parameterSet: .p3243)
        let restored = try SqiSignPublicKey(
            parameterSet: .p3243, encoded: KotlinByteArray(keyPair.publicKey.encoded.data))
        XCTAssertEqual(restored, keyPair.publicKey)
    }

    func testWrongKeyLengthThrows() {
        XCTAssertThrowsError(try SqiSignPublicKey(parameterSet: .p3243, encoded: KotlinByteArray(Data(count: 3))))
    }

    func testClosedSecretKeyThrows() throws {
        let keyPair = try SqiSign.shared.generateKeyPair(parameterSet: .p3243)
        keyPair.secretKey.close()
        XCTAssertThrowsError(try SqiSign.shared.sign(message: KotlinByteArray(message), secretKey: keyPair.secretKey))
    }
}
