# SPDX-License-Identifier: Apache-2.0
#
# SQISIGN_KMP_BACKEND=rust (ADR 0004): the sqisign_kmp_* API from the sqisign-capi crate of the Rust port instead of
# the vendor C libraries. Included by CMakeLists.txt; defines what the outputs there consume:
#   - sqisign_kmp: the API with its link dependencies (and the KAT entry point for SQISIGN_KMP_KAT);
#   - SQISIGN_KMP_ARCHIVES / SQISIGN_KMP_ARCHIVE_FILES: the dependencies and archives merged for Apple cinterop;
#   - SQISIGN_KMP_GLUE_TARGETS: the C glue instrumented by SQISIGN_KMP_COVERAGE.
# The Rust code draws its randomness from randombytes(), linked here from the vendor sources exactly like the C
# backend: randombytes_system.c (operating system) for the shipped library, the NIST CTR-DRBG for the KAT library.

# SQISIGN_BUILD_TYPE selects the vendor C build (ref, arm64, ...); the Rust port has a single implementation.
set(SQISIGN_KMP_IGNORED ${SQISIGN_BUILD_TYPE})

find_program(SQISIGN_KMP_CARGO cargo HINTS "$ENV{HOME}/.cargo/bin" REQUIRED)
find_program(SQISIGN_KMP_RUSTC rustc HINTS "$ENV{HOME}/.cargo/bin" REQUIRED)
set(SQISIGN_KMP_RUST_DIR "${SQISIGN_ROOT}/rust")

# The Rust target triple. Empty means the host, which covers the desktop JNI builds (on Windows, the MinGW host must
# pass SQISIGN_KMP_RUST_TARGET=x86_64-pc-windows-gnu when Rust's default host is MSVC).
set(SQISIGN_KMP_RUST_TARGET "" CACHE STRING "Rust target triple (empty: derived from the CMake target)")
set(SQISIGN_KMP_RUST_ENV "")
if(SQISIGN_KMP_RUST_TARGET)
    set(SQISIGN_KMP_TRIPLE ${SQISIGN_KMP_RUST_TARGET})
elseif(ANDROID)
    set(SQISIGN_KMP_ANDROID_TRIPLES
        arm64-v8a aarch64-linux-android armeabi-v7a armv7-linux-androideabi
        x86_64 x86_64-linux-android x86 i686-linux-android)
    list(FIND SQISIGN_KMP_ANDROID_TRIPLES ${ANDROID_ABI} i)
    if(i LESS 0)
        message(FATAL_ERROR "No Rust target for ANDROID_ABI ${ANDROID_ABI}")
    endif()
    math(EXPR i "${i} + 1")
    list(GET SQISIGN_KMP_ANDROID_TRIPLES ${i} SQISIGN_KMP_TRIPLE)
elseif(APPLE AND CMAKE_OSX_ARCHITECTURES)
    list(GET CMAKE_OSX_ARCHITECTURES 0 arch)
    string(REPLACE "arm64" "aarch64" arch ${arch})
    if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
        if(CMAKE_OSX_SYSROOT MATCHES "[Ss]imulator")
            if(arch STREQUAL "aarch64")
                set(SQISIGN_KMP_TRIPLE aarch64-apple-ios-sim)
            else()
                set(SQISIGN_KMP_TRIPLE ${arch}-apple-ios)
            endif()
        else()
            set(SQISIGN_KMP_TRIPLE ${arch}-apple-ios)
        endif()
        set(SQISIGN_KMP_RUST_ENV IPHONEOS_DEPLOYMENT_TARGET=${CMAKE_OSX_DEPLOYMENT_TARGET})
    else()
        set(SQISIGN_KMP_TRIPLE ${arch}-apple-darwin)
        set(SQISIGN_KMP_RUST_ENV MACOSX_DEPLOYMENT_TARGET=${CMAKE_OSX_DEPLOYMENT_TARGET})
    endif()
else()
    set(SQISIGN_KMP_TRIPLE "")
endif()
if(SQISIGN_KMP_TRIPLE)
    set(SQISIGN_KMP_TRIPLE_NAME ${SQISIGN_KMP_TRIPLE})
else()
    set(SQISIGN_KMP_TRIPLE_NAME "the host")
endif()

set(SQISIGN_KMP_CARGO_DIR "${CMAKE_CURRENT_BINARY_DIR}/cargo")
if(SQISIGN_KMP_TRIPLE)
    set(SQISIGN_KMP_TARGET_ARGS --target ${SQISIGN_KMP_TRIPLE})
    set(SQISIGN_KMP_RUST_OUT "${SQISIGN_KMP_CARGO_DIR}/${SQISIGN_KMP_TRIPLE}/release")
else()
    set(SQISIGN_KMP_TARGET_ARGS "")
    set(SQISIGN_KMP_RUST_OUT "${SQISIGN_KMP_CARGO_DIR}/release")
endif()
if(MSVC)
    set(SQISIGN_KMP_RUST_LIB "${SQISIGN_KMP_RUST_OUT}/sqisign_capi.lib")
else()
    set(SQISIGN_KMP_RUST_LIB "${SQISIGN_KMP_RUST_OUT}/libsqisign_capi.a")
endif()

