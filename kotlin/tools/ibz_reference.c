// SPDX-License-Identifier: Apache-2.0

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// The pinned bounded-integer implementation remains an external test oracle.
#include "../../src/mp/ref/lvlx/modqx.c"
#include "../../src/mp/ref/lvlx/mp.c"
#include "../../src/quaternion/ref/lvlx/lll/lehmer_xgcd.c"
#include "../../src/quaternion/ref/lvlx/integers.c"
#include "../../src/quaternion/ref/lvlx/lll/lll_lg2.c"
#include "../../src/quaternion/ref/lvlx/lll/lll_dim4.c"
#include "../../src/quaternion/ref/lvlx/lll/gaussian_xgcd.c"

enum
{
    LINE_BYTES = 16384,
    MAX_RANDOM_BYTES = 8192
};

SQISIGN_THREAD_LOCAL prng_domain_ctx_t PRNG_default_domain;
static void fail(const char *reason);
static unsigned char scripted[MAX_RANDOM_BYTES];
static size_t scripted_length;
static size_t scripted_offset;

int
prng_random_bytes(prng_domain_ctx_t *ctx, unsigned char *out, size_t len)
{
    (void)ctx;
    // Callers such as ibz_sqrt_m1_mod retry forever on PRNG failure, so exhaustion must stop the adapter.
    if (len > scripted_length - scripted_offset)
        fail("random bytes exhausted");
    memcpy(out, scripted + scripted_offset, len);
    scripted_offset += len;
    return 0;
}

static void
fail(const char *reason)
{
    fprintf(stderr, "Invalid ibz adapter input: %s\n", reason);
    exit(1);
}

static const char *
required(const char *text)
{
    if (text == NULL)
        fail("missing field");
    return text;
}

static int
hex_digit(char character)
{
    if (character >= '0' && character <= '9')
        return character - '0';
    if (character >= 'a' && character <= 'f')
        return character - 'a' + 10;
    fail("hex digit");
    return 0;
}

static void
decode_magnitude(digit_t *words, int count, const char *hex)
{
    size_t length = strlen(required(hex));
    memset(words, 0, (size_t)count * sizeof(digit_t));
    if (length == 0 || length > (size_t)count * 16)
        fail("magnitude width");
    for (size_t index = 0; index < length; index++) {
        size_t nibble = length - 1 - index;
        words[nibble / 16] |= (digit_t)hex_digit(hex[index]) << (4 * (nibble % 16));
    }
}

static void
decode(ibz_t *value, const char *text)
{
    char *colon;
    long bitlen = strtol(required(text), &colon, 10);
    if (*colon != ':' || bitlen < 1 || bitlen > IBZ_NLIMBS * NUM_BITS_LIMB)
        fail("ibz bound");
    const char *hex = colon + 1;
    int negative = *hex == '-';
    memset(value, 0, sizeof(*value));
    decode_magnitude(value->limbs, IBZ_NLIMBS, hex + negative);
    value->bitlen = (int)bitlen;
    if (negative)
        ibz_neg(value, value);
}

static void
print_ibz(const ibz_t *value)
{
    int count = NUM_LIMBS(value->bitlen);
    digit_t magnitude[IBZ_NLIMBS];
    int negative = (int)(value->limbs[count - 1] >> (NUM_BITS_LIMB - 1));
    digit_t carry = (digit_t)negative;
    for (int index = 0; index < count; index++) {
        digit_t word = negative ? ~value->limbs[index] : value->limbs[index];
        magnitude[index] = word + carry;
        carry = carry && magnitude[index] == 0;
    }
    int top = count - 1;
    while (top > 0 && magnitude[top] == 0)
        top--;
    printf("%d:%s%" PRIx64, value->bitlen, negative ? "-" : "", (uint64_t)magnitude[top]);
    for (int index = top - 1; index >= 0; index--)
        printf("%016" PRIx64, (uint64_t)magnitude[index]);
}

static long
number(const char *text)
{
    char *end;
    long value = strtol(required(text), &end, 10);
    if (*end != '\0')
        fail("integer");
    return value;
}

static int
sign(int comparison)
{
    return (comparison > 0) - (comparison < 0);
}

