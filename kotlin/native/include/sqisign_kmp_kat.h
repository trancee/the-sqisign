// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KMP_KAT_H
#define SQISIGN_KMP_KAT_H

// Test-only entry point, built only with -DSQISIGN_KMP_KAT=ON. That build links the upstream test libraries, whose
// randombytes() is the NIST CTR-DRBG instead of the operating system's RNG, so it must never ship.

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C"
{
#endif

#define SQISIGN_KMP_KAT_SEED_BYTES 48

/**
 * Reproduces one NIST KAT entry: seeds the calling thread's CTR-DRBG with `seed`, generates a key pair into `pk` and
 * `sk`, then writes the detached signature of `m` to `sig` (the order PQCgenKAT_sign.c and test/test_kat.c use).
 * Buffer sizes are those reported by sqisign_kmp_sizes(). Returns SQISIGN_KMP_OK, SQISIGN_KMP_ERROR or
 * SQISIGN_KMP_INVALID_SET.
 */
int sqisign_kmp_kat_keypair_sign(int set,
                                 const uint8_t seed[SQISIGN_KMP_KAT_SEED_BYTES],
                                 const uint8_t *m,
                                 size_t mlen,
                                 uint8_t *pk,
                                 uint8_t *sk,
                                 uint8_t *sig);

#ifdef __cplusplus
}
#endif

#endif
