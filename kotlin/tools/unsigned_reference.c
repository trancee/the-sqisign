// SPDX-License-Identifier: Apache-2.0

// Including the reference translation unit exposes its static raw unsigned helpers
// without adding a public C API. Unused functions are discarded at link time.
#include "../../src/mp/ref/lvlx/mp.c"
#undef dpint
#include "../../src/gf/ref/p324_3/fp_p324_3.c"
#include "../../src/gf/ref/lvlx/fp.c"
#include "../../src/gf/ref/lvlx/fp2.c"

enum
{
    ENCODED_BYTES = 41,
    HEX_CHARS = 2 * ENCODED_BYTES,
    WIDE_HEX_CHARS = 2 * HEX_CHARS,
    WORD_COUNT = 6,
    WIDE_SIGNED_WORD_COUNT = 11
};
_Static_assert(sizeof(digit_t) == 8, "The reference adapter requires 64-bit C limbs");

static int
decode(const char *hex, digit_t *words, int bytes, int count)
{
    size_t hex_chars = (size_t)2 * bytes;
    if (strlen(hex) != hex_chars || strspn(hex, "0123456789abcdef") != hex_chars)
        return 0;
    memset(words, 0, (size_t)count * sizeof(*words));
    for (int index = 0; index < bytes; index++) {
        unsigned byte;
        if (sscanf(hex + 2 * index, "%2x", &byte) != 1)
            return 0;
        words[index / 8] |= (digit_t)byte << (8 * (index % 8));
    }
    return 1;
}

static void
encode(const digit_t *words, int bytes)
{
    for (int index = 0; index < bytes; index++)
        printf("%02x", (unsigned)((words[index / 8] >> (8 * (index % 8))) & 0xff));
}

static void
words_to_little_endian(const digit_t *words, unsigned char *bytes, int length)
{
    for (int index = 0; index < length; index++)
        bytes[index] = (unsigned char)(words[index / 8] >> (8 * (index % 8)));
}

static void
encode_bytes(const unsigned char *bytes, int length)
{
    for (int index = 0; index < length; index++)
        printf("%02x", bytes[index]);
}

static void
encode_fp2_fields(const fp2_t *value)
{
    unsigned char bytes[2 * ENCODED_BYTES];
    fp2_encode(bytes, value);
    encode_bytes(bytes, ENCODED_BYTES);
    putchar(':');
    encode_bytes(bytes + ENCODED_BYTES, ENCODED_BYTES);
}

static void
import_signed(ibz_t *value, const digit_t *words)
{
    value->bitlen = 8 * ENCODED_BYTES + 1;
    memcpy(value->limbs, words, WORD_COUNT * sizeof(digit_t));
    if ((words[WORD_COUNT - 1] & 0x80) != 0)
        value->limbs[WORD_COUNT - 1] |= ~(digit_t)0xff;
}

static void
import_signed_wide(ibz_t *value, const digit_t *words)
{
    value->bitlen = 16 * ENCODED_BYTES + 1;
    memcpy(value->limbs, words, WIDE_SIGNED_WORD_COUNT * sizeof(digit_t));
    if ((words[WIDE_SIGNED_WORD_COUNT - 1] & 0x8000) != 0)
        value->limbs[WIDE_SIGNED_WORD_COUNT - 1] |= ~(digit_t)0xffff;
}

static void
encode_signed_result(const ibz_t *value)
{
    // Every result fits 329 signed bits; bits 327..383 must agree to fit 328.
    digit_t high = value->limbs[WORD_COUNT - 1] >> 7;
    if (high != 0 && high != (~(digit_t)0 >> 7))
        printf("overflow");
    else
        encode(value->limbs, ENCODED_BYTES);
}