static void
script(const char *hex)
{
    size_t length = strlen(required(hex));
    if (length % 2 || length / 2 > MAX_RANDOM_BYTES)
        fail("random bytes");
    for (size_t index = 0; index < length / 2; index++)
        scripted[index] = (unsigned char)(16 * hex_digit(hex[2 * index]) + hex_digit(hex[2 * index + 1]));
    scripted_length = length / 2;
    scripted_offset = 0;
}

static void
finish_script(void)
{
    if (scripted_offset != scripted_length)
        fail("unconsumed random bytes");
}

static void
binary(const char *op, char **arguments)
{
    ibz_t a, b, q, r;
    decode(&a, arguments[0]);
    decode(&b, arguments[1]);
    if (strcmp(op, "add") == 0) {
        ibz_add(&r, &a, &b);
        print_ibz(&r);
    } else if (strcmp(op, "sub") == 0) {
        ibz_sub(&r, &a, &b);
        print_ibz(&r);
    } else if (strcmp(op, "mul") == 0) {
        ibz_mul(&r, &a, &b);
        print_ibz(&r);
    } else if (strcmp(op, "cmp") == 0) {
        printf("%d", sign(ibz_cmp(&a, &b)));
    } else if (strcmp(op, "div") == 0) {
        ibz_div(&q, &r, &a, &b);
        print_ibz(&q);
        putchar(' ');
        print_ibz(&r);
    } else if (strcmp(op, "mod") == 0) {
        ibz_mod(&r, &a, &b);
        print_ibz(&r);
    } else if (strcmp(op, "divides") == 0) {
        printf("%d", ibz_divides(&a, &b) != 0);
    } else if (strcmp(op, "addb") == 0) {
        ibz_add_and_set_bound(&r, &a, &b, (int)number(arguments[2]));
        print_ibz(&r);
    } else {
        fail("operation");
    }
}

static void
bound(const ibz_t *a, int bitlen)
{
    ibz_t plain = *a, ct = *a;
    ibz_set_bound(&plain, bitlen);
    ibz_set_bound_ct(&ct, bitlen);
    if (plain.bitlen != ct.bitlen || memcmp(plain.limbs, ct.limbs, NUM_LIMBS(plain.bitlen) * sizeof(digit_t)))
        fail("set_bound and set_bound_ct disagree");
    print_ibz(&plain);
}

static void
info(const ibz_t *a)
{
    if (ibz_bitsize(a) != ibz_bitsize_ct(a))
        fail("bitsize and bitsize_ct disagree");
    printf("%d %d %d %d %d %d %" PRId32,
           ibz_bitsize(a),
           ibz_two_adic(a),
           ibz_is_zero(a) != 0,
           ibz_is_one(a) != 0,
           ibz_is_positive(a) != 0,
           ibz_is_even(a) != 0,
           ibz_get(a));
    if ((ibz_is_even(a) != 0) == (ibz_is_odd(a) != 0))
        fail("even and odd agree");
}

static void
digits(const ibz_t *a)
{
    digit_t words[IBZ_NLIMBS] = { 0 };
    int count = NUM_LIMBS(ibz_bitsize(a));
    ibz_to_digits(words, a);
    printf("%d ", count);
    if (count == 0)
        putchar('-');
    for (int index = count - 1; index >= 0; index--)
        printf("%016" PRIx64, (uint64_t)words[index]);
}

