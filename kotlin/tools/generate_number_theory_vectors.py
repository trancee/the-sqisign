#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the variable-time ibz_t number theory used by the quaternion layer.

Values follow the textbook definitions (Euclid, Jacobi, Hensel lifting, Tonelli-Shanks, Baillie-PSW)
on Python integers; result bit lengths and random-byte consumption follow the ibz_t contract.
Pinned C src/mp/ref/lvlx/mp.c checks every record.
"""

import argparse
from math import isqrt
from pathlib import Path

from generate_ibz_vectors import (CAPACITIES, ROOT, Z, add, ibz_set, limbs, mod, mod2exp, mul, operands, picks,
                                  random_bytes, random_interval, sample)


OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/arithmetic/IbzNumberTheoryVectors.kt"
ONE, TWO = Z(2, 1), Z(3, 2)
SIEVE_PRIMES = [q for q in range(3, 510) if all(q % d for d in range(2, isqrt(q) + 1))]
JACOBI_BASES = [q for q in range(2, 140) if all(q % d for d in range(2, isqrt(q) + 1))]
EXTRA_ROUNDS_AFTER = 24
# C materialises whole Euclidean quotients, so cofactor-tracking operands stay one limb below capacity.
QUOTIENT_HEADROOM = 64
MONTGOMERY_RADIX = 61


class Stream:
    """Deterministic random-byte source that records exactly what the model consumed."""

    def __init__(self, label):
        self.data = random_bytes(label, 1 << 14)
        self.offset = 0

    def take(self, count):
        chunk = self.data[self.offset:self.offset + count]
        self.offset += count
        return chunk

    def text(self):
        return self.data[:self.offset].hex() or "_"


def tight(value):
    return Z(abs(value).bit_length() + 1, value)


def jacobi(a, n):
    assert n > 0 and n % 2 == 1 and a >= 0
    a %= n
    result = 1
    while a:
        while a % 2 == 0:
            a //= 2
            if n % 8 in (3, 5):
                result = -result
        a, n = n, a
        if a % 4 == 3 and n % 4 == 3:
            result = -result
        a %= n
    return result if n == 1 else 0


def gcd(a, b):
    n = max(a.bitlen, b.bitlen)
    if a.value == 0 and b.value == 0:
        return Z(1, 0)
    x, y = abs(a.value), abs(b.value)
    while y:
        x, y = y, x % y
    return Z(n, x)


def xgcd(a, b):
    n = max(a.bitlen, b.bitlen)
    x, y = abs(a.value), abs(b.value)
    if x >= y:
        current, following = (1, 0), (0, 1)
    else:
        x, y = y, x
        current, following = (0, 1), (1, 0)
    while y:
        quotient = x // y
        x, y = y, x - quotient * y
        current, following = following, (current[0] - quotient * following[0],
                                          current[1] - quotient * following[1])
    u = tight(current[0] * (-1 if a.value < 0 else 1))
    v = tight(current[1] * (-1 if b.value < 0 else 1))
    return Z(n, x), u, v


def invmod(a, modulus, capacity):
    x, y = abs(a.value), modulus.value
    swapped = x < y
    if swapped:
        x, y = y, x
    current, following = (0, 1) if swapped else (1, 0)
    while y:
        quotient = x // y
        x, y = y, x - quotient * y
        current, following = following, current - quotient * following
    coefficient = tight(-current if a.value < 0 else current)
    return x == 1, mod(coefficient, modulus, capacity)


def crt(a1, a2, m1, m2, capacity):
    inverse = invmod(m1, m2, capacity)[1]
    difference = add(a2, a1, capacity, negate=True)
    assert difference.bitlen + inverse.bitlen <= capacity
    residue = mod(mul(difference, inverse, capacity), m2, capacity)
    shifted = add(a1, mul(m1, residue, capacity), capacity)
    assert m1.bitlen + m2.bitlen <= capacity
    return mod(shifted, mul(m1, m2, capacity), capacity)


def invmod_2e(a, exponent, capacity):
    if exponent == 0:
        return Z(1, 0)
    x, precision = ONE, 1
    while precision < exponent:
        following = min(2 * precision, exponent)
        t = add(TWO, mul(a, x, capacity), capacity, negate=True)
        x = mod2exp(mul(x, t, capacity), following)
        precision = following
    return mod2exp(x, exponent)


def invmat(entries, exponent, capacity):
    a, b, c, d = (Z(exponent + 1, z.value) if z.bitlen > exponent + 1 else z for z in entries)
    determinant = add(mul(a, d, capacity), mul(b, c, capacity), capacity, negate=True)
    exists = determinant.value % 2 == 1
    inverse = invmod_2e(determinant, exponent, capacity)
    result = [mod2exp(mul(x, inverse, capacity), exponent)
              for x in (d, Z(b.bitlen, -b.value), Z(c.bitlen, -c.value), a)]
    return exists, result


def sqrt_floor(a):
    root = isqrt(a.value)
    return Z(root.bit_length() + 1, root)


def tonelli_shanks(a, p, stream, capacity):
    pm1 = add(p, ONE, capacity, negate=True)
    s = ((pm1.value & -pm1.value).bit_length() - 1)
    q = pm1.value >> s
    while True:
        z = random_interval(ONE, pm1, stream.take((pm1.bitlen + 64 + 7) // 8), capacity).value
        if jacobi(z, p.value) == -1:
            break
    c, t, root = pow(z, q, p.value), pow(a.value, q, p.value), pow(a.value, (q + 1) // 2, p.value)
    order = s
    while t != 1:
        probe, i = t, 0
        while probe != 1:
            probe = probe * probe % p.value
            i += 1
            if i >= order:
                return None
        b = pow(c, 1 << (order - i - 1), p.value)
        root, c = root * b % p.value, b * b % p.value
        t, order = t * c % p.value, i
    return root


def sqrt_mod_p(a, p, stream, capacity):
    if p.value % 4 == 3:
        root = pow(a.value, (p.value + 1) // 4, p.value)
        return root * root % p.value == a.value, Z(p.bitlen, root)
    if p.value % 4 == 1:
        root = tonelli_shanks(a, p, stream, capacity)
        return root is not None, None if root is None else Z(p.bitlen, root)
    return False, None


def sqrt_minus_one(p, stream, capacity):
    while True:
        x = random_interval(Z(1, 0), p, stream.take((p.bitlen + 64 + 7) // 8), capacity).value
        # C feeds the draw to Montgomery exponentiation without nresx, so the base is x / R.
        radix = 1 << (MONTGOMERY_RADIX * ((p.bitlen + MONTGOMERY_RADIX + 1) // MONTGOMERY_RADIX))
        root = pow(x * pow(radix, -1, p.value), p.value >> 2, p.value)
        if (root * root + 1) % p.value == 0:
            return Z(p.bitlen, root)


def sqrt_minus_one_verified(n):
    for base in JACOBI_BASES:
        if base == 2:
            if n.value % 8 == 5:
                break
            continue
        residue = n.value % base
        if residue and pow(residue, (base - 1) // 2, base) == base - 1:
            break
    else:
        return None
    root = pow(base, n.value >> 2, n.value)
    return Z(n.bitlen, root) if (root * root + 1) % n.value == 0 else None


def strong_probable_prime(n, base):
    d, s = n - 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1
    x = pow(base, d, n)
    if x in (1, n - 1):
        return True
    for _ in range(1, s):
        x = x * x % n
        if x == n - 1:
            return True
        if x == 1:
            return False
    return False


def strong_lucas_probable_prime(n):
    if isqrt(n) ** 2 == n:
        return False
    magnitude = 5
    while True:
        if n % magnitude == 0 or jacobi(n % magnitude, magnitude) == 0:
            return False
        if jacobi(n % magnitude, magnitude) == -1:
            break
        magnitude += 2
    discriminant = magnitude if magnitude % 4 == 1 else -magnitude
    q = (1 - discriminant) // 4
    d, s = n + 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1

    def half(x):
        return (x if x % 2 == 0 else x + n) // 2 % n

    u, v, qk = 1, 1, q % n
    for position in range(d.bit_length() - 2, -1, -1):
        u, v, qk = u * v % n, (v * v - 2 * qk) % n, qk * qk % n
        if d >> position & 1:
            u, v = half(u + v), half(discriminant * u + v)
            qk = qk * q % n
    if u == 0:
        return True
    for index in range(s):
        if v == 0:
            return True
        if index + 1 < s:
            v, qk = (v * v - 2 * qk) % n, qk * qk % n
    return False


def probab_prime(n, rounds, stream, capacity):
    value = n.value
    if value < 2:
        return False
    if value in (2, 3):
        return True
    if value % 2 == 0 or any(value % q == 0 and value != q for q in SIEVE_PRIMES):
        return False
    if value <= SIEVE_PRIMES[-1]:
        return True
    if not strong_probable_prime(value, 2) or not strong_lucas_probable_prime(value):
        return False
    argument = Z(value.bit_length() + 1, value) if value.bit_length() + 1 < n.bitlen else n
    upper = add(argument, TWO, capacity, negate=True)
    for _ in range(max(rounds - EXTRA_ROUNDS_AFTER, 0)):
        witness = random_interval(TWO, upper, stream.take((upper.bitlen + 64 + 7) // 8), capacity).value
        if not strong_probable_prime(value, witness):
            return False
    return True


def flag(value):
    return "1" if value else "0"


def is_prime(value):
    return value > 1 and all(strong_probable_prime(value, base) for base in (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31,
                                                                             37, 41)) if value > 41 else value in (
        2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41)


def prime(label, bits, residue):
    candidate = (sample(label, bits - 1) | (1 << (bits - 1)))
    candidate += (residue - candidate) % 4
    while not (all(candidate % q for q in SIEVE_PRIMES) and is_prime(candidate)):
        candidate += 4
    return candidate


def primes_for(level, capacity, full):
    sizes = (3, 5, 64, 65, 127, 255) if full else (64, 129)
    found = []
    for bits in sizes:
        for residue in (1, 3):
            found.append(prime(f"{level}-prime-{bits}-{residue}", bits, residue))
    if full:
        found += [prime(f"{level}-prime-wide-{residue}", 1000, residue) for residue in (1, 3)]
    return found


def records_for(level, capacity, full):
    pool = operands(capacity, level)
    primes = primes_for(level, capacity, full)
    yield from gcd_records(level, capacity, pool, full)
    yield from modular_records(level, capacity, pool, primes, full)
    yield from residue_records(level, capacity, primes, full)
    yield from primality_records(level, capacity, primes, full)


def gcd_records(level, capacity, pool, full):
    pairs = list(picks(pool, f"{level}-gcd", 40 if full else 8))
    fibonacci = [1, 1]
    while fibonacci[-1].bit_length() < min(capacity - 2, 600):
        fibonacci.append(fibonacci[-1] + fibonacci[-2])
    common = sample(f"{level}-common", 100)
    pairs += [(Z(1, 0), Z(1, 0)), (Z(1, 0), Z(65, -(2**64 - 1))), (Z(70, 2**69 - 1), Z(1, 0)),
              (tight(fibonacci[-1]), tight(fibonacci[-2])), (tight(-fibonacci[-2]), tight(fibonacci[-1])),
              (tight(common * sample(f"{level}-ca", 300)), tight(-common * sample(f"{level}-cb", 250))),
              (Z(64, 12345), Z(64, 12345)), (Z(capacity, (1 << (capacity - 1)) - 1), Z(capacity, 3**700)),
              (Z(capacity - 64, (1 << (capacity - 65)) - 1), Z(capacity - 64, -1)),
              (Z(200, 1 << 150), Z(200, 1 << 100))]
    for a, b in pairs:
        yield f"gcd {level} {a.text()} {b.text()} = {gcd(a, b).text()}"
        if max(a.bitlen, b.bitlen) <= capacity - QUOTIENT_HEADROOM:
            yield f"xgcd {level} {a.text()} {b.text()} = {' '.join(z.text() for z in xgcd(a, b))}"
    narrow = [z for z in pool if z.bitlen <= capacity - QUOTIENT_HEADROOM]
    moduli = [z for z in narrow if z.value > 0][::(2 if full else 5)]
    for index, modulus in enumerate(moduli):
        a = narrow[(index * 11 + 5) % len(narrow)]
        exists, inverse = invmod(a, modulus, capacity)
        yield f"invmod {level} {a.text()} {modulus.text()} = {flag(exists)} {inverse.text()}"


def modular_records(level, capacity, pool, primes, full):
    for index, (m1, m2) in enumerate(zip(primes, primes[1:] + primes[:1])):
        if m1 == m2 or 2 * max(m1.bit_length(), m2.bit_length()) + 4 > capacity:
            continue
        a1 = Z(m1.bit_length() + 1, sample(f"{level}-crt1-{index}", m1.bit_length() - 1) * (-1) ** index)
        a2 = Z(m2.bit_length() + 1, sample(f"{level}-crt2-{index}", m2.bit_length() - 1))
        m1z, m2z = tight(m1), tight(m2)
        yield f"crt {level} {a1.text()} {a2.text()} {m1z.text()} {m2z.text()} = " \
              f"{crt(a1, a2, m1z, m2z, capacity).text()}"
    yield f"crt {level} {Z(5, 7).text()} {Z(5, -3).text()} {Z(5, 12).text()} {Z(6, 25).text()} = " \
          f"{crt(Z(5, 7), Z(5, -3), Z(5, 12), Z(6, 25), capacity).text()}"
    exponents = (0, 1, 2, 5, 63, 64, 65, 130, 300) if full else (0, 64, 129)
    for index, exponent in enumerate(exponents):
        entries = [pool[(index * 13 + offset * 7) % len(pool)] for offset in range(4)]
        if index % 3 == 1:
            entries[3] = Z(3, 2)
            entries[0] = Z(3, 3)
            entries[1] = Z(2, 0)
        exists, result = invmat(entries, exponent, capacity)
        yield f"invmat {level} {' '.join(z.text() for z in entries)} {exponent} = " \
              f"{flag(exists)} {' '.join(z.text() for z in result)}"
    roots = [z for z in pool if z.value >= 0] + [tight(r * r) for r in (3, 2**64 - 1, sample(level, 700))]
    roots += [tight(r * r - 1) for r in (2**32, sample(f"{level}-sq", 400))]
    for a in roots[::(1 if full else 4)]:
        yield f"sqrtfloor {level} {a.text()} = {sqrt_floor(a).text()}"
    for index, p in enumerate(primes):
        modulus = tight(p)
        for offset in range(3 if full else 1):
            x = Z(p.bit_length(), sample(f"{level}-px-{index}-{offset}", p.bit_length() - 1) % p)
            e = Z(p.bit_length() + 1, sample(f"{level}-pe-{index}-{offset}", p.bit_length()) % p)
            if offset == 2:
                e = Z(1, 0)
            yield f"powmod {level} {x.text()} {e.text()} {modulus.text()} = " \
                  f"{Z(modulus.bitlen, pow(x.value, e.value, p)).text()}"
    yield f"powmod {level} {Z(1, 0).text()} {Z(2, 1).text()} {Z(4, 7).text()} = {Z(4, 0).text()}"
    near = tight(prime(f"{level}-near", capacity - 66, 3))
    x = Z(near.bitlen, sample(f"{level}-near-x", near.bitlen - 3))
    yield f"powmod {level} {x.text()} {Z(near.bitlen, near.value - 1).text()} {near.text()} = " \
          f"{Z(near.bitlen, pow(x.value, near.value - 1, near.value)).text()}"


def residue_records(level, capacity, primes, full):
    for index, p in enumerate(primes):
        modulus = tight(p)
        candidates = [0, 1, p - 1] + [sample(f"{level}-leg-{index}-{k}", p.bit_length()) % p for k in range(4)]
        for a in candidates[:(None if full else 4)]:
            yield f"legendre {level} {tight(a).text()} {modulus.text()} = {jacobi(a, p)}"
        squares = [0, 1] + [pow(sample(f"{level}-sq-{index}-{k}", p.bit_length()), 2, p) for k in range(2)]
        nonresidues = [a for a in candidates if jacobi(a, p) == -1][:1]
        for k, a in enumerate((squares if full else squares[2:3]) + nonresidues):
            stream = Stream(f"{level}-sqrtp-{index}-{k}")
            found, root = sqrt_mod_p(tight(a), modulus, stream, capacity)
            yield f"sqrtp {level} {tight(a).text()} {modulus.text()} {stream.text()} = " \
                  f"{flag(found)} {root.text() if root else '-'}"
        if p % 4 == 1:
            stream = Stream(f"{level}-sqrtm1-{index}")
            root = sqrt_minus_one(modulus, stream, capacity)
            yield f"sqrtm1 {level} {modulus.text()} {stream.text()} = {root.text()}"
            verified = sqrt_minus_one_verified(modulus)
            yield f"sqrtm1v {level} {modulus.text()} = {flag(verified)} {verified.text() if verified else '-'}"
    for value in ((9, 65, 21, 5 * 2**200 + 1, 13 * 17) if full else (65,)):
        verified = sqrt_minus_one_verified(tight(value))
        yield f"sqrtm1v {level} {tight(value).text()} = {flag(verified)} {verified.text() if verified else '-'}"
    if full:
        stream = Stream(f"{level}-sqrtp-even")
        yield f"sqrtp {level} {Z(2, 1).text()} {Z(3, 2).text()} {stream.text()} = 0 -"


def primality_records(level, capacity, primes, full):
    small = list(range(-3, 40)) + [509, 511, 521, 561, 1031, 2047, 3277, 4033, 4681, 5459, 5777, 8321, 10877,
                                   521 * 521, 1031 * 1031, 1093 * 1093, 3511 * 3511, 2**31 - 1, 2**32 + 15, 2**61 - 1]
    numbers = [(Z(64, value), 1) for value in (small if full else small[::7])]
    numbers += [(tight(p), 1) for p in primes] + [(tight(primes[-1] * primes[-3]), 1)]
    numbers += [(Z(400, primes[-1]), 30), (tight(primes[-2]), 26), (Z(80, 2**61 - 1), 25)]
    if full:
        numbers += [(tight(primes[4]), 64), (tight(2**64 + 13), 40)]
        top = prime(f"{level}-top", capacity - 69, 1)
        numbers += [(tight(top), 1), (tight(top * 3), 1)]
    for index, (n, rounds) in enumerate(numbers):
        stream = Stream(f"{level}-prime-{index}")
        result = probab_prime(n, rounds, stream, capacity)
        yield f"prime {level} {n.text()} {rounds} {stream.text()} = {flag(result)}"


def records():
    for level, capacity in CAPACITIES.items():
        yield from records_for(level, capacity, level == "p324_3")


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_number_theory_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.arithmetic\n\n"
        "internal object IbzNumberTheoryVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = content()
    if arguments.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != expected:
            raise SystemExit("Number theory vectors are stale; run tools/generate_number_theory_vectors.py")
    else:
        OUTPUT.write_text(expected)
    print(f"Verified {expected.count(chr(10) + '        \"')} number theory records")


if __name__ == "__main__":
    main()
