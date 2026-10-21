// swift-tools-version:5.9
// SqiSignKit: a Swift API (Data, async/await) over the Kotlin/Native SqiSign XCFramework.
// Build the framework first: ./gradlew assembleSqiSignReleaseXCFramework (from kotlin/)
import PackageDescription

let package = Package(
    name: "SqiSignKit",
    platforms: [.iOS(.v14), .macOS(.v11)],
    products: [
        .library(name: "SqiSignKit", targets: ["SqiSignKit"]),
    ],
    targets: [
        .binaryTarget(name: "SqiSign", path: "../build/XCFrameworks/release/SqiSign.xcframework"),
        .target(
            name: "SqiSignKit",
            dependencies: ["SqiSign"],
            swiftSettings: [.enableExperimentalFeature("StrictConcurrency")]),
        .testTarget(name: "SqiSignKitTests", dependencies: ["SqiSignKit"]),
        // XCTest measure() benchmarks; skipped by default: swift test --skip SqiSignKitBenchmarks
        .testTarget(name: "SqiSignKitBenchmarks", dependencies: ["SqiSignKit"]),
    ]
)