static void
unary(const char *op, char **arguments)
{
    ibz_t a, r;
    char text[IBZ_NLIMBS * NUM_BITS_LIMB + 2];
    decode(&a, arguments[0]);
    if (strcmp(op, "neg") == 0) {
        ibz_neg(&r, &a);
    } else if (strcmp(op, "abs") == 0) {
        ibz_abs(&r, &a);
    } else if (strcmp(op, "shr") == 0) {
        ibz_div_2exp(&r, &a, (uint32_t)number(arguments[1]));
    } else if (strcmp(op, "shl") == 0) {
        ibz_mul_2exp(&r, &a, (uint32_t)number(arguments[1]));
    } else if (strcmp(op, "mod2exp") == 0) {
        ibz_mod2exp(&r, &a, (uint32_t)number(arguments[1]));
    } else if (strcmp(op, "pow") == 0) {
        ibz_pow(&r, &a, (uint32_t)number(arguments[1]), 32);
    } else if (strcmp(op, "mulint") == 0) {
        ibz_mul_by_int_and_set_bound(&r, &a, (int32_t)number(arguments[1]), (int)number(arguments[2]));
    } else if (strcmp(op, "addint") == 0) {
        ibz_add_int_and_set_bound(&r, &a, (int32_t)number(arguments[1]), (int)number(arguments[2]));
    } else if (strcmp(op, "bound") == 0) {
        bound(&a, (int)number(arguments[1]));
        return;
    } else if (strcmp(op, "info") == 0) {
        info(&a);
        return;
    } else if (strcmp(op, "extract") == 0) {
        int offset = (int)number(arguments[1]);
        printf("%" PRIx64 " %" PRId64, ibz_extract_u64(&a, offset), ibz_extract_i64(&a, offset));
        return;
    } else if (strcmp(op, "size") == 0) {
        printf("%d", ibz_size_in_base(&a, (int)number(arguments[1])));
        return;
    } else if (strcmp(op, "str") == 0) {
        if (!ibz_convert_to_str(&a, text, (int)number(arguments[1])))
            fail("conversion");
        printf("%s", text);
        return;
    } else if (strcmp(op, "cmpi") == 0) {
        printf("%d", sign(ibz_cmp_int32(&a, (int32_t)number(arguments[1]))));
        return;
    } else if (strcmp(op, "modui") == 0) {
        printf("%lu", ibz_mod_ui(&a, strtoul(required(arguments[1]), NULL, 10)));
        return;
    } else if (strcmp(op, "digits") == 0) {
        digits(&a);
        return;
    } else {
        fail("operation");
    }
    print_ibz(&r);
}

static void
print_flag(int value)
{
    printf("%d", value != 0);
}

static void
print_optional(int found, const ibz_t *value)
{
    print_flag(found);
    putchar(' ');
    if (value->bitlen == 1)
        putchar('-');
    else
        print_ibz(value);
}

static void
inverse_matrix(char **arguments)
{
    ibz_t entries[4];
    for (int index = 0; index < 4; index++)
        decode(&entries[index], arguments[index]);
    print_flag(ibz_invmat(&entries[0], &entries[1], &entries[2], &entries[3], (int)number(arguments[4])));
    for (int index = 0; index < 4; index++) {
        putchar(' ');
        print_ibz(&entries[index]);
    }
}

static void
square_roots(const char *op, char **arguments)
{
    // An untouched output keeps bitlen 1, which no written root modulo p > 1 can have.
    ibz_t a, p, r = { 0 };
    r.bitlen = 1;
    if (strcmp(op, "sqrtp") == 0) {
        decode(&a, arguments[0]);
        decode(&p, arguments[1]);
        script(strcmp(required(arguments[2]), "_") == 0 ? "" : arguments[2]);
        print_optional(ibz_sqrt_mod_p(&r, &a, &p), &r);
    } else if (strcmp(op, "sqrtm1") == 0) {
        decode(&p, arguments[0]);
        script(strcmp(required(arguments[1]), "_") == 0 ? "" : arguments[1]);
        ibz_sqrt_m1_mod(&r, &p);
        print_ibz(&r);
    } else {
        decode(&p, arguments[0]);
        print_optional(ibz_sqrt_m1_mod_verified(&r, &p), &r);
        return;
    }
    finish_script();
}

