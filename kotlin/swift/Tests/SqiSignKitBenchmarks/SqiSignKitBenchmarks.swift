import Foundation
import SqiSignKit
import XCTest

/// Wall-clock benchmarks of key generation, signing and verification per parameter set (XCTest `measure`).
///
/// Run on a device through `swift/DeviceHost` (scheme `SqiSignDeviceBenchmarks`); see `kotlin/benchmarks/README.md`.
/// The message is 32 bytes, matching the JVM benchmarks.
final class SqiSignKitBenchmarks: XCTestCase {
    private let message = Data((0..<32).map { UInt8($0) })

    private var options: XCTMeasureOptions {
        let options = XCTMeasureOptions()
        options.iterationCount = 20
        return options
    }

    private func measureKeyGeneration(_ set: ParameterSet) {
        measure(metrics: [XCTClockMetric()], options: options) {
            _ = try! SqiSignKeyPair.generate(set)
        }
    }

    private func measureSigning(_ set: ParameterSet) throws {
        let keyPair = try SqiSignKeyPair.generate(set)
        measure(metrics: [XCTClockMetric()], options: options) {
            _ = try! keyPair.secretKey.signature(for: message)
        }
    }

    private func measureVerification(_ set: ParameterSet) throws {
        let keyPair = try SqiSignKeyPair.generate(set)
        let signature = try keyPair.secretKey.signature(for: message)
        measure(metrics: [XCTClockMetric()], options: options) {
            XCTAssertTrue(keyPair.publicKey.isValidSignature(signature, for: message))
        }
    }

    func testKeyGenerationLevel1() { measureKeyGeneration(.level1) }
    func testKeyGenerationLevel3() { measureKeyGeneration(.level3) }
    func testKeyGenerationLevel5() { measureKeyGeneration(.level5) }
    func testSignLevel1() throws { try measureSigning(.level1) }
    func testSignLevel3() throws { try measureSigning(.level3) }
    func testSignLevel5() throws { try measureSigning(.level5) }
    func testVerifyLevel1() throws { try measureVerification(.level1) }
    func testVerifyLevel3() throws { try measureVerification(.level3) }
    func testVerifyLevel5() throws { try measureVerification(.level5) }
}