static void
encode_xgcd_coefficient(const ibz_t *value)
{
    ibz_t minimum = { .bitlen = 8 * ENCODED_BYTES + 1 };
    ibz_t maximum = { .bitlen = 8 * ENCODED_BYTES + 1 };
    for (int index = 0; index < WORD_COUNT - 1; index++)
        maximum.limbs[index] = ~(digit_t)0;
    maximum.limbs[WORD_COUNT - 1] = 0x7f;
    minimum.limbs[WORD_COUNT - 1] = ~(digit_t)0 ^ 0x7f;
    if (ibz_cmp(value, &minimum) < 0 || ibz_cmp(value, &maximum) > 0) {
        printf("overflow");
        return;
    }
    digit_t words[WORD_COUNT];
    digit_t extension = ibz_is_positive(value) ? 0 : ~(digit_t)0;
    int used_words = NUM_LIMBS(value->bitlen);
    for (int index = 0; index < WORD_COUNT; index++)
        words[index] = index < used_words ? value->limbs[index] : extension;
    encode(words, ENCODED_BYTES);
}

static void
encode_signed_wide_result(const ibz_t *value)
{
    digit_t sign = (value->limbs[WIDE_SIGNED_WORD_COUNT - 1] >> 15) & 1;
    digit_t high = value->limbs[WIDE_SIGNED_WORD_COUNT - 1] >> 16;
    digit_t expected = sign ? ~(digit_t)0 >> 16 : 0;
    if (high != expected)
        printf("overflow");
    else
        encode(value->limbs, 2 * ENCODED_BYTES);
}

