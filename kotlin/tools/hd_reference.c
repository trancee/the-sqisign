// SPDX-License-Identifier: Apache-2.0

#include <hd.h>
#include <verification.h>
#include <encoded_sizes.h>

#include "../../src/mp/ref/lvlx/mp.c"
#undef dpint
#if REFERENCE_LEVEL == 1
#include "../../src/gf/ref/p324_3/fp_p324_3.c"
#include "../../src/precomp/ref/p324_3/e0_basis.c"
#include "../../src/precomp/ref/p324_3/ec_params.c"
#define FIELD_BYTES 41
#elif REFERENCE_LEVEL == 3
#include "../../src/gf/ref/p500_27/fp_p500_27.c"
#include "../../src/precomp/ref/p500_27/e0_basis.c"
#include "../../src/precomp/ref/p500_27/ec_params.c"
#define FIELD_BYTES 64
#elif REFERENCE_LEVEL == 5
#include "../../src/gf/ref/p664_17/fp_p664_17.c"
#include "../../src/precomp/ref/p664_17/e0_basis.c"
#include "../../src/precomp/ref/p664_17/ec_params.c"
#define FIELD_BYTES 84
#else
#error "Select a supported reference level"
#endif
#include "../../src/gf/ref/lvlx/fp.c"
#include "../../src/gf/ref/lvlx/fp2.c"
#include "../../src/ec/ref/lvlx/ec.c"
#include "../../src/ec/ref/lvlx/basis.c"
#include "../../src/ec/ref/lvlx/biextension.c"
#include "../../src/ec/ref/lvlx/isog.c"
#include "../../src/ec/ref/lvlx/normalize.c"
#include "../../src/hd/ref/lvlx/hd.c"
#include "../../src/hd/ref/lvlx/theta_structure.c"
#include "../../src/hd/ref/lvlx/gluing.c"
#include "../../src/hd/ref/lvlx/splitting.c"
#include "../../src/hd/ref/lvlx/theta_isogenies.c"
#include "../../src/common/generic/fips202.c"
#include "../../src/verification/ref/lvlx/common.c"
#include "../../src/verification/ref/lvlx/encode_public.c"

static int (*const reference_verify_chain)(uint16_t,
                                           theta_couple_curve_t *,
                                           const theta_kernel_couple_points_t *,
                                           theta_couple_curve_t *,
                                           theta_couple_point_t *,
                                           uint8_t) = theta_chain_compute_and_eval_verify;
static int captured_chain(uint16_t n,
                          theta_couple_curve_t *E12,
                          const theta_kernel_couple_points_t *ker,
                          theta_couple_curve_t *E34,
                          theta_couple_point_t *P12,
                          uint8_t numP);
#undef theta_chain_compute_and_eval_verify
#define theta_chain_compute_and_eval_verify captured_chain
#include "../../src/verification/ref/lvlx/verify.c"

#include <ctype.h>

#define FP2_BYTES (2 * FIELD_BYTES)
#define FP2_HEX_CHARS (2 * FP2_BYTES)
#define MAX_POINTS 8
#define MAX_MESSAGE 4096

static int
decode_hex(const char *hex, unsigned char *output, size_t length)
{
    if (strlen(hex) != 2 * length)
        return 0;
    for (size_t index = 0; index < length; index++) {
        int high = tolower((unsigned char)hex[2 * index]);
        int low = tolower((unsigned char)hex[2 * index + 1]);
        if (!isxdigit(high) || !isxdigit(low))
            return 0;
        high = high <= '9' ? high - '0' : high - 'a' + 10;
        low = low <= '9' ? low - '0' : low - 'a' + 10;
        output[index] = (unsigned char)((high << 4) | low);
    }
    return 1;
}

static void
encode_field(const fp2_t *value)
{
    unsigned char encoded[FP2_BYTES];
    fp2_encode(encoded, value);
    for (size_t index = 0; index < FP2_BYTES; index++)
        printf("%02x", encoded[index]);
}

static void
encode_ratio(const fp2_t *numerator, const fp2_t *denominator)
{
    if (fp2_is_zero(denominator)) {
        // '*' marks the degenerate (0 : 0) image that the reference returns as success.
        putchar(fp2_is_zero(numerator) ? '*' : '-');
        return;
    }
    fp2_t inverse, value;
    fp2_copy(&inverse, denominator);
    fp2_inv(&inverse);
    fp2_mul(&value, numerator, &inverse);
    encode_field(&value);
}

static int
read_field(fp2_t *value, int allow_missing, int *missing)
{
    char hex[FP2_HEX_CHARS + 2];
    unsigned char encoded[FP2_BYTES];
    char format[16];
    snprintf(format, sizeof(format), "%%%ds", FP2_HEX_CHARS + 1);
    if (scanf(format, hex) != 1)
        return 0;
    if (allow_missing && strcmp(hex, "-") == 0) {
        *missing = 1;
        return 1;
    }
    if (allow_missing)
        *missing = 0;
    return decode_hex(hex, encoded, FP2_BYTES) && fp2_decode(value, encoded) == UINT32_MAX;
}

static int
read_point(ec_point_t *point)
{
    int missing;
    if (!read_field(&point->x, 1, &missing))
        return 0;
    if (missing)
        ec_point_init(point);
    else
        fp2_set_one(&point->z);
    return 1;
}

static int
read_curve(ec_curve_t *curve)
{
    fp2_t A;
    return read_field(&A, 0, NULL) && ec_curve_init_from_A(curve, &A);
}

