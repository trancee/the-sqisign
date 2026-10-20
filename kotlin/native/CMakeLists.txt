# SPDX-License-Identifier: Apache-2.0
#
# Native build for the Kotlin Multiplatform bindings. Nests the upstream SQIsign build unchanged and adds:
#   - per-variant copies of the generically namespaced common sources (prng.c, mem.c), so that all three parameter
#     sets can be linked into one binary with SQISIGN_NAMESPACE_GENERIC_AS_VARIANT (otherwise the level-dependent
#     sqisign_gen_* symbols collide and the first definition silently wins);
#   - a stable sqisign_kmp_* C API (include/sqisign_kmp.h);
#   - SQISIGN_KMP_OUTPUT=jni:    shared library libsqisign_jni for the JVM and Android;
#     SQISIGN_KMP_OUTPUT=static: one merged static archive libsqisign_kmp_all.a for Kotlin/Native cinterop.

cmake_minimum_required(VERSION 3.13)
project(sqisign_kmp C ASM)

set(SQISIGN_KMP_OUTPUT "jni" CACHE STRING "Artifact to build: jni or static")
set_property(CACHE SQISIGN_KMP_OUTPUT PROPERTY STRINGS jni static)

get_filename_component(SQISIGN_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(SQISIGN_KMP_VARIANTS p324_3 p500_27 p664_17)

# Upstream enables LTO for release builds. Keep it only for desktop JNI builds: on Android below API 29, lld's LTO
# codegen emits native ELF TLS while regular objects use emulated TLS (undefined __tls_get_addr), and LLVM bitcode in
# the Apple static archive would be consumed by a different linker toolchain inside Kotlin/Native.
if(SQISIGN_KMP_OUTPUT STREQUAL "jni" AND NOT ANDROID)
    set(SQISIGN_KMP_IPO_DEFAULT ON)
else()
    set(SQISIGN_KMP_IPO_DEFAULT OFF)
endif()
option(SQISIGN_KMP_IPO "Keep upstream link-time optimization" ${SQISIGN_KMP_IPO_DEFAULT})

# Upstream target detection reads CMAKE_SYSTEM_PROCESSOR, which is empty when cross-compiling for iOS.
if(APPLE AND NOT CMAKE_SYSTEM_PROCESSOR AND CMAKE_OSX_ARCHITECTURES)
    list(GET CMAKE_OSX_ARCHITECTURES 0 CMAKE_SYSTEM_PROCESSOR)
endif()
# Upstream probes the ARM PMU cycle counter with try_run(), which cannot run when cross-compiling. The counter is only
# used by benchmarks and is not readable from Android user space, so report the probe as failed.
if(CMAKE_CROSSCOMPILING)
    set(TEMP_RUN_RESULT "FAILED_TO_RUN" CACHE STRING "Cross-compiling: cycle counter probe not run")
endif()

# The upstream static libraries end up inside a shared library for JNI.
if(SQISIGN_KMP_OUTPUT STREQUAL "jni")
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
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
    add_library(sqisign_kmp_common_${V} OBJECT
        ${SQISIGN_ROOT}/src/common/generic/prng.c
        ${SQISIGN_ROOT}/src/common/generic/mem.c)
    target_include_directories(sqisign_kmp_common_${V} PRIVATE
        ${SQISIGN_ROOT}/src/common/generic/include ${SQISIGN_ROOT}/include)
    target_compile_definitions(sqisign_kmp_common_${V} PRIVATE ${SQISIGN_DEFS} SQISIGN_VARIANT=${V})
    target_compile_options(sqisign_kmp_common_${V} PRIVATE ${SQISIGN_C_FLAGS})

    add_library(sqisign_kmp_variant_${V} OBJECT src/sqisign_kmp_variant.c)
    target_compile_definitions(sqisign_kmp_variant_${V} PRIVATE ${SQISIGN_DEFS})
    target_compile_options(sqisign_kmp_variant_${V} PRIVATE ${SQISIGN_C_FLAGS})
    target_link_libraries(sqisign_kmp_variant_${V} PRIVATE sqisign_${V}_nistapi)

    list(APPEND SQISIGN_KMP_OBJECTS
        $<TARGET_OBJECTS:sqisign_kmp_common_${V}> $<TARGET_OBJECTS:sqisign_kmp_variant_${V}>)
endforeach()

add_library(sqisign_kmp STATIC src/sqisign_kmp.c ${SQISIGN_KMP_OBJECTS})
target_include_directories(sqisign_kmp PUBLIC include)
target_compile_options(sqisign_kmp PRIVATE ${SQISIGN_C_FLAGS})
foreach(V ${SQISIGN_KMP_VARIANTS})
    # LINK_ONLY: each nistapi target carries a conflicting SQISIGN_VARIANT definition as a usage requirement.
    target_link_libraries(sqisign_kmp INTERFACE $<LINK_ONLY:sqisign_${V}_nistapi>)
    # The upstream component libraries use SHAKE/randombytes from sqisign_common_sys without declaring it, so
    # order-sensitive linkers (GNU ld) see it before its users. Declaring the edge makes CMake place it after them.
    foreach(C signature verification id2iso quaternion hd ec gf mp precomp)
        if(TARGET sqisign_${C}_${V})
            target_link_libraries(sqisign_${C}_${V} sqisign_common_sys)
        endif()
    endforeach()
endforeach()
# prng.c in this archive uses SHAKE as well.
target_link_libraries(sqisign_kmp INTERFACE $<LINK_ONLY:sqisign_common_sys>)

if(SQISIGN_KMP_OUTPUT STREQUAL "jni")
    if(NOT ANDROID)
        # Only the JNI headers are needed; libjvm is provided by the running VM.
        if(NOT JNI_INCLUDE_DIRS)
            find_package(JNI REQUIRED COMPONENTS JVM)
        endif()
    endif()
    add_library(sqisign_jni SHARED src/sqisign_jni.c)
    target_include_directories(sqisign_jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_compile_options(sqisign_jni PRIVATE ${SQISIGN_C_FLAGS})
    target_link_libraries(sqisign_jni PRIVATE sqisign_kmp)
    # Export only the JNI entry points.
    if(APPLE)
        target_link_options(sqisign_jni PRIVATE -Wl,-dead_strip "-Wl,-exported_symbol,_Java_*")
    elseif(WIN32)
        # System.mapLibraryName("sqisign_jni") is "sqisign_jni.dll"; JNIEXPORT (dllexport) limits the exports.
        set_target_properties(sqisign_jni PROPERTIES PREFIX "")
        if(MINGW)
            # Link the MinGW/compiler runtime statically so the DLL only depends on system DLLs.
            target_link_options(sqisign_jni PRIVATE -static -Wl,--gc-sections)
        endif()
    else()
        target_link_options(sqisign_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL
            -Wl,--version-script=${CMAKE_CURRENT_LIST_DIR}/src/sqisign_jni.map)
    endif()
    install(TARGETS sqisign_jni LIBRARY DESTINATION . RUNTIME DESTINATION .)
elseif(SQISIGN_KMP_OUTPUT STREQUAL "static")
    if(NOT APPLE)
        message(FATAL_ERROR "SQISIGN_KMP_OUTPUT=static is only supported for Apple targets")
    endif()
    # Collect every archive the NIST API libraries depend on and merge them into one archive for cinterop.
    set(SQISIGN_KMP_ARCHIVES sqisign_kmp sqisign_common_sys)
    foreach(V ${SQISIGN_KMP_VARIANTS})
        string(TOUPPER ${V} VU)
        list(APPEND SQISIGN_KMP_ARCHIVES sqisign_${V}_nistapi sqisign_${V}
            ${LIB_SIGNATURE_${VU}} ${LIB_VERIFICATION_${VU}} ${LIB_ID2ISO_${VU}} ${LIB_QUATERNION_${VU}}
            ${LIB_MP_${VU}} ${LIB_GF_${VU}} ${LIB_EC_${VU}} ${LIB_HD_${VU}} ${LIB_PRECOMP_${VU}})
    endforeach()
    list(REMOVE_DUPLICATES SQISIGN_KMP_ARCHIVES)
    set(SQISIGN_KMP_ARCHIVE_FILES "")
    foreach(T ${SQISIGN_KMP_ARCHIVES})
        list(APPEND SQISIGN_KMP_ARCHIVE_FILES $<TARGET_FILE:${T}>)
    endforeach()
    set(SQISIGN_KMP_MERGED ${CMAKE_CURRENT_BINARY_DIR}/libsqisign_kmp_all.a)
    add_custom_command(
        OUTPUT ${SQISIGN_KMP_MERGED}
        COMMAND xcrun libtool -static -no_warning_for_no_symbols -o ${SQISIGN_KMP_MERGED} ${SQISIGN_KMP_ARCHIVE_FILES}
        DEPENDS ${SQISIGN_KMP_ARCHIVES}
        COMMENT "Merging SQIsign archives into libsqisign_kmp_all.a"
        VERBATIM)
    add_custom_target(sqisign_kmp_all ALL DEPENDS ${SQISIGN_KMP_MERGED})
    install(FILES ${SQISIGN_KMP_MERGED} DESTINATION lib)
    install(FILES include/sqisign_kmp.h DESTINATION include)
else()
    message(FATAL_ERROR "Unknown SQISIGN_KMP_OUTPUT: ${SQISIGN_KMP_OUTPUT}")
endif()
