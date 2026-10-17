// SPDX-License-Identifier: Apache-2.0

// Including the reference translation unit exposes its static raw unsigned helpers
// without adding a public C API. Unused functions are discarded at link time.
#include "../../src/mp/ref/lvlx/mp.c"

enum
{
    ENCODED_BYTES = 41,
    HEX_CHARS = 2 * ENCODED_BYTES,
    WIDE_HEX_CHARS = 2 * HEX_CHARS,
    WORD_COUNT = 6
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

int
main(int argc, char **argv)
{
    int wide_division = argc == 2 && strcmp(argv[1], "--wide-division") == 0;
    if (argc != 1 && !wide_division) {
        fprintf(stderr, "Expected no arguments or --wide-division\n");
        return 1;
    }
    int left_bytes = wide_division ? 2 * ENCODED_BYTES : ENCODED_BYTES;
    int left_words = wide_division ? 2 * WORD_COUNT : WORD_COUNT;
    char left_hex[WIDE_HEX_CHARS + 1], right_hex[HEX_CHARS + 1];
    int fields;
    while ((fields = scanf("%164s %82s", left_hex, right_hex)) == 2) {
        digit_t left[2 * WORD_COUNT], right[WORD_COUNT], sum[WORD_COUNT], difference[WORD_COUNT];
        digit_t product[2 * WORD_COUNT];
        if (!decode(left_hex, left, left_bytes, left_words) || !decode(right_hex, right, ENCODED_BYTES, WORD_COUNT)) {
            fprintf(stderr, "Invalid public unsigned reference operands\n");
            return 1;
        }
        encode(left, left_bytes);
        putchar(':');
        encode(right, ENCODED_BYTES);
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
