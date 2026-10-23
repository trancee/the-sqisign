# SPDX-License-Identifier: Apache-2.0
#
# Native build for the Kotlin Multiplatform bindings. Nests the upstream SQIsign build unchanged and adds:
#   - per-variant copies of the generically namespaced common sources (prng.c, mem.c), so that all three parameter
#     sets can be linked into one binary with SQISIGN_NAMESPACE_GENERIC_AS_VARIANT (otherwise the level-dependent
#     sqisign_gen_* symbols collide and the first definition silently wins);
#   - a stable sqisign_kmp_* C API (include/sqisign_kmp.h);
#   - SQISIGN_KMP_OUTPUT=jni:    shared library libsqisign_jni for the JVM and Android;
#     SQISIGN_KMP_OUTPUT=static: one merged static archive libsqisign_kmp_all.a for Kotlin/Native cinterop;
#   - SQISIGN_KMP_BACKEND=rust:  the same C API from the Rust port in ../../rust instead (ADR 0004, rust.cmake).

cmake_minimum_required(VERSION 3.13)
project(sqisign_kmp C ASM)

set(SQISIGN_KMP_OUTPUT "jni" CACHE STRING "Artifact to build: jni or static")
set_property(CACHE SQISIGN_KMP_OUTPUT PROPERTY STRINGS jni static)

# Test-only build for reproducing the NIST KATs (keygen and signing): links upstream's sqisign_<set>_test libraries,
# whose randombytes() is the seedable NIST CTR-DRBG, and adds sqisign_kmp_kat_keypair_sign(). It produces
# libsqisign_kat_jni (jni) or libsqisign_kmp_kat.a (static) so it can never be mistaken for the shipped library.
option(SQISIGN_KMP_KAT "Build the test-only KAT library with the deterministic NIST DRBG" OFF)
if(SQISIGN_KMP_KAT)
    set(SQISIGN_KMP_COMMON sqisign_common_test)
    set(SQISIGN_KMP_LIB_SUFFIX _test)
    set(SQISIGN_KMP_JNI_NAME sqisign_kat_jni)
    set(SQISIGN_KMP_ARCHIVE_NAME libsqisign_kmp_kat.a)
else()
    set(SQISIGN_KMP_COMMON sqisign_common_sys)
    set(SQISIGN_KMP_LIB_SUFFIX "")
    set(SQISIGN_KMP_JNI_NAME sqisign_jni)
    set(SQISIGN_KMP_ARCHIVE_NAME libsqisign_kmp_all.a)
endif()