static int
number_theory(const char *op, char **arguments)
{
    ibz_t a, b, c, d, r, u, v;
    if (strcmp(op, "sqrtp") == 0 || strcmp(op, "sqrtm1") == 0 || strcmp(op, "sqrtm1v") == 0) {
        square_roots(op, arguments);
    } else if (strcmp(op, "invmat") == 0) {
        inverse_matrix(arguments);
    } else if (strcmp(op, "prime") == 0) {
        decode(&a, arguments[0]);
        int rounds = (int)number(arguments[1]);
        script(strcmp(required(arguments[2]), "_") == 0 ? "" : arguments[2]);
        print_flag(ibz_probab_prime(&a, rounds));
        finish_script();
    } else if (strcmp(op, "legendre") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        printf("%d", ibz_legendre(&a, &b));
    } else if (strcmp(op, "sqrtfloor") == 0) {
        decode(&a, arguments[0]);
        ibz_sqrt_floor(&r, &a);
        print_ibz(&r);
    } else if (strcmp(op, "gcd") == 0 || strcmp(op, "xgcd") == 0 || strcmp(op, "invmod") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        if (strcmp(op, "gcd") == 0) {
            ibz_gcd(&r, &a, &b);
            print_ibz(&r);
        } else if (strcmp(op, "xgcd") == 0) {
            ibz_xgcd(&r, &u, &v, &a, &b);
            print_ibz(&r);
            putchar(' ');
            print_ibz(&u);
            putchar(' ');
            print_ibz(&v);
        } else {
            print_flag(ibz_invmod(&r, &a, &b));
            putchar(' ');
            print_ibz(&r);
        }
    } else if (strcmp(op, "powmod") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        decode(&c, arguments[2]);
        ibz_pow_mod(&r, &a, &b, &c);
        print_ibz(&r);
    } else if (strcmp(op, "crt") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        decode(&c, arguments[2]);
        decode(&d, arguments[3]);
        ibz_crt(&r, &a, &b, &c, &d);
        print_ibz(&r);
    } else {
        return 0;
    }
    return 1;
}

static int
fixed_point(const char *op, char **arguments)
{
    ibz_t a, b, r;
    if (strcmp(op, "cshift") == 0) {
        decode(&a, arguments[0]);
        ibz_ct_shift(&r, &a, (int32_t)number(arguments[1]), (int32_t)number(arguments[2]), (int)number(arguments[3]));
    } else if (strcmp(op, "highp") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        ibz_ct_highmul_p(&r, &a, &b, (int)number(arguments[2]), (int)number(arguments[3]));
    } else if (strcmp(op, "highs") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        ibz_ct_highmul_s(
            &r, &a, &b, (int32_t)number(arguments[2]), (int32_t)number(arguments[3]), (int)number(arguments[4]));
    } else if (strcmp(op, "recip") == 0) {
        int32_t size;
        decode(&a, arguments[0]);
        ibz_ct_fp_recip(&r, &size, &a, (int)number(arguments[1]), (int)number(arguments[2]));
        print_ibz(&r);
        printf(" %" PRId32, size);
        return 1;
    } else if (strcmp(op, "rshift") == 0) {
        decode(&a, arguments[0]);
        ibz_ct_round_shift_limb(&r, &a, (int)number(arguments[1]), (int)number(arguments[2]));
    } else if (strcmp(op, "ltmask") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        printf("%d", (int)(sdigit_t)ibz_ct_lt_mask(&a, &b));
        return 1;
    } else {
        return 0;
    }
    print_ibz(&r);
    return 1;
}

static int
lattice(const char *op, char **arguments)
{
    ibz_t a, b, c, r;
    if (strcmp(op, "squares") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        quat_lll_dim2_sumofsquares(&r, &c, &a, &b);
        print_ibz(&r);
        printf(" ");
        print_ibz(&c);
        return 1;
    } else if (strcmp(op, "cornacchia") == 0) {
        decode(&a, arguments[0]);
        if (!ibz_cornacchia_prime(&r, &b, &a)) {
            printf("0");
            return 1;
        }
        printf("1 ");
        print_ibz(&r);
        printf(" ");
        print_ibz(&b);
        return 1;
    } else if (strcmp(op, "rdiv") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        ibz_rounded_div(&r, &a, &b);
    } else if (strcmp(op, "sq2") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        ibz_sum_two_squares(&r, &a, &b);
    } else {
        return 0;
    }
    print_ibz(&r);
    return 1;
}

