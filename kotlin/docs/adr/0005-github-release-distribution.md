# ADR 0005: Distribution through GitHub Releases

- Status: accepted
- Version: 1.0
- Date: 2026-10-29
- Constitution: E1 (reproducible release), G4 (public change), O3 (decision record), Q5/Q6 (compatibility, semver)

## Context

Nothing is published. The JVM jar, AAR, klibs and XCFramework exist only as build outputs and CI workflow artifacts,
and `SqiSignKit`'s binary target points at the local XCFramework ([ADR 0003](0003-sqisignkit-swift-layer.md)). The
project version was `0.1.0-SNAPSHOT` under the group `org.sqisign`, which belongs to the SQIsign authors' domain, not to
this fork.

The Rust port is an opt-in backend that does not yet meet the 10 % performance budget
([ADR 0004](0004-rust-port-opt-in-backend.md)), so it must not replace the C build for existing consumers.

## Decision

1. Releases are GitHub Releases of `trancee/the-sqisign`, created by
   [`release.yml`](../../../.github/workflows/release.yml) when a tag `vX.Y.Z` matching the project `version` is pushed.
   The workflow creates a draft; a maintainer checks it and publishes it by hand. A manual run of the workflow, or a
   pull request that changes it, builds the same assets as workflow artifacts only (dry run).
2. Maven coordinates are `io.github.trancee:sqisign:<version>` for the C backend and
   `io.github.trancee:sqisign-rust:<version>` for the Rust backend, with the Kotlin Multiplatform per-target artifacts
   (`sqisign-jvm`, `sqisign-android`, `sqisign-iosarm64`, `sqisign-iossimulatorarm64`, `sqisign-macosarm64`, and the
   same with `sqisign-rust-`). The two never share coordinates, so a consumer selects the backend by dependency. The
   Kotlin package stays `org.sqisign`.
3. The first version is `0.1.0`. Versions follow Q6: while the major version is 0, breaking API changes raise the
   minor version.
4. Each release carries, per backend (`<name>` is `sqisign` or `sqisign-rust`):
   - `<name>-<version>-maven.zip`: a Maven repository (`publishAllPublicationsToReleaseRepository`, Gradle module
     metadata, POMs, sources) whose JVM jar carries the JNI libraries of all five JVM platforms, each built and tested
     on its own runner;
   - `<name>-<version>.xcframework.zip`, the static `SqiSign.xcframework`, with its SwiftPM checksum in
     `<name>-<version>.xcframework.zip.checksum`;
   - `<name>-<version>.sha256`, the SHA-256 of both zips.
5. Before zipping, the workflow checks that the jar holds all five JVM libraries, the AAR both ABIs, every cinterop
   klib its static library, and that the XCFramework holds the selected backend.

## Alternatives

- Maven Central: rejected for now. Releases are permanent, and it needs a verified namespace and signing keys. The
  `io.github.trancee` group can be verified later without changing coordinates.
- GitHub Packages for Maven: rejected. Consumers must authenticate even for public packages.
- Shipping the Rust backend under the C coordinates or as a Gradle variant attribute: rejected. ADR 0004 keeps C the
  default, and separate coordinates make the choice explicit in every dependency declaration.

## Risks

- Consumers must download and unzip the Maven repository and declare it as a local `maven` repository; nothing
  resolves from a public repository.
- `SqiSignKit` still points at the local build output. Switching its binary target to a release URL and checksum is a
  separate change once a release exists.
- The zips are not byte-for-byte reproducible: `zip` and `ditto` record file times. The jar and klib entries are.
- The JVM jar is assembled on macOS from libraries built and tested on their own runners. Loading each library from
  the combined jar on its platform is tested by the `JVM jar` job of `kotlin.yml` on the tagged commit, not again by
  the release workflow.

## Migration

The Maven group changes from `org.sqisign` to `io.github.trancee` and the artifact base from `sqisign-kotlin` to
`sqisign`. Nothing was published under the old coordinates, so no consumer migrates. The klib's unique name in the ABI
dump (`api/sqisign-kotlin.klib.api`) follows the group; the declarations are unchanged.
