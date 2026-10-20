// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KMP_H
#define SQISIGN_KMP_H

// Stable, build-type-independent C entry points used by the Kotlin bindings (JNI and cinterop).
//
// All three parameter sets are linked into one binary. The `set` argument selects the parameter set; the values match
// the ordinals of the Kotlin `ParameterSet` enum.

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C"
{
#endif

#define SQISIGN_KMP_P324_3 0
#define SQISIGN_KMP_P500_27 1
#define SQISIGN_KMP_P664_17 2

#define SQISIGN_KMP_OK 0
#define SQISIGN_KMP_ERROR (-1)
#define SQISIGN_KMP_INVALID_SET (-2)

/** Writes the encoded sizes for `set`. Returns SQISIGN_KMP_OK or SQISIGN_KMP_INVALID_SET. */
int sqisign_kmp_sizes(int set, size_t *public_key_bytes, size_t *secret_key_bytes, size_t *signature_bytes);

/** Generates a key pair. `pk` and `sk` must hold the sizes reported by sqisign_kmp_sizes(). */
int sqisign_kmp_keypair(int set, uint8_t *pk, uint8_t *sk);

/** Writes a detached signature of exactly `signature_bytes` bytes to `sig`. */
int sqisign_kmp_sign(int set, uint8_t *sig, const uint8_t *m, size_t mlen, const uint8_t *sk);

/** Returns SQISIGN_KMP_OK if `sig` is a valid signature of `m` under `pk`, SQISIGN_KMP_ERROR otherwise. */
int sqisign_kmp_verify(int set, const uint8_t *sig, size_t siglen, const uint8_t *m, size_t mlen, const uint8_t *pk);

/** Zeroizes `len` bytes at `mem` in a way the compiler cannot elide. */
void sqisign_kmp_secure_clear(void *mem, size_t len);

#ifdef __cplusplus
}
#endif

#endif
