// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KOTLIN_PAIRING_REFERENCE_H
#define SQISIGN_KOTLIN_PAIRING_REFERENCE_H

static int
check_discrete_logs(void)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        fp2_t values[2], power, minus_one;
        int exponent;
        for (int index = 0; index < 2; index++) {
            unsigned char encoded[FP2_BYTES];
            if ((index != 0 && scanf(FIELD_SCAN, hex) != 1) || !decode_hex(hex, encoded, FP2_BYTES) ||
                fp2_decode(&values[index], encoded) != UINT32_MAX)
                goto invalid;
        }
        if (!read_bounded_integer(1, TORSION_EVEN_POWER, &exponent))
            goto invalid;
        fp2_set_one(&minus_one);
        fp2_neg(&minus_one, &minus_one);
        fp2_copy(&power, &values[0]);
        for (int bit = 1; bit < exponent; bit++)
            fp2_sqr(&power, &power);
        if (!fp2_is_equal(&power, &minus_one))
            goto invalid;
        fp2_copy(&power, &values[1]);
        for (int bit = 0; bit < exponent; bit++)
            fp2_sqr(&power, &power);
        if (!fp2_is_one(&power))
            goto invalid;
        fp2_inv(&values[0]);
        digit_t scalar[NWORDS_ORDER];
        if (!fp2_dlog_2e(scalar, &values[1], &values[0], exponent))
            goto invalid;
        for (int index = 0; index < (exponent + 7) / 8; index++)
            printf("%02x", (unsigned)((scalar[index / 8] >> (8 * (index % 8))) & 0xff));
        putchar('\n');
    }
    return 0;
invalid:
    fprintf(stderr, "Invalid discrete logarithm operands or order\n");
    return 1;
}

static int
check_tate_pairings(void)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        fp2_t values[4];
        int exponent;
        for (int index = 0; index < 4; index++) {
            unsigned char encoded[FP2_BYTES];
            if ((index != 0 && scanf(FIELD_SCAN, hex) != 1) || !decode_hex(hex, encoded, FP2_BYTES) ||
                fp2_decode(&values[index], encoded) != UINT32_MAX)
                goto invalid;
        }
        if (!read_bounded_integer(1, TORSION_EVEN_POWER, &exponent))
            goto invalid;
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_copy(&curve.A, &values[0]);
        fp2_set_one(&curve.C);
        if (!ec_curve_verify_A(&curve.A) || fp2_is_zero(&values[1]) || fp2_is_zero(&values[2]) ||
            fp2_is_equal(&values[1], &values[2]))
            goto invalid;
        ec_point_t points[3];
        for (int index = 0; index < 3; index++) {
            if (!is_on_curve(&values[index + 1], &curve))
                goto invalid;
            fp2_copy(&points[index].x, &values[index + 1]);
            fp2_set_one(&points[index].z);
        }
        ec_point_t multiple, coefficients = { curve.A, curve.C };
        ec_copy_point(&multiple, &points[0]);
        for (int bit = 0; bit < exponent; bit++)
            ec_xDBL(&multiple, &multiple, &coefficients);
        if (!ec_is_zero(&multiple))
            goto invalid;
        ec_point_t plus, minus;
        difference_point(&plus, &minus, &points[0], &points[1], &curve);
        if (!ec_is_equal(&points[2], &plus) && !ec_is_equal(&points[2], &minus))
            goto invalid;
        fp2_t value;
        pairing_reduced_tate(&value, exponent, &points[0], &points[1], &points[2], &curve);
        encode_field(&value);
        putchar('\n');
    }
    return 0;
invalid:
    fprintf(stderr, "Invalid generic Tate operands or order\n");
    return 1;
}

#endif
