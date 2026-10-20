// SPDX-License-Identifier: Apache-2.0

// Test-only JNI bridge (libsqisign_kat_jni), loaded by org.sqisign.SqiSignKatJni in the JVM tests.

#include <jni.h>
#include <stdlib.h>

#include <sqisign_kmp.h>
#include <sqisign_kmp_kat.h>

JNIEXPORT jint JNICALL
Java_org_sqisign_SqiSignKatJni_keypairSign(JNIEnv *env,
                                           jclass clazz,
                                           jint set,
                                           jbyteArray seed,
                                           jbyteArray msg,
                                           jbyteArray pk,
                                           jbyteArray sk,
                                           jbyteArray sig)
{
    (void)clazz;
    if ((*env)->GetArrayLength(env, seed) != SQISIGN_KMP_KAT_SEED_BYTES) {
        return SQISIGN_KMP_ERROR;
    }
    jsize msg_len = (*env)->GetArrayLength(env, msg);
    jsize pk_len = (*env)->GetArrayLength(env, pk);
    jsize sk_len = (*env)->GetArrayLength(env, sk);
    jsize sig_len = (*env)->GetArrayLength(env, sig);
    uint8_t seed_buf[SQISIGN_KMP_KAT_SEED_BYTES];
    uint8_t *msg_buf = malloc(msg_len > 0 ? (size_t)msg_len : 1);
    uint8_t *pk_buf = malloc((size_t)pk_len);
    uint8_t *sk_buf = malloc((size_t)sk_len);
    uint8_t *sig_buf = malloc((size_t)sig_len);
    int ret = SQISIGN_KMP_ERROR;
    if (msg_buf != NULL && pk_buf != NULL && sk_buf != NULL && sig_buf != NULL) {
        (*env)->GetByteArrayRegion(env, seed, 0, SQISIGN_KMP_KAT_SEED_BYTES, (jbyte *)seed_buf);
        (*env)->GetByteArrayRegion(env, msg, 0, msg_len, (jbyte *)msg_buf);
        ret = sqisign_kmp_kat_keypair_sign(set, seed_buf, msg_buf, (size_t)msg_len, pk_buf, sk_buf, sig_buf);
        if (ret == SQISIGN_KMP_OK) {
            (*env)->SetByteArrayRegion(env, pk, 0, pk_len, (const jbyte *)pk_buf);
            (*env)->SetByteArrayRegion(env, sk, 0, sk_len, (const jbyte *)sk_buf);
            (*env)->SetByteArrayRegion(env, sig, 0, sig_len, (const jbyte *)sig_buf);
        }
    }
    free(msg_buf);
    free(pk_buf);
    free(sk_buf);
    free(sig_buf);
    return ret;
}