get_filename_component(SQISIGN_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(SQISIGN_KMP_VARIANTS p324_3 p500_27 p664_17)

# The implementation behind the sqisign_kmp_* API: the vendor C code (default and release backend) or the opt-in,
# unreviewed Rust port (ADR 0004).
set(SQISIGN_KMP_BACKEND "c" CACHE STRING "Core implementation: c or rust")
set_property(CACHE SQISIGN_KMP_BACKEND PROPERTY STRINGS c rust)
if(NOT SQISIGN_KMP_BACKEND MATCHES "^(c|rust)$")
    message(FATAL_ERROR "Unknown SQISIGN_KMP_BACKEND: ${SQISIGN_KMP_BACKEND}")
endif()

if(SQISIGN_KMP_BACKEND STREQUAL "rust")
    set(SQISIGN_KMP_IPO OFF)
else()
# Upstream enables LTO for release builds. Keep it only for desktop JNI builds with Clang: on Android below API 29,
# lld's LTO codegen emits native ELF TLS while regular objects use emulated TLS (undefined __tls_get_addr); GCC's LTO
# drops the thread-local PRNG state referenced across archives; and LLVM bitcode in the Apple static archive would be
# consumed by a different linker toolchain inside Kotlin/Native.
if(SQISIGN_KMP_OUTPUT STREQUAL "jni" AND NOT ANDROID AND CMAKE_C_COMPILER_ID MATCHES "Clang")
    set(SQISIGN_KMP_IPO_DEFAULT ON)
else()
    set(SQISIGN_KMP_IPO_DEFAULT OFF)
endif()
option(SQISIGN_KMP_IPO "Keep upstream link-time optimization" ${SQISIGN_KMP_IPO_DEFAULT})
endif()

# Test-only: Clang source-based coverage of the KMP glue (src/*.c), not the upstream library. Coverage needs the
# glue's objects outside LTO, so it turns IPO off.
option(SQISIGN_KMP_COVERAGE "Instrument the KMP glue for Clang source-based coverage" OFF)
if(SQISIGN_KMP_COVERAGE)
    if(NOT CMAKE_C_COMPILER_ID MATCHES "Clang" OR NOT SQISIGN_KMP_OUTPUT STREQUAL "jni")
        message(FATAL_ERROR "SQISIGN_KMP_COVERAGE requires Clang and SQISIGN_KMP_OUTPUT=jni")
    endif()
    set(SQISIGN_KMP_IPO OFF)
endif()

# The upstream static libraries end up inside a shared library for JNI.
if(SQISIGN_KMP_OUTPUT STREQUAL "jni")
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
endif()

if(SQISIGN_KMP_BACKEND STREQUAL "rust")
    include(rust.cmake)
else()
# Upstream target detection reads CMAKE_SYSTEM_PROCESSOR, which is empty when cross-compiling for iOS.
if(APPLE AND NOT CMAKE_SYSTEM_PROCESSOR AND CMAKE_OSX_ARCHITECTURES)
    list(GET CMAKE_OSX_ARCHITECTURES 0 CMAKE_SYSTEM_PROCESSOR)
endif()
# Upstream probes the ARM PMU cycle counter with try_run(), which cannot run when cross-compiling. The counter is only
# used by benchmarks and is not readable from Android user space, so report the probe as failed.
if(CMAKE_CROSSCOMPILING)
    set(TEMP_RUN_RESULT "FAILED_TO_RUN" CACHE STRING "Cross-compiling: cycle counter probe not run")
endif()

set(ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_SIGN ON CACHE BOOL "" FORCE)
set(ENABLE_CT_TESTING OFF CACHE BOOL "" FORCE)
add_compile_definitions(SQISIGN_NAMESPACE_GENERIC_AS_VARIANT)
add_subdirectory(${SQISIGN_ROOT} sqisign EXCLUDE_FROM_ALL)

function(sqisign_kmp_disable_ipo dir)
    get_directory_property(targets DIRECTORY ${dir} BUILDSYSTEM_TARGETS)
    foreach(t ${targets})
        get_target_property(type ${t} TYPE)
        if(NOT type STREQUAL "UTILITY" AND NOT type STREQUAL "INTERFACE_LIBRARY")
            set_target_properties(${t} PROPERTIES INTERPROCEDURAL_OPTIMIZATION OFF)
        endif()
    endforeach()
    get_directory_property(subdirs DIRECTORY ${dir} SUBDIRECTORIES)
    foreach(s ${subdirs})
        sqisign_kmp_disable_ipo(${s})
    endforeach()
endfunction()
if(NOT SQISIGN_KMP_IPO)
    sqisign_kmp_disable_ipo(${SQISIGN_ROOT})
endif()

# Reuse the upstream directory-level definitions (build type, target, ENABLE_SIGN, ...) and C flags.
get_directory_property(SQISIGN_DEFS DIRECTORY ${SQISIGN_ROOT} COMPILE_DEFINITIONS)
get_directory_property(SQISIGN_C_FLAGS DIRECTORY ${SQISIGN_ROOT} DEFINITION CMAKE_C_FLAGS)
separate_arguments(SQISIGN_C_FLAGS)

set(SQISIGN_KMP_OBJECTS "")
foreach(V ${SQISIGN_KMP_VARIANTS})
    # Per-variant PRNG and memory helpers (their names carry the variant namespace). A static library rather than
    # objects in sqisign_kmp, so it can be ordered after the upstream libraries that use it.
    add_library(sqisign_kmp_common_${V} STATIC
        ${SQISIGN_ROOT}/src/common/generic/prng.c
        ${SQISIGN_ROOT}/src/common/generic/mem.c)
    target_include_directories(sqisign_kmp_common_${V} PRIVATE
        ${SQISIGN_ROOT}/src/common/generic/include ${SQISIGN_ROOT}/include)
    target_compile_definitions(sqisign_kmp_common_${V} PRIVATE ${SQISIGN_DEFS} SQISIGN_VARIANT=${V})
    target_compile_options(sqisign_kmp_common_${V} PRIVATE ${SQISIGN_C_FLAGS})
    target_link_libraries(sqisign_kmp_common_${V} PUBLIC ${SQISIGN_KMP_COMMON})

    # The upstream component libraries use these helpers and SHAKE/randombytes from the common library without
    # declaring it. Order-sensitive linkers (GNU ld) need the providers after their users, so declare the edges.
    foreach(C signature verification id2iso quaternion hd ec gf mp precomp)
        if(TARGET sqisign_${C}_${V})
            target_link_libraries(sqisign_${C}_${V} sqisign_kmp_common_${V} ${SQISIGN_KMP_COMMON})
        endif()
    endforeach()

    add_library(sqisign_kmp_variant_${V} OBJECT src/sqisign_kmp_variant.c)
    target_compile_definitions(sqisign_kmp_variant_${V} PRIVATE ${SQISIGN_DEFS})
    target_compile_options(sqisign_kmp_variant_${V} PRIVATE ${SQISIGN_C_FLAGS})
    target_link_libraries(sqisign_kmp_variant_${V} PRIVATE sqisign_${V}${SQISIGN_KMP_LIB_SUFFIX}_nistapi)

    list(APPEND SQISIGN_KMP_OBJECTS $<TARGET_OBJECTS:sqisign_kmp_variant_${V}>)
endforeach()

add_library(sqisign_kmp STATIC src/sqisign_kmp.c ${SQISIGN_KMP_OBJECTS})
target_include_directories(sqisign_kmp PUBLIC include)
target_compile_options(sqisign_kmp PRIVATE ${SQISIGN_C_FLAGS})
if(SQISIGN_KMP_KAT)
    target_sources(sqisign_kmp PRIVATE src/sqisign_kmp_kat.c)
    target_include_directories(sqisign_kmp PRIVATE ${SQISIGN_ROOT}/include)
endif()
foreach(V ${SQISIGN_KMP_VARIANTS})
    # LINK_ONLY: each nistapi target carries a conflicting SQISIGN_VARIANT definition as a usage requirement.
    target_link_libraries(sqisign_kmp INTERFACE
        $<LINK_ONLY:sqisign_${V}${SQISIGN_KMP_LIB_SUFFIX}_nistapi> $<LINK_ONLY:sqisign_kmp_common_${V}>)
endforeach()

# Every archive the NIST API libraries depend on, merged into one archive for cinterop.
set(SQISIGN_KMP_ARCHIVES sqisign_kmp ${SQISIGN_KMP_COMMON})
foreach(V ${SQISIGN_KMP_VARIANTS})
    string(TOUPPER ${V} VU)
    list(APPEND SQISIGN_KMP_ARCHIVES sqisign_${V}${SQISIGN_KMP_LIB_SUFFIX}_nistapi sqisign_${V}${SQISIGN_KMP_LIB_SUFFIX}
        sqisign_kmp_common_${V}
        ${LIB_SIGNATURE_${VU}} ${LIB_VERIFICATION_${VU}} ${LIB_ID2ISO_${VU}} ${LIB_QUATERNION_${VU}}
        ${LIB_MP_${VU}} ${LIB_GF_${VU}} ${LIB_EC_${VU}} ${LIB_HD_${VU}} ${LIB_PRECOMP_${VU}})
endforeach()
list(REMOVE_DUPLICATES SQISIGN_KMP_ARCHIVES)
set(SQISIGN_KMP_ARCHIVE_FILES "")
foreach(T ${SQISIGN_KMP_ARCHIVES})
    list(APPEND SQISIGN_KMP_ARCHIVE_FILES $<TARGET_FILE:${T}>)
endforeach()
set(SQISIGN_KMP_GLUE_TARGETS sqisign_kmp)
foreach(V ${SQISIGN_KMP_VARIANTS})
    list(APPEND SQISIGN_KMP_GLUE_TARGETS sqisign_kmp_variant_${V})
endforeach()
endif()

if(SQISIGN_KMP_OUTPUT STREQUAL "jni")
    if(NOT ANDROID)
        # Only the JNI headers are needed; libjvm is provided by the running VM.
        if(NOT JNI_INCLUDE_DIRS)
            find_package(JNI REQUIRED COMPONENTS JVM)
        endif()
    endif()
    if(SQISIGN_KMP_KAT)
        add_library(sqisign_jni SHARED src/sqisign_kat_jni.c)
    else()
        add_library(sqisign_jni SHARED src/sqisign_jni.c)
    endif()
    set_target_properties(sqisign_jni PROPERTIES OUTPUT_NAME ${SQISIGN_KMP_JNI_NAME})
    target_include_directories(sqisign_jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_compile_options(sqisign_jni PRIVATE ${SQISIGN_C_FLAGS})
    target_link_libraries(sqisign_jni PRIVATE sqisign_kmp)
    # Export only the JNI entry points.
    if(APPLE)
        target_link_options(sqisign_jni PRIVATE -Wl,-dead_strip "-Wl,-exported_symbol,_Java_*")
    elseif(WIN32)
        # System.mapLibraryName("sqisign_jni") is "sqisign_jni.dll" (no "lib" prefix); JNIEXPORT (dllexport) limits the exports.
        set_target_properties(sqisign_jni PROPERTIES PREFIX "")
        if(MINGW)
            # Link the MinGW/compiler runtime statically so the DLL only depends on system DLLs.
            target_link_options(sqisign_jni PRIVATE -static -Wl,--gc-sections)
        endif()
    else()
        # The version script hides everything else. (--exclude-libs is avoided: with GCC's LTO plugin it breaks
        # resolution of LTO archive symbols referenced from regular objects.)
        target_link_options(sqisign_jni PRIVATE -Wl,--gc-sections -Wl,-z,defs
            -Wl,--version-script=${CMAKE_CURRENT_LIST_DIR}/src/sqisign_jni.map)
    endif()
    install(TARGETS sqisign_jni LIBRARY DESTINATION . RUNTIME DESTINATION .)
    if(SQISIGN_KMP_COVERAGE)
        set(SQISIGN_KMP_COVERAGE_FLAGS -fprofile-instr-generate -fcoverage-mapping)
        foreach(T sqisign_jni ${SQISIGN_KMP_GLUE_TARGETS})
            target_compile_options(${T} PRIVATE ${SQISIGN_KMP_COVERAGE_FLAGS})
        endforeach()
        target_link_options(sqisign_jni PRIVATE -fprofile-instr-generate)
    endif()
    if(SQISIGN_KMP_IPO)
        get_directory_property(SQISIGN_UPSTREAM_IPO DIRECTORY ${SQISIGN_ROOT} DEFINITION CMAKE_INTERPROCEDURAL_OPTIMIZATION)
    endif()
    if(SQISIGN_KMP_IPO AND SQISIGN_UPSTREAM_IPO)
        # Match the upstream libraries (IPO is a directory-scoped default there), so the whole link is LTO.
        set(SQISIGN_KMP_OWN_TARGETS sqisign_kmp sqisign_jni)
        foreach(V ${SQISIGN_KMP_VARIANTS})
            list(APPEND SQISIGN_KMP_OWN_TARGETS sqisign_kmp_common_${V} sqisign_kmp_variant_${V})
        endforeach()
        set_target_properties(${SQISIGN_KMP_OWN_TARGETS} PROPERTIES INTERPROCEDURAL_OPTIMIZATION ON)
    endif()
elseif(SQISIGN_KMP_OUTPUT STREQUAL "static")
    if(NOT APPLE)
        message(FATAL_ERROR "SQISIGN_KMP_OUTPUT=static is only supported for Apple targets")
    endif()
    set(SQISIGN_KMP_MERGED ${CMAKE_CURRENT_BINARY_DIR}/${SQISIGN_KMP_ARCHIVE_NAME})
    # `cmake --install --strip` strips only the JNI libraries, not this archive. Outside Debug builds, drop its debug
    # information (the Rust standard library ships with DWARF); strip -S keeps the symbols the final link needs.
    if(CMAKE_BUILD_TYPE STREQUAL "Debug")
        set(SQISIGN_KMP_STRIP_DEBUG "")
    else()
        set(SQISIGN_KMP_STRIP_DEBUG COMMAND xcrun strip -S ${SQISIGN_KMP_MERGED})
    endif()
    if(SQISIGN_KMP_KAT)
        # A test binary links this archive next to libsqisign_kmp_all.a, and both define the same SQIsign symbols. Prelink
        # everything into one object that exports only sqisign_kmp_kat_*, so the DRBG chain cannot mix with the
        # production one (ld -r turns the non-exported symbols local).
        set(SQISIGN_KMP_PRELINKED ${CMAKE_CURRENT_BINARY_DIR}/sqisign_kmp_kat.o)
        list(GET CMAKE_OSX_ARCHITECTURES 0 SQISIGN_KMP_ARCH)
        # ld -r drops the platform load command, which the final link checks.
        if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
            if(CMAKE_OSX_SYSROOT MATCHES "[Ss]imulator")
                set(SQISIGN_KMP_PLATFORM ios-simulator)
            else()
                set(SQISIGN_KMP_PLATFORM ios)
            endif()
        else()
            set(SQISIGN_KMP_PLATFORM macos)
        endif()
        add_custom_command(
            OUTPUT ${SQISIGN_KMP_MERGED}
            COMMAND xcrun libtool -static -no_warning_for_no_symbols -o ${SQISIGN_KMP_MERGED}.in ${SQISIGN_KMP_ARCHIVE_FILES}
            COMMAND xcrun ld -r -arch ${SQISIGN_KMP_ARCH} -u _sqisign_kmp_kat_keypair_sign -exported_symbol _sqisign_kmp_kat_keypair_sign
                    -platform_version ${SQISIGN_KMP_PLATFORM} ${CMAKE_OSX_DEPLOYMENT_TARGET} ${CMAKE_OSX_DEPLOYMENT_TARGET}
                    -o ${SQISIGN_KMP_PRELINKED} ${SQISIGN_KMP_MERGED}.in
            COMMAND ${CMAKE_COMMAND} -E rm -f ${SQISIGN_KMP_MERGED}
            COMMAND xcrun libtool -static -o ${SQISIGN_KMP_MERGED} ${SQISIGN_KMP_PRELINKED}
            ${SQISIGN_KMP_STRIP_DEBUG}
            DEPENDS ${SQISIGN_KMP_ARCHIVES}
            COMMENT "Prelinking the KAT archive ${SQISIGN_KMP_ARCHIVE_NAME}"
            VERBATIM)
    else()
        add_custom_command(
            OUTPUT ${SQISIGN_KMP_MERGED}
            COMMAND xcrun libtool -static -no_warning_for_no_symbols -o ${SQISIGN_KMP_MERGED} ${SQISIGN_KMP_ARCHIVE_FILES}
            ${SQISIGN_KMP_STRIP_DEBUG}
            DEPENDS ${SQISIGN_KMP_ARCHIVES}
            COMMENT "Merging SQIsign archives into ${SQISIGN_KMP_ARCHIVE_NAME}"
            VERBATIM)
    endif()
    add_custom_target(sqisign_kmp_all ALL DEPENDS ${SQISIGN_KMP_MERGED})
    install(FILES ${SQISIGN_KMP_MERGED} DESTINATION lib)
    install(FILES include/sqisign_kmp.h include/sqisign_kmp_kat.h DESTINATION include)
else()
    message(FATAL_ERROR "Unknown SQISIGN_KMP_OUTPUT: ${SQISIGN_KMP_OUTPUT}")
endif()
