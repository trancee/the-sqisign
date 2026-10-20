// SPDX-License-Identifier: Apache-2.0

// JNI bridge for the JVM and Android targets. Arrays are copied into native buffers instead of pinned with
// Get*Critical, because signing runs for tens of milliseconds and must not block the garbage collector. Native copies
// of secret keys are zeroized before release.

#include <jni.h>
#include <stdlib.h>

#include <sqisign_kmp.h>

static uint8_t *
copy_in(JNIEnv *env, jbyteArray array, jsize *out_len)
{
    jsize len = (*env)->GetArrayLength(env, array);
    uint8_t *buf = malloc(len > 0 ? (size_t)len : 1);
    if (buf == NULL) {
        return NULL;
    }
    if (len > 0) {
        (*env)->GetByteArrayRegion(env, array, 0, len, (jbyte *)buf);
    }
    *out_len = len;
    return buf;
}

static void
free_secret(uint8_t *buf, jsize len)
{
    if (buf != NULL) {
        sqisign_kmp_secure_clear(buf, (size_t)len);
        free(buf);
    }
}

JNIEXPORT jint JNICALL
Java_org_sqisign_SqiSignJni_sizes(JNIEnv *env, jclass clazz, jint set, jintArray out)
{
    (void)clazz;
    size_t pk = 0, sk = 0, sig = 0;
    int ret = sqisign_kmp_sizes(set, &pk, &sk, &sig);
    if (ret == SQISIGN_KMP_OK) {
        jint values[3] = { (jint)pk, (jint)sk, (jint)sig };
        (*env)->SetIntArrayRegion(env, out, 0, 3, values);
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_sqisign_SqiSignJni_keypair(JNIEnv *env, jclass clazz, jint set, jbyteArray pk, jbyteArray sk)
{
    (void)clazz;
    jsize pk_len = (*env)->GetArrayLength(env, pk);
    jsize sk_len = (*env)->GetArrayLength(env, sk);
    uint8_t *pk_buf = malloc((size_t)pk_len);
    uint8_t *sk_buf = malloc((size_t)sk_len);
    int ret = SQISIGN_KMP_ERROR;
    if (pk_buf != NULL && sk_buf != NULL) {
        ret = sqisign_kmp_keypair(set, pk_buf, sk_buf);
        if (ret == SQISIGN_KMP_OK) {
            (*env)->SetByteArrayRegion(env, pk, 0, pk_len, (const jbyte *)pk_buf);
            (*env)->SetByteArrayRegion(env, sk, 0, sk_len, (const jbyte *)sk_buf);
        }
    }
    free(pk_buf);
    free_secret(sk_buf, sk_len);
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_sqisign_SqiSignJni_sign(JNIEnv *env, jclass clazz, jint set, jbyteArray sig, jbyteArray msg, jbyteArray sk)
{
    (void)clazz;
    jsize sig_len = (*env)->GetArrayLength(env, sig);
    jsize msg_len = 0, sk_len = 0;
    uint8_t *sig_buf = malloc((size_t)sig_len);
    uint8_t *msg_buf = copy_in(env, msg, &msg_len);
    uint8_t *sk_buf = copy_in(env, sk, &sk_len);
    int ret = SQISIGN_KMP_ERROR;
    if (sig_buf != NULL && msg_buf != NULL && sk_buf != NULL) {
        ret = sqisign_kmp_sign(set, sig_buf, msg_buf, (size_t)msg_len, sk_buf);
        if (ret == SQISIGN_KMP_OK) {
            (*env)->SetByteArrayRegion(env, sig, 0, sig_len, (const jbyte *)sig_buf);
        }
    }
    free(sig_buf);
    free(msg_buf);
    free_secret(sk_buf, sk_len);
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_sqisign_SqiSignJni_verify(JNIEnv *env, jclass clazz, jint set, jbyteArray sig, jbyteArray msg, jbyteArray pk)
{
    (void)clazz;
    jsize sig_len = 0, msg_len = 0, pk_len = 0;
    uint8_t *sig_buf = copy_in(env, sig, &sig_len);
    uint8_t *msg_buf = copy_in(env, msg, &msg_len);
    uint8_t *pk_buf = copy_in(env, pk, &pk_len);
    int ret = SQISIGN_KMP_ERROR;
    if (sig_buf != NULL && msg_buf != NULL && pk_buf != NULL) {
        ret = sqisign_kmp_verify(set, sig_buf, (size_t)sig_len, msg_buf, (size_t)msg_len, pk_buf);
    }
    free(sig_buf);
    free(msg_buf);
    free(pk_buf);
    return ret;
}
