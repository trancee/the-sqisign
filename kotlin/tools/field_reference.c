// SPDX-License-Identifier: Apache-2.0

#include <fp2.h>

#if defined(SQISIGN_FIELD_P324_3)
#define FIELD_BYTES 41
#include "../../src/gf/ref/p324_3/fp_p324_3.c"
#elif defined(SQISIGN_FIELD_P500_27)
#define FIELD_BYTES 64
#include "../../src/gf/ref/p500_27/fp_p500_27.c"
#elif defined(SQISIGN_FIELD_P664_17)
#define FIELD_BYTES 84
#include "../../src/gf/ref/p664_17/fp_p664_17.c"
#else
#error "Select a supported reference field parameter set"
#endif

#include "../../src/gf/ref/lvlx/fp.c"
#include "../../src/gf/ref/lvlx/fp2.c"

#include <ctype.h>

#define STRINGIFY_INNER(value) #value
#define STRINGIFY(value) STRINGIFY_INNER(value)
#define FIELD_HEX_CHARS (2 * FIELD_BYTES)
#define FP2_HEX_CHARS (4 * FIELD_BYTES)

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
encode_hex(const unsigned char *bytes, size_t length)
{
    for (size_t index = 0; index < length; index++)
        printf("%02x", bytes[index]);
}

static int
compare_encoded(const unsigned char *left, const unsigned char *right, size_t length)
{
    for (size_t index = length; index-- > 0;) {
        if (left[index] != right[index])
            return left[index] < right[index] ? -1 : 1;
    }
    return 0;
}

static void
encode_fp(const fp_t *value)
{
    unsigned char bytes[FIELD_BYTES];
    fp_encode(bytes, value);
    encode_hex(bytes, FIELD_BYTES);
}

static void
encode_fp2(const fp2_t *value)
{
    unsigned char bytes[2 * FIELD_BYTES];
    fp2_encode(bytes, value);
    encode_hex(bytes, FIELD_BYTES);
    putchar(':');
    encode_hex(bytes + FIELD_BYTES, FIELD_BYTES);
}