static int
gram(const char *op, char **arguments)
{
    quat_lll_gram2_t form;
    ibz_t a, b, c, U[2][2];
    int32_t M[2][2];
    if (strcmp(op, "outer") == 0) {
        printf("%d", lg_outer_its_for_round((int)number(arguments[0])));
    } else if (strcmp(op, "gmsb") == 0) {
        partial_gram_msb(strtoull(required(arguments[0]), NULL, 16),
                         (int64_t)strtoull(required(arguments[1]), NULL, 16),
                         strtoull(required(arguments[2]), NULL, 16),
                         M,
                         (uint64_t)number(arguments[3]));
        printf("%" PRId32 " %" PRId32 " %" PRId32 " %" PRId32, M[0][0], M[0][1], M[1][0], M[1][1]);
    } else if (strcmp(op, "block") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        decode(&c, arguments[2]);
        quat_lll_materialise_block(&form, &a, &b, &c, FP_P_BITS);
        print_ibz(&form.a);
        printf(" ");
        print_ibz(&form.b);
        printf(" ");
        print_ibz(&form.c);
        printf(" %" PRId32, form.e);
    } else if (strcmp(op, "gram") == 0) {
        decode(&form.a, arguments[0]);
        decode(&form.b, arguments[1]);
        decode(&form.c, arguments[2]);
        quat_lll_gram_lehmer(&form, U, (int)number(arguments[3]));
        print_ibz(&form.a);
        printf(" ");
        print_ibz(&form.b);
        printf(" ");
        print_ibz(&form.c);
        for (int index = 0; index < 4; index++) {
            printf(" ");
            print_ibz(&U[index / 2][index % 2]);
        }
    } else {
        return 0;
    }
    return 1;
}

static void
print_ldl(const fp_ldl_t *st)
{
    for (int i = 0; i < 4; i++) {
        printf(i ? " " : "");
        print_ibz(&st->D[i]);
    }
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < i; j++) {
            printf(" ");
            print_ibz(&st->L[i][j]);
        }
    }
    for (int i = 0; i < 16; i++) {
        printf(" ");
        print_ibz(&st->Ainv[i / 4][i % 4]);
    }
}

static int
dual(const char *op, char **arguments)
{
    if (strcmp(op, "ldl") != 0 && strcmp(op, "tour") != 0 && strcmp(op, "dual") != 0)
        return 0;
    ibz_t twoN, E, Dq, C, B;
    quat_alg_t alg;
    decode(&twoN, arguments[0]);
    decode(&E, arguments[1]);
    decode(&Dq, arguments[2]);
    decode(&C, arguments[3]);
    decode(&B, arguments[4]);
    decode(&alg.p, arguments[5]);
    if (strcmp(op, "dual") != 0) {
        fp_ldl_t st;
        fp_ldl_init_dual(&st, &twoN, &B, &C, &Dq, &E, &alg.p, FP_P_BITS);
        if (strcmp(op, "tour") == 0)
            ct_lll_tour4(&st, FP_P_BITS);
        print_ldl(&st);
        return 1;
    }
    quat_lattice_t hnf, reduced;
    ibz_mat_4x4_t transform;
    ibz_t diagonal[4];
    ibz_set(&hnf.denom, 2, 3);
    for (int i = 0; i < 16; i++)
        ibz_set(&hnf.basis.m[i / 4][i % 4], i % 5 == 0, 2);
    ibz_copy(&hnf.basis.m[0][0], &twoN);
    ibz_copy(&hnf.basis.m[1][1], &twoN);
    ibz_copy(&hnf.basis.m[0][2], &E);
    ibz_copy(&hnf.basis.m[0][3], &Dq);
    ibz_copy(&hnf.basis.m[1][2], &C);
    ibz_copy(&hnf.basis.m[1][3], &B);
    quat_lll_dual_reduce_ideal(&reduced, diagonal, &transform, &hnf, &alg);
    for (int i = 0; i < 16; i++) {
        printf(i ? " " : "");
        print_ibz(&reduced.basis.m[i / 4][i % 4]);
    }
    for (int i = 0; i < 4; i++) {
        printf(" ");
        print_ibz(&diagonal[i]);
    }
    for (int i = 0; i < 16; i++) {
        printf(" ");
        print_ibz(&transform.m[i / 4][i % 4]);
    }
    return 1;
}

static int64_t
signed_word(const char *text)
{
    char *end = NULL;
    uint64_t value = strtoull(required(text), &end, 16);
    if (*end != '\0')
        fail("word");
    return (int64_t)value;
}

