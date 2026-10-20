// swift-tools-version:5.9
// Swift consumer test for the Kotlin/Native SqiSign XCFramework.
// Build the framework first: ./gradlew assembleSqiSignReleaseXCFramework (from kotlin/)
import PackageDescription

let package = Package(
    name: "SqiSignSwiftTests",
    platforms: [.iOS(.v14), .macOS(.v11)],
    targets: [
        .binaryTarget(name: "SqiSign", path: "../build/XCFrameworks/release/SqiSign.xcframework"),
        .testTarget(name: "SqiSignTests", dependencies: ["SqiSign"]),
    ]
)
