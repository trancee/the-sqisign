// SPDX-License-Identifier: Apache-2.0

#include <ec.h>

#include "../../src/mp/ref/lvlx/mp.c"
#undef dpint
#if REFERENCE_LEVEL == 1
#include "../../src/gf/ref/p324_3/fp_p324_3.c"
#include "../../src/precomp/ref/p324_3/e0_basis.c"
#include "../../src/precomp/ref/p324_3/ec_params.c"
#define FIELD_BYTES 41
#define FIELD_SCAN "%165s"
#elif REFERENCE_LEVEL == 3
#include "../../src/gf/ref/p500_27/fp_p500_27.c"
#include "../../src/precomp/ref/p500_27/e0_basis.c"
#include "../../src/precomp/ref/p500_27/ec_params.c"
#define FIELD_BYTES 64
#define FIELD_SCAN "%257s"
#elif REFERENCE_LEVEL == 5
#include "../../src/gf/ref/p664_17/fp_p664_17.c"
#include "../../src/precomp/ref/p664_17/e0_basis.c"
#include "../../src/precomp/ref/p664_17/ec_params.c"
#define FIELD_BYTES 84
#define FIELD_SCAN "%337s"
#else
#error "Select a supported reference level"
#endif
#include "../../src/gf/ref/lvlx/fp.c"
#include "../../src/gf/ref/lvlx/fp2.c"
#include "../../src/ec/ref/lvlx/ec.c"
#include "../../src/ec/ref/lvlx/basis.c"
#include "../../src/ec/ref/lvlx/biextension.c"

#include <ctype.h>
#include <errno.h>

#define FP2_BYTES (2 * FIELD_BYTES)
#define FP2_HEX_CHARS (2 * FP2_BYTES)
#define SCALAR_BITS (8 * FIELD_BYTES)

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
encode_affine(const ec_point_t *point)
{
    if (ec_is_zero(point)) {
        putchar('-');
    } else {
        fp2_t inverse, coordinate;
        fp2_copy(&inverse, &point->z);
        fp2_inv(&inverse);
        fp2_mul(&coordinate, &point->x, &inverse);
        encode_field(&coordinate);
    }
}

static int
decode_scalar(digit_t scalar[NWORDS_ORDER])
{
    char hex[FP2_HEX_CHARS + 2];
    unsigned char bytes[FIELD_BYTES];
    if (scanf(FIELD_SCAN, hex) != 1 || !decode_hex(hex, bytes, FIELD_BYTES))
        return 0;
    memset(scalar, 0, NWORDS_ORDER * sizeof(*scalar));
    for (size_t index = 0; index < FIELD_BYTES; index++)
        scalar[index / 8] |= (digit_t)bytes[index] << (8 * (index % 8));
    return 1;
}

static int
scalar_is_zero(const digit_t scalar[NWORDS_ORDER])
{
    digit_t combined = 0;
    for (size_t index = 0; index < NWORDS_ORDER; index++)
        combined |= scalar[index];
    return combined == 0;
}

static int
check_roots(int base_residue)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        unsigned char encoded[FP2_BYTES];
        fp2_t value;
        if (!decode_hex(hex, encoded, FP2_BYTES) || fp2_decode(&value, encoded) != UINT32_MAX) {
            fprintf(stderr, "Invalid canonical root operand\n");
            return 1;
        }
        if (base_residue) {
            if (!fp_is_zero(&value.im)) {
                fprintf(stderr, "Base residue operand must have zero imaginary component\n");
                return 1;
            }
            putchar(fp_is_square(&value.re) ? '1' : '0');
        } else if (fp2_sqrt_verify(&value))
            encode_field(&value);
        else
            putchar('-');
        putchar('\n');
    }
    return 0;
}

static int
check_e0_bases(void)
{
    char token[32];
    while (scanf("%31s", token) == 1) {
        char *end;
        errno = 0;
        long exponent = strtol(token, &end, 10);
        if (errno != 0 || end == token || *end != '\0' || exponent < 1 || exponent > TORSION_EVEN_POWER) {
            fprintf(stderr, "Invalid canonical E0 torsion exponent\n");
            return 1;
        }
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_set_zero(&curve.A);
        fp2_set_one(&curve.C);
        ec_basis_t basis;
        ec_basis_E0_2f(&basis, &curve, (int)exponent);
        encode_affine(&basis.P);
        putchar(':');
        encode_affine(&basis.Q);
        putchar(':');
        encode_affine(&basis.PmQ);
        putchar('\n');
    }
    return 0;
}

