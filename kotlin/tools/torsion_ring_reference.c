// SPDX-License-Identifier: Apache-2.0

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// The pinned integer/matrix implementations remain an external test oracle.
#include "../../src/mp/ref/lvlx/mp.c"
#include "../../src/quaternion/ref/lvlx/dim2.c"

enum
{
    MAX_EXPONENT = 664,
    LINE_BYTES = 2048
};

static int
decode(ibz_t *value, const char *hex, unsigned exponent)
{
    size_t bytes = (exponent + 7) / 8;
    if (hex == NULL || strlen(hex) != 2 * bytes || strspn(hex, "0123456789abcdef") != 2 * bytes)
        return 0;
    memset(value, 0, sizeof(*value));
    value->bitlen = exponent + 1;
    for (size_t index = 0; index < bytes; index++) {
        unsigned byte;
        if (sscanf(hex + 2 * index, "%2x", &byte) != 1)
            return 0;
        if (index == bytes - 1 && (byte >> ((exponent - 1) % 8 + 1)) != 0)
            return 0;
        value->limbs[index / 8] |= (digit_t)byte << (8 * (index % 8));
    }
    return 1;
}

static void
encode_residue(const ibz_t *value, const ibz_t *modulus, unsigned exponent)
{
    ibz_t residue;
    ibz_mod(&residue, value, modulus);
    for (unsigned index = 0; index < (exponent + 7) / 8; index++)
        printf("%02x", (unsigned)((residue.limbs[index / 8] >> (8 * (index % 8))) & 0xff));
}

static void
scalar_record(unsigned exponent, const ibz_t *left, const ibz_t *right, const ibz_t *modulus)
{
    ibz_t result;
    printf("%u:", exponent);
    encode_residue(left, modulus, exponent);
    putchar(':');
    encode_residue(right, modulus, exponent);
    ibz_add(&result, left, right);
    putchar(':');
    encode_residue(&result, modulus, exponent);
    ibz_sub(&result, left, right);
    putchar(':');
    encode_residue(&result, modulus, exponent);
    ibz_mul(&result, left, right);
    putchar(':');
    encode_residue(&result, modulus, exponent);
    ibz_neg(&result, left);
    putchar(':');
    encode_residue(&result, modulus, exponent);
    int comparison = ibz_cmp(left, right);
    printf(":%d:", (comparison > 0) - (comparison < 0));
    if (ibz_invmod(&result, left, modulus))
        encode_residue(&result, modulus, exponent);
    else
        putchar('-');
    putchar('\n');
}

static void
encode_matrix(const ibz_mat_2x2_t *matrix, const ibz_t *modulus, unsigned exponent)
{
    for (int row = 0; row < 2; row++)
        for (int column = 0; column < 2; column++) {
            putchar(':');
            encode_residue(&matrix->m[row][column], modulus, exponent);
        }
}

static void
matrix_record(unsigned exponent, const ibz_mat_2x2_t *matrix, const ibz_t *modulus)
{
    ibz_t determinant;
    ibz_mat_2x2_t normalized, inverse;
    printf("%u", exponent);
    encode_matrix(matrix, modulus, exponent);
    ibz_mat_2x2_det_from_ibz(&determinant, &matrix->m[0][0], &matrix->m[0][1], &matrix->m[1][0], &matrix->m[1][1]);
    putchar(':');
    encode_residue(&determinant, modulus, exponent);
    ibz_mat_2x2_copy(&normalized, matrix);
    ibz_mat_2x2_normalize(&normalized, exponent);
    // C negation leaves a zero entry equal to the modulus; compare ring residues explicitly.
    encode_matrix(&normalized, modulus, exponent);
    if (ibz_mat_2x2_inv_mod(&inverse, matrix, modulus))
        encode_matrix(&inverse, modulus, exponent);
    else
        printf(":-");
    putchar('\n');
}

int
main(void)
{
    char line[LINE_BYTES];
    while (fgets(line, sizeof(line), stdin) != NULL) {
        char *mode = strtok(line, " \t\r\n");
        char *exponent_text = strtok(NULL, " \t\r\n");
        if (mode == NULL || strlen(mode) != 1 || (mode[0] != 'S' && mode[0] != 'M') || exponent_text == NULL ||
            strlen(exponent_text) > 3 || strspn(exponent_text, "0123456789") != strlen(exponent_text))
            goto invalid;
        unsigned exponent = (unsigned)strtoul(exponent_text, NULL, 10);
        if (exponent < 1 || exponent > MAX_EXPONENT)
            goto invalid;
        ibz_t modulus;
        ibz_mul_2exp(&modulus, &ibz_const_one, exponent);
        ibz_mat_2x2_t operands;
        unsigned count = mode[0] == 'S' ? 2 : 4;
        for (unsigned index = 0; index < count; index++)
            if (!decode(&operands.m[index / 2][index % 2], strtok(NULL, " \t\r\n"), exponent))
                goto invalid;
        if (strtok(NULL, " \t\r\n") != NULL)
            goto invalid;
        if (mode[0] == 'S')
            scalar_record(exponent, &operands.m[0][0], &operands.m[0][1], &modulus);
        else
            matrix_record(exponent, &operands, &modulus);
    }
    if (ferror(stdin))
        goto invalid;
    return 0;
invalid:
    fprintf(stderr, "Invalid or incomplete canonical torsion ring operands\n");
    return 2;
}