int
main(int argc, char **argv)
{
    int field_mode = argc == 2 && strcmp(argv[1], "--field") == 0;
    int field_nonlinear_mode = argc == 2 && strcmp(argv[1], "--field-nonlinear") == 0;
    int field_control_mode = argc == 2 && strcmp(argv[1], "--field-control") == 0;
    int fp2_mode = argc == 2 && strcmp(argv[1], "--fp2") == 0;
    int fp2_nonlinear_mode = argc == 2 && strcmp(argv[1], "--fp2-nonlinear") == 0;
    int fp2_control_mode = argc == 2 && strcmp(argv[1], "--fp2-control") == 0;
    if (!field_mode && !field_nonlinear_mode && !field_control_mode && !fp2_mode &&
        !fp2_nonlinear_mode && !fp2_control_mode) {
        fprintf(stderr, "Expected a supported field reference mode\n");
        return 1;
    }

    int single_operand_mode = field_nonlinear_mode || fp2_nonlinear_mode;
    size_t input_hex_width =
        fp2_mode || fp2_nonlinear_mode || fp2_control_mode ? FP2_HEX_CHARS : FIELD_HEX_CHARS;
    char input_format[32];
    snprintf(input_format, sizeof(input_format), "%%%zus", input_hex_width);
    char left_hex[FP2_HEX_CHARS + 1], right_hex[FP2_HEX_CHARS + 1];
    int fields;
    while ((fields = single_operand_mode ? scanf(input_format, left_hex) : scanf(input_format, left_hex)) == 1) {
        if (!single_operand_mode && scanf(input_format, right_hex) != 1) {
            fprintf(stderr, "Incomplete reference field operands\n");
            return 1;
        }
        uint32_t control = 0;
        if ((field_control_mode || fp2_control_mode) && scanf("%u", &control) != 1) {
            fprintf(stderr, "Incomplete reference field control\n");
            return 1;
        }
        if ((field_control_mode || fp2_control_mode) && control > 1) {
            fprintf(stderr, "Reference field control must be zero or one\n");
            return 1;
        }
        if (field_mode) {
            unsigned char left_bytes[FIELD_BYTES], right_bytes[FIELD_BYTES];
            fp_t left, right, result;
            if (!decode_hex(left_hex, left_bytes, FIELD_BYTES) || !decode_hex(right_hex, right_bytes, FIELD_BYTES) ||
                fp_decode(&left, left_bytes) != UINT32_MAX || fp_decode(&right, right_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical prime-field operands\n");
                return 1;
            }
            encode_fp(&left);
            putchar(':');
            encode_fp(&right);
            fp_add(&result, &left, &right);
            putchar(':');
            encode_fp(&result);
            fp_sub(&result, &left, &right);
            putchar(':');
            encode_fp(&result);
            fp_mul(&result, &left, &right);
            putchar(':');
            encode_fp(&result);
            fp_sqr(&result, &left);
            putchar(':');
            encode_fp(&result);
            putchar('\n');
            continue;
        }
        if (field_control_mode) {
            unsigned char left_bytes[FIELD_BYTES], right_bytes[FIELD_BYTES];
            unsigned char left_encoding[FIELD_BYTES], negative_encoding[FIELD_BYTES];
            fp_t left, right, negative, selected, swapped_left, swapped_right;
            if (!decode_hex(left_hex, left_bytes, FIELD_BYTES) ||
                !decode_hex(right_hex, right_bytes, FIELD_BYTES) ||
                fp_decode(&left, left_bytes) != UINT32_MAX ||
                fp_decode(&right, right_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical prime-field operands\n");
                return 1;
            }
            fp_neg(&negative, &left);
            fp_encode(left_encoding, &left);
            fp_encode(negative_encoding, &negative);
            fp_select(&selected, &left, &right, control ? UINT32_MAX : 0);
            fp_copy(&swapped_left, &left);
            fp_copy(&swapped_right, &right);
            fp_cswap(&swapped_left, &swapped_right, control);
            printf("%u:", compare_encoded(left_encoding, negative_encoding, FIELD_BYTES) > 0);
            encode_fp(&selected);
            putchar(':');
            encode_fp(&swapped_left);
            putchar(':');
            encode_fp(&swapped_right);
            putchar('\n');
            continue;
        }
        if (field_nonlinear_mode) {
            unsigned char value_bytes[FIELD_BYTES], root_bytes[FIELD_BYTES];
            fp_t value, inverse, root, square;
            if (!decode_hex(left_hex, value_bytes, FIELD_BYTES) || fp_decode(&value, value_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical prime-field operand\n");
                return 1;
            }
            encode_fp(&value);
            putchar(':');
            if (fp_is_zero(&value)) {
                putchar('-');
            } else {
                fp_copy(&inverse, &value);
                fp_inv(&inverse);
                encode_fp(&inverse);
            }
            uint32_t is_square = fp_is_square(&value) != 0;
            printf(":%u:", is_square);
            if (!is_square) {
                putchar('-');
            } else {
                fp_copy(&root, &value);
                fp_sqrt(&root);
                fp_sqr(&square, &root);
                if (fp_is_equal(&square, &value) != UINT32_MAX) {
                    fprintf(stderr, "Reference prime-field square root failed verification\n");
                    return 1;
                }
                fp_neg(&square, &root);
                unsigned char negative_bytes[FIELD_BYTES];
                fp_encode(root_bytes, &root);
                fp_encode(negative_bytes, &square);
                if (compare_encoded(root_bytes, negative_bytes, FIELD_BYTES) > 0)
                    memcpy(root_bytes, negative_bytes, FIELD_BYTES);
                encode_hex(root_bytes, FIELD_BYTES);
            }
            putchar('\n');
            continue;
        }
        if (fp2_mode || fp2_nonlinear_mode || fp2_control_mode) {
            unsigned char left_bytes[2 * FIELD_BYTES], right_bytes[2 * FIELD_BYTES];
            fp2_t left, right, result;
            if (!decode_hex(left_hex, left_bytes, 2 * FIELD_BYTES) || fp2_decode(&left, left_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical extension-field operand\n");
                return 1;
            }
            if ((fp2_mode || fp2_control_mode) &&
                (!decode_hex(right_hex, right_bytes, 2 * FIELD_BYTES) ||
                             fp2_decode(&right, right_bytes) != UINT32_MAX)) {
                fprintf(stderr, "Invalid canonical extension-field operand\n");
                return 1;
            }
            if (fp2_control_mode) {
                unsigned char left_encoding[2 * FIELD_BYTES];
                unsigned char negative_encoding[2 * FIELD_BYTES];
                fp2_t negative, selected, swapped_left, swapped_right;
                fp2_neg(&negative, &left);
                fp2_encode(left_encoding, &left);
                fp2_encode(negative_encoding, &negative);
                fp2_select(&selected, &left, &right, control ? UINT32_MAX : 0);
                fp2_copy(&swapped_left, &left);
                fp2_copy(&swapped_right, &right);
                fp2_cswap(&swapped_left, &swapped_right, control);
                printf("%u:", compare_encoded(left_encoding, negative_encoding,
                                              2 * FIELD_BYTES) > 0);
                encode_fp2(&selected);
                putchar(':');
                encode_fp2(&swapped_left);
                putchar(':');
                encode_fp2(&swapped_right);
                putchar('\n');
                continue;
            }
            if (fp2_nonlinear_mode) {
                fp2_t inverse, root, negative;
                encode_fp2(&left);
                putchar(':');
                if (fp2_is_zero(&left)) {
                    printf("-:-");
                } else {
                    fp2_copy(&inverse, &left);
                    fp2_inv(&inverse);
                    encode_fp2(&inverse);
                }
                uint32_t is_square = fp2_is_square(&left) != 0;
                printf(":%u:", is_square);
                if (!is_square) {
                    printf("-:-");
                } else {
                    fp2_copy(&root, &left);
                    if (fp2_sqrt_verify(&root) != UINT32_MAX) {
                        fprintf(stderr, "Reference extension-field square root failed verification\n");
                        return 1;
                    }
                    fp2_neg(&negative, &root);
                    if (fp2_less_than(&root, &negative) == 0)
                        fp2_copy(&root, &negative);
                    encode_fp2(&root);
                }
                putchar('\n');
                continue;
            }
            encode_fp2(&left);
            putchar(':');
            encode_fp2(&right);
            fp2_add(&result, &left, &right);
            putchar(':');
            encode_fp2(&result);
            fp2_sub(&result, &left, &right);
            putchar(':');
            encode_fp2(&result);
            fp2_mul(&result, &left, &right);
            putchar(':');
            encode_fp2(&result);
            fp2_sqr(&result, &left);
            putchar(':');
            encode_fp2(&result);
            putchar('\n');
        }
    }
    if (fields != EOF || ferror(stdin)) {
        fprintf(stderr, "Incomplete reference field input\n");
        return 1;
    }
    return 0;
}