static int
gaussian(const char *op, char **arguments)
{
    if (strcmp(op, "ginner") == 0) {
        i64_complex_t words[4];
        for (int i = 0; i < 4; i++)
            words[i] = (i64_complex_t){ signed_word(arguments[2 * i]), signed_word(arguments[2 * i + 1]) };
        i32_complex_t matrix[2][2];
        (void)LG_gaussian_inner(words[0],
                                words[1],
                                words[2],
                                words[3],
                                matrix,
                                (uint64_t)number(arguments[8]),
                                (int32_t)number(arguments[9]),
                                (int32_t)number(arguments[10]));
        for (int i = 0; i < 4; i++)
            printf(
                i ? " %" PRId32 " %" PRId32 : "%" PRId32 " %" PRId32, matrix[i / 2][i % 2].re, matrix[i / 2][i % 2].im);
        return 1;
    }
    if (strcmp(op, "gauss") != 0)
        return 0;
    quat_lattice_t hnf, reduced;
    quat_alg_t alg;
    ibz_set(&hnf.denom, 2, 3);
    for (int i = 0; i < 16; i++)
        ibz_set(&hnf.basis.m[i / 4][i % 4], i % 5 == 0, 2);
    decode(&hnf.basis.m[0][0], arguments[0]);
    ibz_copy(&hnf.basis.m[1][1], &hnf.basis.m[0][0]);
    decode(&hnf.basis.m[0][2], arguments[1]);
    decode(&hnf.basis.m[0][3], arguments[2]);
    decode(&hnf.basis.m[1][2], arguments[3]);
    decode(&hnf.basis.m[1][3], arguments[4]);
    decode(&alg.p, arguments[5]);
    quat_lll_reduce_O0_ideal(&reduced, &hnf, &alg, (int)number(arguments[6]));
    for (int i = 0; i < 16; i++) {
        printf(i ? " " : "");
        print_ibz(&reduced.basis.m[i / 4][i % 4]);
    }
    return 1;
}

static void
special(const char *op, char **arguments)
{
    ibz_t a, b, r;
    if (strcmp(op, "set") == 0) {
        ibz_set(&r, (sdigit_t)number(arguments[0]), (int)number(arguments[1]));
    } else if (strcmp(op, "parse") == 0) {
        const char *text = strcmp(required(arguments[0]), "_") == 0 ? "" : arguments[0];
        if (!ibz_set_from_str(&r, text, (int)number(arguments[1]))) {
            printf("fail");
            return;
        }
    } else if (strcmp(op, "bits") == 0) {
        digit_t words[IBZ_NLIMBS];
        decode_magnitude(words, IBZ_NLIMBS, arguments[1]);
        ibz_copy_bits(&r, words, (int)number(arguments[0]));
    } else if (strcmp(op, "rand") == 0) {
        decode(&a, arguments[0]);
        decode(&b, arguments[1]);
        script(arguments[2]);
        if (!ibz_rand_interval_with_domain(&r, &a, &b, &PRNG_default_domain))
            fail("random interval");
        finish_script();
    } else if (strcmp(op, "randm") == 0) {
        script(arguments[1]);
        if (!ibz_rand_interval_minm_m_with_domain(&r, (int32_t)number(arguments[0]), &PRNG_default_domain))
            fail("random symmetric interval");
        finish_script();
    } else {
        if (!fixed_point(op, arguments) && !lattice(op, arguments) && !gram(op, arguments) && !dual(op, arguments) &&
            !gaussian(op, arguments) && !number_theory(op, arguments))
            unary(op, arguments);
        return;
    }
    print_ibz(&r);
}

int
main(void)
{
    static char line[LINE_BYTES];
    while (fgets(line, sizeof(line), stdin)) {
        char *fields[17] = { NULL };
        int count = 0;
        for (char *token = strtok(line, " \n"); token; token = strtok(NULL, " \n")) {
            if (count == 16)
                fail("field count");
            fields[count++] = token;
        }
        if (count < 2)
            fail("record");
        const char *op = fields[0];
        int is_binary = strcmp(op, "add") == 0 || strcmp(op, "sub") == 0 || strcmp(op, "mul") == 0 ||
                        strcmp(op, "cmp") == 0 || strcmp(op, "div") == 0 || strcmp(op, "mod") == 0 ||
                        strcmp(op, "divides") == 0 || strcmp(op, "addb") == 0;
        if (is_binary)
            binary(op, fields + 1);
        else
            special(op, fields + 1);
        putchar('\n');
    }
    return 0;
}