# The system libraries the Rust standard library needs on this target, as rustc reports them for a staticlib (the
# crate adds none). Run in the workspace so rust-toolchain.toml selects the pinned compiler.
file(WRITE "${SQISIGN_KMP_CARGO_DIR}/probe/probe.rs" "")
execute_process(
    COMMAND ${SQISIGN_KMP_RUSTC} ${SQISIGN_KMP_TARGET_ARGS} --crate-type staticlib --print native-static-libs
            -o "${SQISIGN_KMP_CARGO_DIR}/probe/libprobe.a" "${SQISIGN_KMP_CARGO_DIR}/probe/probe.rs"
    WORKING_DIRECTORY ${SQISIGN_KMP_RUST_DIR}
    RESULT_VARIABLE rc
    ERROR_VARIABLE probe)
if(NOT rc EQUAL 0)
    message(FATAL_ERROR "rustc cannot build a staticlib for ${SQISIGN_KMP_TRIPLE_NAME} (rustup target add?):\n${probe}")
endif()
string(REGEX MATCH "native-static-libs: ([^\n]*)" _ "${probe}")
separate_arguments(SQISIGN_KMP_RUST_SYSLIBS UNIX_COMMAND "${CMAKE_MATCH_1}")
list(REMOVE_DUPLICATES SQISIGN_KMP_RUST_SYSLIBS)
if(APPLE)
    # Every Apple link already adds libSystem.
    list(REMOVE_ITEM SQISIGN_KMP_RUST_SYSLIBS -lSystem -lc -lm)
endif()

# Always run: cargo decides what is stale. Fat LTO and one codegen unit for the shipped code; unwinding stays on so
# the C API can turn a panic into SQISIGN_KMP_ERROR.
add_custom_target(sqisign_kmp_cargo
    COMMAND ${CMAKE_COMMAND} -E env ${SQISIGN_KMP_RUST_ENV} CARGO_PROFILE_RELEASE_LTO=fat
            CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1
            ${SQISIGN_KMP_CARGO} build --release --locked -p sqisign-capi ${SQISIGN_KMP_TARGET_ARGS}
            --target-dir ${SQISIGN_KMP_CARGO_DIR}
    WORKING_DIRECTORY ${SQISIGN_KMP_RUST_DIR}
    BYPRODUCTS ${SQISIGN_KMP_RUST_LIB}
    COMMENT "Building the Rust sqisign-capi for ${SQISIGN_KMP_TRIPLE_NAME}"
    VERBATIM)

# randombytes() from the vendor sources, as in sqisign_common_sys and sqisign_common_test.
if(SQISIGN_KMP_KAT)
    add_library(sqisign_kmp_rng STATIC
        ${SQISIGN_ROOT}/src/common/ref/randombytes_ctrdrbg.c ${SQISIGN_ROOT}/src/common/ref/aes_c.c)
    target_compile_definitions(sqisign_kmp_rng PRIVATE RANDOMBYTES_C)
    target_include_directories(sqisign_kmp_rng PRIVATE ${SQISIGN_ROOT}/src/common/ref/include)
else()
    add_library(sqisign_kmp_rng STATIC ${SQISIGN_ROOT}/src/common/generic/randombytes_system.c)
    target_compile_definitions(sqisign_kmp_rng PRIVATE RANDOMBYTES_SYSTEM)
endif()
target_include_directories(sqisign_kmp_rng PRIVATE ${SQISIGN_ROOT}/src/common/generic/include ${SQISIGN_ROOT}/include)
add_dependencies(sqisign_kmp_rng sqisign_kmp_cargo)

# Link order for order-sensitive linkers: the KAT glue, the Rust API, randombytes(), the Rust runtime's libraries.
if(SQISIGN_KMP_KAT)
    add_library(sqisign_kmp STATIC src/sqisign_kmp_kat.c)
    target_include_directories(sqisign_kmp PUBLIC include PRIVATE ${SQISIGN_ROOT}/include)
    set(SQISIGN_KMP_GLUE_TARGETS sqisign_kmp)
    set(SQISIGN_KMP_ARCHIVES sqisign_kmp sqisign_kmp_rng sqisign_kmp_cargo ${SQISIGN_KMP_RUST_LIB})
    set(SQISIGN_KMP_ARCHIVE_FILES $<TARGET_FILE:sqisign_kmp> ${SQISIGN_KMP_RUST_LIB} $<TARGET_FILE:sqisign_kmp_rng>)
    set(scope PUBLIC)
else()
    add_library(sqisign_kmp INTERFACE)
    target_include_directories(sqisign_kmp INTERFACE include)
    set(SQISIGN_KMP_GLUE_TARGETS "")
    set(SQISIGN_KMP_ARCHIVES sqisign_kmp_rng sqisign_kmp_cargo ${SQISIGN_KMP_RUST_LIB})
    set(SQISIGN_KMP_ARCHIVE_FILES ${SQISIGN_KMP_RUST_LIB} $<TARGET_FILE:sqisign_kmp_rng>)
    set(scope INTERFACE)
endif()
target_link_libraries(sqisign_kmp ${scope} ${SQISIGN_KMP_RUST_LIB} sqisign_kmp_rng ${SQISIGN_KMP_RUST_SYSLIBS})
