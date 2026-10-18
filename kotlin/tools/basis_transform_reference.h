// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KOTLIN_BASIS_TRANSFORM_REFERENCE_H
#define SQISIGN_KOTLIN_BASIS_TRANSFORM_REFERENCE_H

static int
read_basis_fields(const char *first, fp2_t *values, int count)
{
    char hex[FP2_HEX_CHARS + 2];
    for (int index = 0; index < count; index++) {
        unsigned char encoded[FP2_BYTES];
        if ((index != 0 && scanf(FIELD_SCAN, hex) != 1) || !decode_hex(index == 0 ? first : hex, encoded, FP2_BYTES) ||
            fp2_decode(&values[index], encoded) != UINT32_MAX)
            return 0;
    }
    return 1;
}

static int
read_torsion_coefficient(digit_t *value, int exponent)
{
    char hex[FP2_HEX_CHARS + 2];
    unsigned char encoded[FIELD_BYTES];
    int length = (exponent + 7) / 8;
    if (scanf(FIELD_SCAN, hex) != 1 || !decode_hex(hex, encoded, (size_t)length) ||
        encoded[length - 1] >> ((exponent - 1) % 8 + 1))
        return 0;
    memset(value, 0, NWORDS_ORDER * sizeof(*value));
    for (int index = 0; index < length; index++)
        value[index / 8] |= (digit_t)encoded[index] << (8 * (index % 8));
    return 1;
}

static int
checked_reference_basis(ec_basis_t *basis, ec_curve_t *curve, const fp2_t *values, int exponent)
{
    ec_point_t *points[] = { &basis->P, &basis->Q, &basis->PmQ };
    for (int index = 0; index < 3; index++) {
        if (!is_on_curve(&values[index], curve))
            return 0;
        fp2_copy(&points[index]->x, &values[index]);
        fp2_set_one(&points[index]->z);
    }
    if (ec_is_equal(&basis->P, &basis->Q) || !test_basis_order_twof(basis, curve, exponent))
        return 0;
    ec_point_t plus, minus;
    difference_point(&plus, &minus, &basis->P, &basis->Q, curve);
    return ec_is_equal(&basis->PmQ, &plus) || ec_is_equal(&basis->PmQ, &minus);
}

static int
check_basis_combinations(void)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        fp2_t values[4];
        int exponent;
        digit_t left[NWORDS_ORDER], right[NWORDS_ORDER];
        if (!read_basis_fields(hex, values, 4) || !read_bounded_integer(1, TORSION_EVEN_POWER, &exponent) ||
            !read_torsion_coefficient(left, exponent) || !read_torsion_coefficient(right, exponent) ||
            !ec_curve_verify_A(&values[0]))
            goto invalid;
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_copy(&curve.A, &values[0]);
        fp2_set_one(&curve.C);
        ec_curve_normalize_A24(&curve);
        ec_basis_t basis;
        if (!checked_reference_basis(&basis, &curve, &values[1], exponent))
            goto invalid;
        ec_point_t result;
        if (exponent > 1 && scalar_is_zero(left))
            ec_mul(&result, right, exponent, &basis.Q, &curve);
        else if (exponent > 1 && scalar_is_zero(right))
            ec_mul(&result, left, exponent, &basis.P, &curve);
        else if (!ec_biscalar_mul(&result, left, right, exponent, &basis, &curve))
            goto invalid;
        encode_affine(&result);
        putchar('\n');
    }
    return 0;
invalid:
    fprintf(stderr, "Invalid checked basis combination\n");
    return 1;
}

static int
check_basis_changes(void)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        fp2_t values[7];
        int exponent;
        if (!read_basis_fields(hex, values, 7) || !read_bounded_integer(2, TORSION_EVEN_POWER, &exponent) ||
            !ec_curve_verify_A(&values[0]))
            goto invalid;
        ec_curve_t curve;
        ec_curve_init(&curve);
        fp2_copy(&curve.A, &values[0]);
        fp2_set_one(&curve.C);
        ec_curve_normalize_A24(&curve);
        ec_basis_t reference, target;
        if (!checked_reference_basis(&reference, &curve, &values[1], TORSION_EVEN_POWER) ||
            !checked_reference_basis(&target, &curve, &values[4], exponent))
            goto invalid;
        pairing_dlog_params_t data = { 0 };
        data.e = exponent;
        data.PQ = reference;
        data.RS = target;
        data.A24 = curve.A24;
        // Check the partial reference domain before its unconditional inversions.
        compute_shared_difference_points(&data, &curve);
        ec_point_t *differences[] = { &data.diff.PmR, &data.diff.RmQ, &data.diff.PmS, &data.diff.SmQ };
        for (size_t index = 0; index < sizeof(differences) / sizeof(*differences); index++) {
            if (fp2_is_zero(&differences[index]->x) || fp2_is_zero(&differences[index]->z))
                goto invalid;
        }
        digit_t entries[4][NWORDS_ORDER];
        pairing_dlog_2_tate(entries[0], entries[2], entries[1], entries[3], &reference, &target, &curve, exponent);
        for (int entry = 0; entry < 4; entry++) {
            if (entry != 0)
                putchar(':');
            for (int index = 0; index < (exponent + 7) / 8; index++)
                printf("%02x", (unsigned)((entries[entry][index / 8] >> (8 * (index % 8))) & 0xff));
        }
        putchar('\n');
    }
    return 0;
invalid:
    fprintf(stderr, "Invalid or nongeneric basis-change operands\n");
    return 1;
}

#endif
