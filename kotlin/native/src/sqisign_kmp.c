// SPDX-License-Identifier: Apache-2.0

#include <sqisign_kmp.h>
#include <string.h>

#include "sqisign_kmp_variants.h"

int
sqisign_kmp_sizes(int set, size_t *public_key_bytes, size_t *secret_key_bytes, size_t *signature_bytes)
{
    switch (set) {
        case SQISIGN_KMP_P324_3:
            sqisign_kmp_p324_3_sizes(public_key_bytes, secret_key_bytes, signature_bytes);
            return SQISIGN_KMP_OK;
        case SQISIGN_KMP_P500_27:
            sqisign_kmp_p500_27_sizes(public_key_bytes, secret_key_bytes, signature_bytes);
            return SQISIGN_KMP_OK;
        case SQISIGN_KMP_P664_17:
            sqisign_kmp_p664_17_sizes(public_key_bytes, secret_key_bytes, signature_bytes);
            return SQISIGN_KMP_OK;
        default:
            return SQISIGN_KMP_INVALID_SET;
    }
}

int
sqisign_kmp_keypair(int set, uint8_t *pk, uint8_t *sk)
{
    switch (set) {
        case SQISIGN_KMP_P324_3:
            return sqisign_kmp_p324_3_keypair(pk, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P500_27:
            return sqisign_kmp_p500_27_keypair(pk, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P664_17:
            return sqisign_kmp_p664_17_keypair(pk, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        default:
            return SQISIGN_KMP_INVALID_SET;
    }
}

int
sqisign_kmp_sign(int set, uint8_t *sig, const uint8_t *m, size_t mlen, const uint8_t *sk)
{
    switch (set) {
        case SQISIGN_KMP_P324_3:
            return sqisign_kmp_p324_3_sign(sig, m, mlen, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P500_27:
            return sqisign_kmp_p500_27_sign(sig, m, mlen, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P664_17:
            return sqisign_kmp_p664_17_sign(sig, m, mlen, sk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        default:
            return SQISIGN_KMP_INVALID_SET;
    }
}

int
sqisign_kmp_verify(int set, const uint8_t *sig, size_t siglen, const uint8_t *m, size_t mlen, const uint8_t *pk)
{
    switch (set) {
        case SQISIGN_KMP_P324_3:
            return sqisign_kmp_p324_3_verify(sig, siglen, m, mlen, pk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P500_27:
            return sqisign_kmp_p500_27_verify(sig, siglen, m, mlen, pk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        case SQISIGN_KMP_P664_17:
            return sqisign_kmp_p664_17_verify(sig, siglen, m, mlen, pk) == 0 ? SQISIGN_KMP_OK : SQISIGN_KMP_ERROR;
        default:
            return SQISIGN_KMP_INVALID_SET;
    }
}

void
sqisign_kmp_secure_clear(void *mem, size_t len)
{
    // Volatile function pointer prevents the compiler from proving the store dead and eliding it.
    static void *(*const volatile memset_v)(void *, int, size_t) = &memset;
    if (mem != NULL && len > 0) {
        memset_v(mem, 0, len);
    }
}
