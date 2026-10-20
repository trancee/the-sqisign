// SPDX-License-Identifier: Apache-2.0

#include <rng.h>
#include <string.h>

#include <sqisign_kmp.h>
#include <sqisign_kmp_kat.h>

// Upstream compiles with -fvisibility=hidden; the Apple KAT archive is prelinked with this as its only export.
#if defined(__GNUC__) && !defined(_WIN32)
__attribute__((visibility("default")))
#endif
int
sqisign_kmp_kat_keypair_sign(int set,
                             const uint8_t seed[SQISIGN_KMP_KAT_SEED_BYTES],
                             const uint8_t *m,
                             size_t mlen,
                             uint8_t *pk,
                             uint8_t *sk,
                             uint8_t *sig)
{
    size_t pk_bytes, sk_bytes, sig_bytes;
    int ret = sqisign_kmp_sizes(set, &pk_bytes, &sk_bytes, &sig_bytes);
    if (ret != SQISIGN_KMP_OK) {
        return ret;
    }
    // randombytes_init() takes a non-const pointer.
    unsigned char entropy[SQISIGN_KMP_KAT_SEED_BYTES];
    memcpy(entropy, seed, sizeof(entropy));
    randombytes_init(entropy, NULL, 256);

    ret = sqisign_kmp_keypair(set, pk, sk);
    if (ret == SQISIGN_KMP_OK) {
        ret = sqisign_kmp_sign(set, sig, m, mlen, sk);
    }
    return ret;
}