static void
print_chain(int ok, const theta_couple_curve_t *E34, const theta_couple_point_t *P12, int numP, int mode)
{
    printf("%d", ok);
    if (ok) {
        putchar(' ');
        if (mode != 2)
            encode_ratio(&E34->E1.A, &E34->E1.C);
        else
            putchar('-');
        putchar(' ');
        if (mode == 0 || mode == 2)
            encode_ratio(&E34->E2.A, &E34->E2.C);
        else
            putchar('-');
        for (int index = 0; index < numP; index++) {
            putchar(' ');
            if (mode != 2)
                encode_ratio(&P12[index].P1.x, &P12[index].P1.z);
            else
                putchar('-');
            putchar(' ');
            if (mode == 0 || mode == 2)
                encode_ratio(&P12[index].P2.x, &P12[index].P2.z);
            else
                putchar('-');
        }
    }
    putchar('\n');
}

static int
captured_chain(uint16_t n,
               theta_couple_curve_t *E12,
               const theta_kernel_couple_points_t *ker,
               theta_couple_curve_t *E34,
               theta_couple_point_t *P12,
               uint8_t numP)
{
    printf("chain %u ", n);
    encode_ratio(&E12->E1.A, &E12->E1.C);
    putchar(' ');
    encode_ratio(&E12->E2.A, &E12->E2.C);
    const ec_point_t *points[] = { &ker->T1.P1, &ker->T1.P2, &ker->T2.P1, &ker->T2.P2 };
    for (size_t index = 0; index < 4; index++) {
        putchar(' ');
        encode_ratio(&points[index]->x, &points[index]->z);
    }
    putchar(' ');
    int ok = reference_verify_chain(n, E12, ker, E34, P12, numP);
    print_chain(ok, E34, P12, 0, -1);
    return ok;
}

static int
replay_verification(void)
{
    char pk_hex[2 * PUBLICKEY_BYTES + 2], sig_hex[2 * SIGNATURE_BYTES + 2], msg_hex[2 * MAX_MESSAGE + 2];
    char pk_format[16], sig_format[16], msg_format[16];
    snprintf(pk_format, sizeof(pk_format), "%%%ds", 2 * PUBLICKEY_BYTES + 1);
    snprintf(sig_format, sizeof(sig_format), "%%%ds", 2 * SIGNATURE_BYTES + 1);
    snprintf(msg_format, sizeof(msg_format), "%%%ds", 2 * MAX_MESSAGE + 1);
    while (scanf(pk_format, pk_hex) == 1) {
        unsigned char pk_bytes[PUBLICKEY_BYTES], sig_bytes[SIGNATURE_BYTES], message[MAX_MESSAGE];
        if (scanf(sig_format, sig_hex) != 1 || scanf(msg_format, msg_hex) != 1 ||
            !decode_hex(pk_hex, pk_bytes, PUBLICKEY_BYTES) || !decode_hex(sig_hex, sig_bytes, SIGNATURE_BYTES)) {
            fprintf(stderr, "Invalid verification record\n");
            return 1;
        }
        size_t length = strcmp(msg_hex, "-") == 0 ? 0 : strlen(msg_hex) / 2;
        if (length && !decode_hex(msg_hex, message, length)) {
            fprintf(stderr, "Invalid verification message\n");
            return 1;
        }
        public_key_t pk;
        signature_t sig;
        public_key_init(&pk);
        public_key_from_bytes(&pk, pk_bytes);
        signature_from_bytes(&sig, sig_bytes);
        printf("verify %d\n", protocols_verify(&sig, &pk, message, length));
    }
    return 0;
}

static int
run_chains(void)
{
    int n, mode, numP;
    while (scanf("%d %d", &mode, &n) == 2) {
        theta_couple_curve_t E12, E34;
        theta_kernel_couple_points_t ker;
        theta_couple_point_t P12[MAX_POINTS];
        if (mode < -1 || mode > 2 || n < 2 || n > TORSION_EVEN_POWER - HD_EXTRA_TORSION || !read_curve(&E12.E1) ||
            !read_curve(&E12.E2) || !read_point(&ker.T1.P1) || !read_point(&ker.T1.P2) || !read_point(&ker.T2.P1) ||
            !read_point(&ker.T2.P2) || scanf("%d", &numP) != 1 || numP < 0 || numP > MAX_POINTS) {
            fprintf(stderr, "Invalid or incomplete chain operands\n");
            return 1;
        }
        for (int index = 0; index < numP; index++) {
            if (!read_point(&P12[index].P1) || !read_point(&P12[index].P2)) {
                fprintf(stderr, "Invalid or incomplete chain operands\n");
                return 1;
            }
        }
        int ok;
        if (mode == 0)
            ok = theta_chain_compute_and_eval(n, &E12, &ker, &E34, P12, numP);
        else if (mode == 1)
            ok = theta_chain_compute_and_eval_E1(n, &E12, &ker, &E34, P12, numP);
        else if (mode == 2)
            ok = theta_chain_compute_and_eval_E2(n, &E12, &ker, &E34, P12, numP);
        else
            ok = reference_verify_chain(n, &E12, &ker, &E34, P12, numP);
        print_chain(ok, &E34, P12, numP, mode);
    }
    return 0;
}

int
main(int argc, char **argv)
{
    if (argc == 2 && strcmp(argv[1], "--verify") == 0)
        return replay_verification();
    if (argc == 2 && strcmp(argv[1], "--chain") == 0)
        return run_chains();
    fprintf(stderr, "usage: hd-reference --verify|--chain\n");
    return 2;
}
