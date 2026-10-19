// SPDX-License-Identifier: Apache-2.0

#ifndef SQISIGN_KOTLIN_ISOGENY_REFERENCE_H
#define SQISIGN_KOTLIN_ISOGENY_REFERENCE_H

static int
read_isogeny_point(ec_point_t *point)
{
    char hex[FP2_HEX_CHARS + 2];
    unsigned char encoded[FP2_BYTES];
    if (scanf(FIELD_SCAN, hex) != 1)
        return 0;
    if (strcmp(hex, "-") == 0) {
        fp2_set_one(&point->x);
        fp2_set_zero(&point->z);
        return 1;
    }
    if (!decode_hex(hex, encoded, FP2_BYTES) || fp2_decode(&point->x, encoded) != UINT32_MAX)
        return 0;
    fp2_set_one(&point->z);
    return 1;
}

static int
read_isogeny_curve(const char *hex, ec_curve_t *curve)
{
    unsigned char encoded[FP2_BYTES];
    ec_curve_init(curve);
    if (!decode_hex(hex, encoded, FP2_BYTES) || fp2_decode(&curve->A, encoded) != UINT32_MAX ||
        !ec_curve_verify_A(&curve->A))
        return 0;
    fp2_set_one(&curve->C);
    ec_curve_normalize_A24(curve);
    return 1;
}

static int
is_nonsingular_four_torsion(const ec_point_t *kernel, ec_curve_t *curve)
{
    ec_point_t top;
    if (!ec_is_four_torsion(kernel, curve))
        return 0;
    ec_dbl(&top, kernel, curve);
    return !fp2_is_zero(&top.x);
}

static void
encode_codomain(ec_point_t a24)
{
    fp2_t two;
    fp2_inv(&a24.z);
    fp2_mul(&a24.x, &a24.x, &a24.z);
    fp2_add(&a24.x, &a24.x, &a24.x);
    fp2_add(&a24.x, &a24.x, &a24.x);
    fp2_set_one(&two);
    fp2_add(&two, &two, &two);
    fp2_sub(&a24.x, &a24.x, &two);
    encode_field(&a24.x);
}

// Modes: 0 normalizes, 1 evaluates one four-isogeny, 2 computes a normalized two-isogeny chain.
static int
check_isogenies(int mode)
{
    char hex[FP2_HEX_CHARS + 2];
    while (scanf(FIELD_SCAN, hex) == 1) {
        ec_curve_t curve, image;
        ec_point_t kernel;
        if (!read_isogeny_curve(hex, &curve) || !read_isogeny_point(&kernel))
            goto invalid;
        if (mode == 2) {
            int length;
            if (!read_bounded_integer(2, TORSION_EVEN_POWER, &length) || length % 2)
                goto invalid;
            ec_isog_even_t phi = { 0 };
            phi.curve = curve;
            phi.kernel = kernel;
            phi.length = length + 2;
            if (iso_isogeny_2chain(&image, &phi) != 0)
                goto invalid;
            encode_field(&image.A);
        } else if (!is_nonsingular_four_torsion(&kernel, &curve)) {
            goto invalid;
        } else if (mode == 0) {
            if (!ec_normalize_montgomery(&image, &kernel, NULL))
                goto invalid;
            encode_field(&image.A);
        } else {
            ec_point_t point, evaluated, a24;
            ec_kps4_t constants;
            if (!read_isogeny_point(&point))
                goto invalid;
            iso_xisog_4(&constants, &a24, kernel);
            iso_xeval_4(&evaluated, &point, 1, &constants);
            encode_codomain(a24);
            putchar(':');
            encode_affine(&evaluated);
        }
        putchar('\n');
    }
    return 0;
invalid:
    fprintf(stderr, "Invalid isogeny operands, kernel or length\n");
    return 1;
}

#endif
