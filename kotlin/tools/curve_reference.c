// SPDX-License-Identifier: Apache-2.0

#include <ec.h>

#include "../../src/gf/ref/p324_3/fp_p324_3.c"
#include "../../src/gf/ref/lvlx/fp.c"
#include "../../src/gf/ref/lvlx/fp2.c"
#include "../../src/ec/ref/lvlx/ec.c"

#include <ctype.h>

#define FIELD_BYTES 41
#define FP2_BYTES (2 * FIELD_BYTES)
#define FP2_HEX_CHARS (2 * FP2_BYTES)

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

int
main(void)
{
    char hex[FP2_HEX_CHARS + 1];
    int fields;
    while ((fields = scanf("%164s", hex)) == 1) {
        fp2_t values[8];
        for (size_t index = 0; index < 8; index++) {
            unsigned char encoded[FP2_BYTES];
            if ((index != 0 && scanf("%164s", hex) != 1) || !decode_hex(hex, encoded, FP2_BYTES) ||
                fp2_decode(&values[index], encoded) != UINT32_MAX) {
                fprintf(stderr, "Invalid or incomplete canonical curve operands\n");
                return 1;
            }
        }
        unsigned char scalar_bytes[FIELD_BYTES];
        if (scanf("%164s", hex) != 1 || !decode_hex(hex, scalar_bytes, FIELD_BYTES)) {
            fprintf(stderr, "Invalid or incomplete unsigned 328-bit scalar\n");
            return 1;
        }
        digit_t scalar[6] = { 0 };
        for (size_t index = 0; index < FIELD_BYTES; index++)
            scalar[index / 8] |= (digit_t)scalar_bytes[index] << (8 * (index % 8));

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
        if ((fp2_is_zero(&left.x) && fp2_is_zero(&left.z)) || (fp2_is_zero(&right.x) && fp2_is_zero(&right.z)) ||
            fp2_is_zero(&difference.x) || fp2_is_zero(&difference.z)) {
            fprintf(stderr, "Invalid projective or exceptional differential operands\n");
            return 1;
        }
        ec_point_t doubled, sum, product;
        ec_point_t coefficients = { curve.A, curve.C };
        ec_xDBL(&doubled, &left, &coefficients);
        ec_xADD(&sum, &left, &right, &difference);
        ec_curve_normalize_A24(&curve);
        ec_mul(&product, scalar, 328, &left, &curve);
        encode_affine(&doubled);
        putchar(':');
        encode_affine(&sum);
        putchar(':');
        encode_affine(&product);
        putchar(':');
        encode_field(&normalized);
        putchar('\n');
    }
    if (fields != EOF) {
        fprintf(stderr, "Invalid curve reference input\n");
        return 1;
    }
    return 0;
}
