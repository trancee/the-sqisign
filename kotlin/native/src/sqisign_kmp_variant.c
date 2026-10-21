// SPDX-License-Identifier: Apache-2.0

// Compiled once per parameter set with SQISIGN_VARIANT defined; exposes sqisign_kmp_<variant>_* wrappers so the
// dispatcher does not depend on the build-type-specific namespacing of the NIST API symbols.

#include <api.h>
#include <stddef.h>
#include <stdint.h>

#include "sqisign_kmp_variants.h"

#define KMP_JOIN_(v, name) sqisign_kmp_##v##_##name
#define KMP_JOIN(v, name) KMP_JOIN_(v, name)
#define KMP_FN(name) KMP_JOIN(SQISIGN_VARIANT, name)

void
KMP_FN(sizes)(size_t *public_key_bytes, size_t *secret_key_bytes, size_t *signature_bytes)
{
    *public_key_bytes = CRYPTO_PUBLICKEYBYTES;
    *secret_key_bytes = CRYPTO_SECRETKEYBYTES;
    *signature_bytes = CRYPTO_BYTES;
}

int
KMP_FN(keypair)(uint8_t *pk, uint8_t *sk)
{
    return crypto_sign_keypair(pk, sk);
}

int
KMP_FN(sign)(uint8_t *sig, const uint8_t *m, size_t mlen, const uint8_t *sk)
{
    unsigned long long siglen = 0;
    int ret = crypto_sign_signature(sig, &siglen, m, (unsigned long long)mlen, sk);
    if (ret != 0 || siglen != CRYPTO_BYTES) {
        return -1;
    }
    return 0;
}

int
KMP_FN(verify)(const uint8_t *sig, size_t siglen, const uint8_t *m, size_t mlen, const uint8_t *pk)
{
    return crypto_sign_verify(sig, (unsigned long long)siglen, m, (unsigned long long)mlen, pk);
}
