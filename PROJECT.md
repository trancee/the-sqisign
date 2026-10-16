# Project profile

purpose= C implementation of SQIsign.
languages/toolchains= C11 and ASM; CMake 3.13 or later.
supported_platforms= x64 and arm64 are exercised by CI; implementation types are `ref`, `opt`, `broadwell`, and `arm64`. Do not infer support for other targets from local builds.
prerequisites= CMake and a C11-compatible compiler; CI uses Ninja. Constant-time tests additionally require Valgrind and its development header.

## Setup and validation

Configure and build a reference implementation:

```sh
cmake -S . -B build -DSQISIGN_BUILD_TYPE=ref
cmake --build build
ctest --test-dir build --output-on-failure
```

Use `ctest --test-dir build -R '<test-regex>' --output-on-failure` for targeted tests. The default suite includes KATs; CI's main CMake test step excludes tests whose names end in `KAT`.

Formatting is checked with `scripts/format.sh --check` (clang-format 18.1.6, pinned in `.pre-commit-config.yaml`). Static analysis entry points are `scripts/cppcheck.sh` and `scripts/clang-tidy.sh`; coverage is measured with `scripts/coverage.sh`. CI also runs build/test configurations including Release, shared-library, ASan, LSan, UBSan, and dedicated Valgrind constant-time checks. See `.github/workflows/` for the authoritative platform and configuration matrix.

## Generated artifacts

The constants under `src/precomp` are generated from scripts in `scripts/precomp`; regeneration is not required to build. Their generators require the `Theta-SageMath` version of `two-isogenies` and `deuring-2D`. The `scripts/gen_pqm4_sources.sh` script generates sources for the pqm4 integration.