static int
read_bounded_integer(int lower, int upper, int *result)
{
    char token[32], *end;
    if (scanf("%31s", token) != 1)
        return 0;
    errno = 0;
    long value = strtol(token, &end, 10);
    if (errno != 0 || end == token || *end != '\0' || value < lower || value > upper)
        return 0;
    *result = (int)value;
    return 1;
}

static int
check_canonical_bases(int from_hint)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        unsigned char encoded[FP2_BYTES];
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_set_one(&curve.C);
        int exponent, auxiliary;
        if (!decode_hex(hex, encoded, FP2_BYTES) || fp2_decode(&curve.A, encoded) != UINT32_MAX ||
            !ec_curve_verify_A(&curve.A) || !read_bounded_integer(1, TORSION_EVEN_POWER, &exponent) ||
            !read_bounded_integer(0, from_hint ? UINT8_MAX : exponent - 1, &auxiliary)) {
            fprintf(stderr, "Invalid canonical basis operands or bounds\n");
            return 1;
        }
        ec_basis_t basis;
        uint8_t hint;
        if (from_hint) {
            hint = (uint8_t)auxiliary;
            if (!ec_curve_to_basis_2f_from_hint(&basis, &curve, exponent, hint)) {
                fprintf(stderr, "Invalid canonical basis hint\n");
                return 1;
            }
        } else {
            hint = ec_curve_to_basis_2f_to_hint(&basis, &curve, exponent, auxiliary);
        }
        printf("%u:", (unsigned)hint);
        encode_affine(&basis.P);
        putchar(':');
        encode_affine(&basis.Q);
        putchar(':');
        encode_affine(&basis.PmQ);
        putchar('\n');
    }
    return 0;
}

static int
check_shared_differences(void)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        fp2_t values[10];
        for (size_t index = 0; index < 10; index++) {
            unsigned char encoded[FP2_BYTES];
            if ((index != 0 && scanf(FIELD_SCAN, hex) != 1) || !decode_hex(hex, encoded, FP2_BYTES) ||
                fp2_decode(&values[index], encoded) != UINT32_MAX) {
                fprintf(stderr, "Invalid or incomplete shared-difference operands\n");
                return 1;
            }
        }
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_copy(&curve.A, &values[0]);
        fp2_copy(&curve.C, &values[1]);
        fp2_t inverse, normalized;
        fp2_copy(&inverse, &curve.C);
        if (fp2_is_zero(&inverse)) {
            fprintf(stderr, "Invalid shared-difference curve\n");
            return 1;
        }
        fp2_inv(&inverse);
        fp2_mul(&normalized, &curve.A, &inverse);
        if (!ec_curve_verify_A(&normalized)) {
            fprintf(stderr, "Invalid shared-difference curve\n");
            return 1;
        }
        ec_point_t points[4];
        for (size_t index = 0; index < 4; index++) {
            fp2_copy(&points[index].x, &values[2 + 2 * index]);
            fp2_copy(&points[index].z, &values[3 + 2 * index]);
            if (fp2_is_zero(&points[index].x) && fp2_is_zero(&points[index].z)) {
                fprintf(stderr, "Invalid shared-difference point\n");
                return 1;
            }
        }
        ec_point_t result;
        shared_difference_point(&result, &points[0], &points[1], &points[2], &points[3], &curve);
        if (fp2_is_zero(&result.x) && fp2_is_zero(&result.z))
            putchar('!');
        else
            encode_affine(&result);
        putchar('\n');
    }
    return 0;
}

#include "pairing_reference.h"
#include "basis_transform_reference.h"

