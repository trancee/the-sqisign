// SPDX-License-Identifier: Apache-2.0

// Including the reference translation unit exposes its static raw unsigned helpers
// without adding a public C API. Unused functions are discarded at link time.
#include "../../src/mp/ref/lvlx/mp.c"

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
    int gcd_mode = argc == 2 && strcmp(argv[1], "--gcd") == 0;
    int inverse_mode = argc == 2 && strcmp(argv[1], "--inverse") == 0;
    int signed_mode = argc == 2 && strcmp(argv[1], "--signed") == 0;
    int signed_division = argc == 2 && strcmp(argv[1], "--signed-division") == 0;
    int signed_wide_division = argc == 2 && strcmp(argv[1], "--signed-wide-division") == 0;
    int signed_wide_mode = argc == 2 && strcmp(argv[1], "--signed-wide") == 0;
    if (argc != 1 && !wide_division && !sqrt_floor_mode && !gcd_mode && !inverse_mode && !signed_mode &&
        !signed_division && !signed_wide_division && !signed_wide_mode) {
        fprintf(stderr,
                "Expected no arguments, --wide-division, --sqrt-floor, --gcd, --inverse, --signed, "
                "--signed-division, --signed-wide-division or --signed-wide\n");
        return 1;
    }
    int left_bytes = wide_division || signed_wide_division || signed_wide_mode ? 2 * ENCODED_BYTES : ENCODED_BYTES;
    int left_words = wide_division                              ? 2 * WORD_COUNT
                     : signed_wide_division || signed_wide_mode ? WIDE_SIGNED_WORD_COUNT
                                                                : WORD_COUNT;
    int right_bytes = signed_wide_mode ? 2 * ENCODED_BYTES : ENCODED_BYTES;
    int right_words = signed_wide_mode ? WIDE_SIGNED_WORD_COUNT : WORD_COUNT;
    const char *input_format = signed_wide_mode ? "%164s %164s" : "%164s %82s";
    char left_hex[WIDE_HEX_CHARS + 1], right_hex[WIDE_HEX_CHARS + 1];
    int fields;
    int expected_fields = sqrt_floor_mode ? 1 : 2;
    while ((fields = sqrt_floor_mode ? scanf("%82s", left_hex) : scanf(input_format, left_hex, right_hex)) ==
           expected_fields) {
        digit_t left[2 * WORD_COUNT], right[2 * WORD_COUNT], sum[WORD_COUNT], difference[WORD_COUNT];
        digit_t product[2 * WORD_COUNT];
        if (!decode(left_hex, left, left_bytes, left_words) ||
            (!sqrt_floor_mode && !decode(right_hex, right, right_bytes, right_words))) {
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
        encode(left, left_bytes);
        putchar(':');
        encode(right, right_bytes);
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
