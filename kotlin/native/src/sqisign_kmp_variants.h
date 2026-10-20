// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KMP_VARIANTS_H
#define SQISIGN_KMP_VARIANTS_H

#include <stddef.h>
#include <stdint.h>

#define SQISIGN_KMP_DECLARE_VARIANT(v)                                                                                 \
    void sqisign_kmp_##v##_sizes(size_t *public_key_bytes, size_t *secret_key_bytes, size_t *signature_bytes);         \
    int sqisign_kmp_##v##_keypair(uint8_t *pk, uint8_t *sk);                                                           \
    int sqisign_kmp_##v##_sign(uint8_t *sig, const uint8_t *m, size_t mlen, const uint8_t *sk);                        \
    int sqisign_kmp_##v##_verify(const uint8_t *sig, size_t siglen, const uint8_t *m, size_t mlen, const uint8_t *pk);

SQISIGN_KMP_DECLARE_VARIANT(p324_3)
SQISIGN_KMP_DECLARE_VARIANT(p500_27)
SQISIGN_KMP_DECLARE_VARIANT(p664_17)

#endif