int
main(int argc, char **argv)
{
    if (argc == 2 && strcmp(argv[1], "--basis-combination") == 0)
        return check_basis_combinations();
    if (argc == 2 && strcmp(argv[1], "--basis-change") == 0)
        return check_basis_changes();
    if (argc == 2 && strcmp(argv[1], "--dlog") == 0)
        return check_discrete_logs();
    if (argc == 2 && strcmp(argv[1], "--tate") == 0)
        return check_tate_pairings();
    if (argc == 2 && strcmp(argv[1], "--roots") == 0)
        return check_roots(0);
    if (argc == 2 && strcmp(argv[1], "--base-residue") == 0)
        return check_roots(1);
    if (argc == 2 && strcmp(argv[1], "--shared-difference") == 0)
        return check_shared_differences();
    if (argc == 2 && strcmp(argv[1], "--e0-basis") == 0)
        return check_e0_bases();
    if (argc == 2 && strcmp(argv[1], "--canonical-basis") == 0)
        return check_canonical_bases(0);
    if (argc == 2 && strcmp(argv[1], "--basis-from-hint") == 0)
        return check_canonical_bases(1);
    int two_torsion = argc == 2 && strcmp(argv[1], "--two-torsion") == 0;
    if (argc != 1 && !two_torsion) {
        fprintf(stderr, "Invalid curve reference mode\n");
        return 1;
    }
    char hex[FP2_HEX_CHARS + 2];
    int fields;
    while ((fields = scanf(FIELD_SCAN, hex)) == 1) {
        fp2_t values[8];
        for (size_t index = 0; index < 8; index++) {
            unsigned char encoded[FP2_BYTES];
            if ((index != 0 && scanf(FIELD_SCAN, hex) != 1) || !decode_hex(hex, encoded, FP2_BYTES) ||
                fp2_decode(&values[index], encoded) != UINT32_MAX) {
                fprintf(stderr, "Invalid or incomplete canonical curve operands\n");
                return 1;
            }
        }
        digit_t scalar[NWORDS_ORDER], other_scalar[NWORDS_ORDER];
        if (!decode_scalar(scalar) || !decode_scalar(other_scalar)) {
            fprintf(stderr, "Invalid or incomplete unsigned %d-bit scalar\n", SCALAR_BITS);
            return 1;
        }

        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_copy(&curve.A, &values[0]);
        fp2_copy(&curve.C, &values[1]);
        if (fp2_is_zero(&curve.C)) {
            fprintf(stderr, "Invalid zero curve denominator\n");
            return 1;
        }
        fp2_t normalized, inverse;
        fp2_copy(&inverse, &curve.C);
        fp2_inv(&inverse);
        fp2_mul(&normalized, &curve.A, &inverse);
        if (!ec_curve_verify_A(&normalized)) {
            fprintf(stderr, "Invalid singular curve\n");
            return 1;
        }
        ec_point_t left = { values[2], values[3] };
        ec_point_t right = { values[4], values[5] };
        ec_point_t difference = { values[6], values[7] };
        if (fp2_is_zero(&left.z) || fp2_is_zero(&right.z) || fp2_is_zero(&difference.z) ||
            (!two_torsion && (fp2_is_zero(&left.x) || fp2_is_zero(&right.x) || fp2_is_zero(&difference.x)))) {
            fprintf(stderr, "Invalid projective or exceptional differential operands\n");
            return 1;
        }
        ec_basis_t basis = { left, right, difference };
        if (two_torsion) {
            ec_point_t result;
            if (!ec_biscalar_mul(&result, scalar, other_scalar, 1, &basis, &curve)) {
                fprintf(stderr, "Invalid two-torsion basis\n");
                return 1;
            }
            encode_affine(&result);
            putchar('\n');
            continue;
        }
        ec_point_t doubled, sum, product, three_point, biscalar, selected_difference, other_difference;
        ec_point_t coefficients = { curve.A, curve.C };
        ec_xDBL(&doubled, &left, &coefficients);
        ec_xADD(&sum, &left, &right, &difference);
        ec_curve_normalize_A24(&curve);
        ec_mul(&product, scalar, SCALAR_BITS, &left, &curve);
        if (!ec_ladder3pt(&three_point, scalar, &left, &right, &difference, &curve)) {
            fprintf(stderr, "Invalid three-point ladder operands\n");
            return 1;
        }
        // The reference biladder's wrapped-zero recoding assumes a torsion basis.
        if (scalar_is_zero(scalar)) {
            ec_mul(&biscalar, other_scalar, SCALAR_BITS, &right, &curve);
        } else if (scalar_is_zero(other_scalar)) {
            ec_mul(&biscalar, scalar, SCALAR_BITS, &left, &curve);
        } else if (!ec_biscalar_mul(&biscalar, scalar, other_scalar, SCALAR_BITS, &basis, &curve)) {
            fprintf(stderr, "Invalid biscalar ladder operands\n");
            return 1;
        }
        difference_point(&other_difference, &selected_difference, &left, &right, &curve);
        encode_affine(&doubled);
        putchar(':');
        encode_affine(&sum);
        putchar(':');
        encode_affine(&product);
        putchar(':');
        encode_field(&normalized);
        putchar(':');
        encode_affine(&three_point);
        putchar(':');
        encode_affine(&biscalar);
        putchar(':');
        encode_affine(&selected_difference);
        putchar('\n');
    }
    if (fields != EOF) {
        fprintf(stderr, "Invalid curve reference input\n");
        return 1;
    }
    return 0;
}