int
main(int argc, char **argv)
{
    int wide_division = argc == 2 && strcmp(argv[1], "--wide-division") == 0;
    int sqrt_floor_mode = argc == 2 && strcmp(argv[1], "--sqrt-floor") == 0;
    int extended_gcd_mode = argc == 2 && strcmp(argv[1], "--extended-gcd") == 0;
    int gcd_mode = argc == 2 && strcmp(argv[1], "--gcd") == 0;
    int inverse_mode = argc == 2 && strcmp(argv[1], "--inverse") == 0;
    int signed_mode = argc == 2 && strcmp(argv[1], "--signed") == 0;
    int signed_division = argc == 2 && strcmp(argv[1], "--signed-division") == 0;
    int signed_wide_division = argc == 2 && strcmp(argv[1], "--signed-wide-division") == 0;
    int signed_wide_mode = argc == 2 && strcmp(argv[1], "--signed-wide") == 0;
    int field_mode = argc == 2 && strcmp(argv[1], "--field") == 0;
    int field_nonlinear_mode = argc == 2 && strcmp(argv[1], "--field-nonlinear") == 0;
    int fp2_mode = argc == 2 && strcmp(argv[1], "--fp2") == 0;
    int fp2_nonlinear_mode = argc == 2 && strcmp(argv[1], "--fp2-nonlinear") == 0;
    if (argc != 1 && !wide_division && !sqrt_floor_mode && !extended_gcd_mode && !gcd_mode && !inverse_mode &&
        !signed_mode && !signed_division && !signed_wide_division && !signed_wide_mode && !field_mode &&
        !field_nonlinear_mode && !fp2_mode && !fp2_nonlinear_mode) {
        fprintf(stderr,
                "Expected no arguments, --wide-division, --sqrt-floor, --extended-gcd, --gcd, --inverse, "
                "--signed, --signed-division, --signed-wide-division, --signed-wide, --field or "
                "--field-nonlinear, --fp2 or --fp2-nonlinear\n");
        return 1;
    }
    int left_bytes = wide_division || signed_wide_division || signed_wide_mode || fp2_mode || fp2_nonlinear_mode
                         ? 2 * ENCODED_BYTES
                         : ENCODED_BYTES;
    int left_words = wide_division                              ? 2 * WORD_COUNT
                     : signed_wide_division || signed_wide_mode ? WIDE_SIGNED_WORD_COUNT
                     : fp2_mode || fp2_nonlinear_mode           ? 2 * WORD_COUNT
                                                                : WORD_COUNT;
    int right_bytes = signed_wide_mode || fp2_mode ? 2 * ENCODED_BYTES : ENCODED_BYTES;
    int right_words = signed_wide_mode ? WIDE_SIGNED_WORD_COUNT : fp2_mode ? 2 * WORD_COUNT : WORD_COUNT;
    int single_operand_mode = sqrt_floor_mode || field_nonlinear_mode || fp2_nonlinear_mode;
    const char *input_format = signed_wide_mode || fp2_mode ? "%164s %164s" : "%164s %82s";
    char left_hex[WIDE_HEX_CHARS + 1], right_hex[WIDE_HEX_CHARS + 1];
    int fields;
    int expected_fields = single_operand_mode ? 1 : 2;
    const char *single_input_format = fp2_nonlinear_mode ? "%164s" : "%82s";
    while ((fields = single_operand_mode ? scanf(single_input_format, left_hex)
                                         : scanf(input_format, left_hex, right_hex)) == expected_fields) {
        digit_t left[2 * WORD_COUNT], right[2 * WORD_COUNT], sum[WORD_COUNT], difference[WORD_COUNT];
        digit_t product[2 * WORD_COUNT];
        if (!decode(left_hex, left, left_bytes, left_words) ||
            (!single_operand_mode && !decode(right_hex, right, right_bytes, right_words))) {
            fprintf(stderr, "Invalid public unsigned reference operands\n");
            return 1;
        }
        if (sqrt_floor_mode) {
            ibz_t value = { .bitlen = 8 * ENCODED_BYTES + 1 }, root = { 0 };
            memcpy(value.limbs, left, WORD_COUNT * sizeof(digit_t));
            ibz_sqrt_floor(&root, &value);
            encode(left, ENCODED_BYTES);
            putchar(':');
            encode(root.limbs, ENCODED_BYTES);
            putchar('\n');
            continue;
        }
        if (field_nonlinear_mode) {
            unsigned char value_bytes[ENCODED_BYTES], output_bytes[ENCODED_BYTES];
            fp_t value, inverse, root;
            words_to_little_endian(left, value_bytes, ENCODED_BYTES);
            if (fp_decode(&value, value_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical p324_3 field operand\n");
                return 1;
            }
            encode(left, ENCODED_BYTES);
            putchar(':');
            if (fp_is_zero(&value)) {
                putchar('-');
            } else {
                fp_copy(&inverse, &value);
                fp_inv(&inverse);
                fp_encode(output_bytes, &inverse);
                encode_bytes(output_bytes, ENCODED_BYTES);
            }
            printf(":%u:", fp_is_square(&value) != 0);
            fp_copy(&root, &value);
            fp_sqrt(&root);
            fp_encode(output_bytes, &root);
            encode_bytes(output_bytes, ENCODED_BYTES);
            putchar('\n');
            continue;
        }
        if (fp2_nonlinear_mode) {
            unsigned char value_bytes[2 * ENCODED_BYTES];
            fp2_t value, inverse, root, negative_root;
            words_to_little_endian(left, value_bytes, 2 * ENCODED_BYTES);
            if (fp2_decode(&value, value_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical p324_3 extension-field operand\n");
                return 1;
            }
            encode_fp2_fields(&value);
            putchar(':');
            if (fp2_is_zero(&value)) {
                printf("-:-");
            } else {
                fp2_copy(&inverse, &value);
                fp2_inv(&inverse);
                encode_fp2_fields(&inverse);
            }
            uint32_t is_square = fp2_is_square(&value) != 0;
            printf(":%u:", is_square);
            fp2_copy(&root, &value);
            uint32_t has_root = fp2_sqrt_verify(&root);
            if (has_root == 0) {
                printf("-:-");
            } else {
                fp2_neg(&negative_root, &root);
                fp2_select(&root, &negative_root, &root, fp2_less_than(&root, &negative_root));
                encode_fp2_fields(&root);
            }
            putchar('\n');
            continue;
        }
        if (fp2_mode) {
            unsigned char left_bytes[2 * ENCODED_BYTES], right_bytes[2 * ENCODED_BYTES];
            fp2_t a, b, result;
            words_to_little_endian(left, left_bytes, 2 * ENCODED_BYTES);
            words_to_little_endian(right, right_bytes, 2 * ENCODED_BYTES);
            if (fp2_decode(&a, left_bytes) != UINT32_MAX || fp2_decode(&b, right_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical p324_3 extension-field operands\n");
                return 1;
            }
            encode_fp2_fields(&a);
            putchar(':');
            encode_fp2_fields(&b);
            fp2_add(&result, &a, &b);
            putchar(':');
            encode_fp2_fields(&result);
            fp2_sub(&result, &a, &b);
            putchar(':');
            encode_fp2_fields(&result);
            fp2_mul(&result, &a, &b);
            putchar(':');
            encode_fp2_fields(&result);
            fp2_sqr(&result, &a);
            putchar(':');
            encode_fp2_fields(&result);
            putchar('\n');
            continue;
        }
        if (field_mode) {
            unsigned char left_bytes[ENCODED_BYTES], right_bytes[ENCODED_BYTES];
            unsigned char output_bytes[ENCODED_BYTES];
            fp_t a, b, result;
            words_to_little_endian(left, left_bytes, ENCODED_BYTES);
            words_to_little_endian(right, right_bytes, ENCODED_BYTES);
            if (fp_decode(&a, left_bytes) != UINT32_MAX || fp_decode(&b, right_bytes) != UINT32_MAX) {
                fprintf(stderr, "Invalid canonical p324_3 field operands\n");
                return 1;
            }
            encode(left, ENCODED_BYTES);
            putchar(':');
            encode(right, ENCODED_BYTES);
            fp_add(&result, &a, &b);
            putchar(':');
            fp_encode(output_bytes, &result);
            encode_bytes(output_bytes, ENCODED_BYTES);
            fp_sub(&result, &a, &b);
            putchar(':');
            fp_encode(output_bytes, &result);
            encode_bytes(output_bytes, ENCODED_BYTES);
            fp_mul(&result, &a, &b);
            putchar(':');
            fp_encode(output_bytes, &result);
            encode_bytes(output_bytes, ENCODED_BYTES);
            putchar('\n');
            continue;
        }
        encode(left, left_bytes);
        putchar(':');
        encode(right, right_bytes);
        if (extended_gcd_mode) {
            ibz_t a = { 0 }, b = { 0 }, gcd = { 0 }, left_coefficient = { 0 }, right_coefficient = { 0 };
            import_signed(&a, left);
            import_signed(&b, right);
            ibz_xgcd(&gcd, &left_coefficient, &right_coefficient, &a, &b);
            putchar(':');
            encode(gcd.limbs, ENCODED_BYTES);
            putchar(':');
            encode_xgcd_coefficient(&left_coefficient);
            putchar(':');
            encode_xgcd_coefficient(&right_coefficient);
            putchar('\n');
            continue;
        }
        if (signed_division) {
            ibz_t a = { 0 }, b = { 0 }, quotient = { 0 }, remainder = { 0 };
            import_signed(&a, left);
            import_signed(&b, right);
            if (ibz_is_zero(&b)) {
                printf(":zero_divisor:zero_divisor\n");
            } else {
                ibz_div(&quotient, &remainder, &a, &b);
                putchar(':');
                encode_signed_result(&quotient);
                putchar(':');
                encode_signed_result(&remainder);
                putchar('\n');
            }
            continue;
        }
        if (signed_wide_mode) {
            ibz_t a = { 0 }, b = { 0 }, sum = { 0 }, difference = { 0 };
            import_signed_wide(&a, left);
            import_signed_wide(&b, right);
            printf(":%d:", ibz_cmp(&a, &b));
            ibz_add(&sum, &a, &b);
            encode_signed_wide_result(&sum);
            putchar(':');
            ibz_sub(&difference, &a, &b);
            encode_signed_wide_result(&difference);
            putchar('\n');
            continue;
        }
        if (signed_wide_division) {
            ibz_t a = { 0 }, b = { 0 }, quotient = { 0 }, remainder = { 0 };
            import_signed_wide(&a, left);
            import_signed(&b, right);
            if (ibz_is_zero(&b)) {
                printf(":zero_divisor:zero_divisor\n");
            } else {
                ibz_div(&quotient, &remainder, &a, &b);
                putchar(':');
                encode_signed_wide_result(&quotient);
                putchar(':');
                encode_signed_result(&remainder);
                putchar('\n');
            }
            continue;
        }
        if (signed_mode) {
            ibz_t a = { 0 }, b = { 0 }, result = { 0 };
            import_signed(&a, left);
            import_signed(&b, right);
            printf(":%d:", ibz_cmp(&a, &b));
            ibz_add(&result, &a, &b);
            encode_signed_result(&result);
            putchar(':');
            ibz_sub(&result, &a, &b);
            encode_signed_result(&result);
            putchar(':');
            ibz_mul(&result, &a, &b);
            encode(result.limbs, 2 * ENCODED_BYTES);
            putchar('\n');
            continue;
        }
        if (gcd_mode || inverse_mode) {
            // A 329-bit signed bound leaves the sign bit clear for every 328-bit input.
            ibz_t a = { .bitlen = 8 * ENCODED_BYTES + 1 };
            ibz_t b = { .bitlen = 8 * ENCODED_BYTES + 1 };
            ibz_t result = { 0 };
            memcpy(a.limbs, left, WORD_COUNT * sizeof(digit_t));
            memcpy(b.limbs, right, WORD_COUNT * sizeof(digit_t));
            putchar(':');
            if (gcd_mode) {
                ibz_gcd(&result, &a, &b);
                encode(result.limbs, ENCODED_BYTES);
            } else if (ibz_cmp_int32(&b, 2) < 0) {
                printf("invalid_modulus");
            } else if (ibz_invmod(&result, &a, &b) == 0) {
                printf("not_coprime");
            } else {
                encode(result.limbs, ENCODED_BYTES);
            }
            putchar('\n');
            continue;
        }
        if (!wide_division) {
            digit_t wide_carry = mp_add_raw(sum, WORD_COUNT, left, WORD_COUNT, right, WORD_COUNT);
            if (wide_carry != 0) {
                fprintf(stderr, "Unexpected overflow beyond 384 bits\n");
                return 1;
            }
            unsigned carry = (unsigned)(sum[WORD_COUNT - 1] >> 8);
            digit_t borrow = mp_sub_raw(difference, WORD_COUNT, left, WORD_COUNT, right, WORD_COUNT);
            mp_mul_raw(product, left, WORD_COUNT, right, WORD_COUNT);
            putchar(':');
            encode(sum, ENCODED_BYTES);
            printf(":%u:", carry);
            encode(difference, ENCODED_BYTES);
            printf(":%u:%d:", (unsigned)borrow, mp_compare(left, right, WORD_COUNT));
            encode(product, 2 * ENCODED_BYTES);
        }
        int divisor_words = WORD_COUNT;
        while (divisor_words > 0 && right[divisor_words - 1] == 0)
            divisor_words--;
        if (divisor_words == 0) {
            printf(":-:-");
        } else {
            digit_t quotient[2 * WORD_COUNT] = { 0 }, remainder[WORD_COUNT] = { 0 };
            mp_divmod_schoolbook(quotient, remainder, left, left_words, right, divisor_words);
            putchar(':');
            encode(quotient, left_bytes);
            putchar(':');
            encode(remainder, ENCODED_BYTES);
        }
        putchar('\n');
    }
    if (fields != EOF || ferror(stdin)) {
        fprintf(stderr, "Incomplete unsigned reference input\n");
        return 1;
    }
    return 0;
}
