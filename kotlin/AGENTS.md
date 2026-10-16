# Kotlin subtree

Scope=all work under `kotlin/`; R/X/D/O/priority are defined by [`../CONSTITUTION.md`](../CONSTITUTION.md).

Follow the repository-wide [`../AGENTS.md`](../AGENTS.md) and apply the Kotlin-specific build, compatibility, platform-proof, generated-source, and tooling guidance in [`../guidance/kotlin.md`](../guidance/kotlin.md). Treat Kotlin tooling recommendations as guidance, not project facts: verify the actual Gradle files, wrapper, CI, and target matrix before proposing or running commands.

Use `./gradlew spotlessApply` for owned Kotlin/Gradle formatting and `./gradlew check` for local gates. Run clean CI-equivalent checks on both native hosts as recorded in [`../PROJECT.md`](../PROJECT.md). Never hand-edit generated fixtures or ABI dumps; use their owning commands after reviewing the intended change.
