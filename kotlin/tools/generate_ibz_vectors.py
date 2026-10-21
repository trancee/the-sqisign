#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the bounded ibz_t integer semantics used by the quaternion layer.

An ibz_t value is the two's-complement value of its first ceil(bitlen / 64) words. Every operation
below is stated on that (bitlen, value) pair, never on C limb loops; pinned C checks the model.
"""

import argparse
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/arithmetic/IbzVectors.kt"
CAPACITIES = {"p324_3": 1920, "p500_27": 2560, "p664_17": 3520}
MASK64 = (1 << 64) - 1


# Set when a checked operation wraps at capacity: C keeps the wrapped value, Kotlin rejects it.
OVERFLOW = [False]


def limbs(bitlen):
    return (bitlen + 63) // 64


def wrap(value, bitlen):
    width = 64 * limbs(bitlen)
    value %= 1 << width
    return value - (1 << width) if value >> (width - 1) else value


def sign_extend(value, bits):
    value %= 1 << bits
    return value - (1 << bits) if value >> (bits - 1) else value


class Z:
    __slots__ = ("bitlen", "value")

    def __init__(self, bitlen, value):
        self.bitlen = bitlen
        self.value = wrap(value, bitlen)

    def text(self):
        return f"{self.bitlen}:{'-' if self.value < 0 else ''}{abs(self.value):x}"


def ibz_set(x, bitlen):
    bits = max(bitlen, 1)
    assert sign_extend(x, bits) != -(1 << (bits - 1))
    return Z(bits, sign_extend(x, bits))


def set_bound(z, bitlen):
    if bitlen >= z.bitlen:
        return Z(bitlen, z.value)
    bits = max(bitlen, 1)
    return Z(bits, sign_extend(z.value, bits))


def add(a, b, capacity, checked=True, negate=False):
    exact = a.value - b.value if negate else a.value + b.value
    result = Z(min(max(a.bitlen, b.bitlen) + 1, capacity), exact)
    OVERFLOW[0] |= checked and result.value != exact
    return result


def mul(a, b, capacity):
    requested = a.bitlen + b.bitlen - 1
    magnitude = abs(a.value) * abs(b.value)
    negative = (a.value < 0) != (b.value < 0)
    truncated = magnitude % (1 << capacity)
    result = Z(min(requested, capacity), -truncated if negative else truncated)
    OVERFLOW[0] |= magnitude.bit_length() > capacity or (
        requested > capacity and magnitude.bit_length() == capacity)
    return result


def divide(a, b):
    quotient, remainder = abs(a.value) // abs(b.value), abs(a.value) % abs(b.value)
    if (a.value < 0) != (b.value < 0):
        quotient = -quotient
    if a.value < 0:
        remainder = -remainder
    return Z(a.bitlen, quotient), Z(b.bitlen, remainder)


def mod(a, b, capacity):
    remainder = divide(a, b)[1]
    if remainder.value < 0:
        remainder = add(remainder, b, capacity, negate=b.value < 0)
    return remainder


def shift_right(a, exponent):
    if exponent == 0:
        return a
    words = (a.value % (1 << (64 * limbs(a.bitlen)))) >> exponent
    return Z(a.bitlen - exponent if exponent < a.bitlen else 1, words)


def shift_left(a, exponent, capacity):
    if exponent == 0:
        return a
    requested = a.bitlen + exponent
    result = Z(min(requested, capacity), a.value << exponent)
    OVERFLOW[0] |= requested > capacity and result.value != a.value << exponent
    return result


def mod2exp(a, exponent):
    return Z(exponent + 1, a.value % (1 << exponent))


def two_adic(a):
    value = a.value % (1 << (64 * limbs(a.bitlen)))
    return (value & -value).bit_length() - 1 if value else 0


def power(a, exponent, capacity):
    result, base = Z(2, 1), a
    while exponent:
        if exponent & 1:
            result = mul(result, base, capacity)
        exponent >>= 1
        if exponent:
            base = mul(base, base, capacity)
    return result


def size_in_base(a, base):
    bits = abs(a.value).bit_length()
    if base < 2 or bits == 0:
        return 0
    k = base.bit_length() - 1
    return (bits + k - 1) // k + (0 if base & (base - 1) == 0 else 1)


def mul_by_int(a, factor, bound):
    width = 64 * limbs(bound)
    product = (abs(a.value) % (1 << width)) * abs(factor) % (1 << width)
    negative = (a.value < 0) != (factor < 0)
    return set_bound(Z(width, -product if negative else product), bound)


def extract(a, offset):
    unsigned = (abs(a.value) >> offset) & MASK64
    signed = (unsigned & ((1 << 63) - 1)) * (-1 if a.value < 0 else 1)
    return unsigned, signed


def to_text(a, base):
    digits = format(abs(a.value), "x" if base == 16 else "d")
    return ("-" if a.value < 0 else "") + digits


def parse(text, base, capacity):
    body = text[1:] if text[:1] in "+-" else text
    allowed = "0123456789abcdefABCDEF"[:22 if base == 16 else 10]
    if not body or any(character not in allowed for character in body):
        return None
    value = int(body, base)
    bitlen = value.bit_length() + 1
    if bitlen > capacity:
        return None
    return Z(bitlen, -value if text[0] == "-" else value)


def random_interval(a, b, data, capacity):
    span = add(add(b, a, capacity, negate=True), Z(2, 1), capacity)
    bits = b.bitlen + 64
    sample = Z(bits + 1, int.from_bytes(data[:(bits + 7) // 8], "little") % (1 << bits))
    reduced = mod(add(sample, span, capacity), span, capacity)
    return set_bound(add(a, reduced, capacity), b.bitlen)


def random_symmetric(bound, data, capacity):
    doubled = add(ibz_set(bound, 32), ibz_set(bound, 32), capacity)
    sample = random_interval(Z(1, 0), doubled, data, capacity)
    return set_bound(add(sample, ibz_set(bound, 32), capacity, negate=True), 32)


def random_bytes(label, count):
    return hashlib.shake_256(label.encode()).digest(count)


def sample(label, bits):
    width = (bits + 7) // 8
    return int.from_bytes(random_bytes(label, width), "little") % (1 << bits) if bits else 0


def operands(capacity, label):
    result = []
    for bitlen in (1, 2, 3, 32, 33, 63, 64, 65, 129, 250, capacity - 1, capacity):
        top = 1 << (bitlen - 1)
        values = {0, -1, -top, top - 1} | ({1} if bitlen > 1 else set())
        for index in range(2):
            magnitude = sample(f"{label}-{bitlen}-{index}", bitlen - 1)
            values |= {magnitude if index % 2 else -magnitude}
        result += [Z(bitlen, value) for value in sorted(values)]
    result += [Z(bitlen, 1 << (bitlen - 1)) for bitlen in (33, 65, 130)]
    return result


def picks(pool, label, count):
    for index in range(count):
        digest = random_bytes(f"{label}-{index}", 4)
        yield pool[int.from_bytes(digest[:2], "little") % len(pool)], \
            pool[int.from_bytes(digest[2:], "little") % len(pool)]


def checked(function, *arguments):
    OVERFLOW[0] = False
    text = function(*arguments).text()
    return "! " + text if OVERFLOW[0] else text


def unary_records(level, capacity, index, a):
    exponent = (0, 1, 7, 63, 64, 65, 200)[index % 7]
    bound = (1, 2, 32, 64, 65, 129, capacity)[index % 7]
    offset = (0, 1, 63, 64, 100, 190)[index % 6]
    base = (2, 3, 8, 10, 16, 1)[index % 6]
    factor = (0, 1, -1, 3, -12345, 2**31 - 1, -(2**31))[index % 7]
    small_int = (0, 5, -5, 2**31 - 1, -(2**31) + 1)[index % 5]
    comparand = (0, 1, -1, 2**31 - 1, -(2**31) + 1, 77)[index % 6]
    divisor = (1, 2, 3, 10, 2**63 + 5, MASK64)[index % 6]
    value = a.value % (1 << (64 * limbs(a.bitlen)))
    unsigned, signed = extract(a, offset)
    words = [(a.value >> (64 * i)) & MASK64 for i in range(limbs(a.value.bit_length()))]
    hexadecimal = "".join(f"{w:016x}" for w in reversed(words)) or "-"
    text = a.text()
    return [
        f"neg {level} {text} = {Z(a.bitlen, -a.value).text()}",
        f"abs {level} {text} = {Z(a.bitlen, abs(a.value)).text()}",
        f"shr {level} {text} {exponent} = {shift_right(a, exponent).text()}",
        f"shl {level} {text} {exponent} = {checked(shift_left, a, exponent, capacity)}",
        f"mod2exp {level} {text} {exponent} = {mod2exp(a, exponent).text()}",
        f"bound {level} {text} {bound} = {set_bound(a, bound).text()}",
        f"info {level} {text} = {abs(a.value).bit_length()} {two_adic(a)} {int(a.value == 0)} "
        f"{int(a.value == 1)} {int(a.value >= 0)} {int(a.value % 2 == 0)} {sign_extend(value, 32)}",
        f"extract {level} {text} {offset} = {unsigned:x} {signed}",
        f"size {level} {text} {base} = {size_in_base(a, base)}",
        f"str {level} {text} {(10, 16)[index % 2]} = {to_text(a, (10, 16)[index % 2])}",
        f"mulint {level} {text} {factor} {bound} = {mul_by_int(a, factor, bound).text()}",
        f"addint {level} {text} {small_int} {bound} = "
        f"{set_bound(add(a, ibz_set(small_int, 32), capacity, checked=False), bound).text()}",
        f"cmpi {level} {text} {comparand} = {(a.value > comparand) - (a.value < comparand)}",
        f"modui {level} {text} {divisor} = {a.value % divisor}",
    ] + ([f"digits {level} {text} = {len(words)} {hexadecimal}"] if a.value >= 0 else [])


def records_for(level, capacity, full):
    pool = operands(capacity, level)
    small = [z for z in pool if z.bitlen <= 250]
    large = [z for z in pool if z.bitlen >= capacity - 1]
    if not full:
        pool = large + [Z(2, 1), Z(2, -1), Z(65, 2**64 - 1), Z(129, -(2**127))]
    pairs = list(picks(pool, f"{level}-pairs", 36 if full else 6)) + list(picks(large, f"{level}-large", 4))
    pairs += [(Z(capacity, (1 << (capacity - 1)) - 1), Z(2, 1)),
              (Z(capacity, -(1 << (capacity - 1))), Z(2, -1)),
              (Z(capacity - 1, (1 << (capacity - 2)) - 1), Z(capacity - 1, (1 << (capacity - 2)) - 1)),
              (Z(capacity // 2 + 1, (1 << (capacity // 2)) - 1), Z(capacity // 2 + 1, -(1 << (capacity // 2))))]
    for a, b in pairs:
        yield f"add {level} {a.text()} {b.text()} = {checked(add, a, b, capacity)}"
        yield f"sub {level} {a.text()} {b.text()} = {checked(lambda x, y: add(x, y, capacity, negate=True), a, b)}"
        yield f"mul {level} {a.text()} {b.text()} = {checked(mul, a, b, capacity)}"
        yield f"cmp {level} {a.text()} {b.text()} = {(a.value > b.value) - (a.value < b.value)}"
        if b.value:
            quotient, remainder = divide(a, b)
            yield f"div {level} {a.text()} {b.text()} = {quotient.text()} {remainder.text()}"
            yield f"mod {level} {a.text()} {b.text()} = {mod(a, b, capacity).text()}"
            yield f"divides {level} {a.text()} {b.text()} = {int(a.value % abs(b.value) == 0)}"
        if a.value and a.bitlen + b.bitlen - 1 <= capacity:
            product = mul(a, b, capacity)
            yield f"divides {level} {product.text()} {a.text()} = {int(product.value % abs(a.value) == 0)}"
    for index, a in enumerate(pool):
        unary = unary_records(level, capacity, index, a)
        yield from (unary[(index + step * 5) % len(unary)] for step in range(3 if full else 2))
        other = pool[(index * 7 + 3) % len(pool)]
        bound = (1, 2, 32, 64, 65, 129, capacity)[index % 7]
        if index % 3 == 0:
            yield (f"addb {level} {a.text()} {other.text()} {bound} = "
                   f"{set_bound(add(a, other, capacity, checked=False), bound).text()}")
    yield f"pow {level} {Z(3, 2).text()} {capacity - 2} = {checked(power, Z(3, 2), capacity - 2, capacity)}"
    yield f"pow {level} {Z(3, 2).text()} {capacity} = {checked(power, Z(3, 2), capacity, capacity)}"
    top = "-7" + "f" * (capacity // 4 - 1)
    yield f"parse {level} {top} 16 = {parse(top, 16, capacity).text()}"
    top_bits = sample(f"{level}-bits", capacity - 1)
    yield f"bits {level} {capacity - 1} {top_bits:x} = {Z(capacity, top_bits).text()}"
    if not full:
        return
    yield from (f"set {level} {x} {bits} = {ibz_set(x, bits).text()}"
                for x, bits in ((0, 0), (2, 0), (0, 1), (5, 3), (-3, 3), (12, 4), (2**31 - 1, 32),
                                (-(2**31) + 1, 32), (-7, 31), (300, 9), (-300, 9), (7, 32)))
    for a in small[::4]:
        for exponent in (0, 1, 2, 5)[::2 if a.bitlen > 33 else 1]:
            yield f"pow {level} {a.text()} {exponent} = {checked(power, a, exponent, capacity)}"
    for text, base in (("0", 10), ("-0", 16), ("+12345678901234567890", 10), ("-deadBEEF", 16),
                       ("1" * 40, 16), ("", 10), ("-", 10), ("+", 16), ("12a", 10), ("g", 16)):
        parsed = parse(text, base, capacity)
        yield f"parse {level} {text or '_'} {base} = {parsed.text() if parsed else 'fail'}"
    for bits, value in ((0, 0), (1, 1), (63, 2**63 - 1), (64, MASK64), (65, 2**65 - 1), (130, 2**130 - 1)):
        yield f"bits {level} {bits} {value:x} = {Z(bits + 1, value % (1 << bits)).text()}"
    intervals = [(Z(1, 0), Z(2, 1)), (Z(1, 0), Z(65, 2**64 - 1)), (Z(33, -1000), Z(33, 1000)),
                 (Z(200, -(2**198)), Z(200, 2**199 - 1)), (Z(64, 5), Z(64, 5))]
    for index, (low, high) in enumerate(intervals):
        data = random_bytes(f"{level}-rand-{index}", (high.bitlen + 64 + 7) // 8)
        yield f"rand {level} {low.text()} {high.text()} {data.hex()} = {random_interval(low, high, data, capacity).text()}"
    for index, bound in enumerate((1, 2, 1000, 2**31 - 1)):
        data = random_bytes(f"{level}-randm-{index}", 13)
        yield f"randm {level} {bound} {data.hex()} = {random_symmetric(bound, data, capacity).text()}"


def records():
    for level, capacity in CAPACITIES.items():
        yield from records_for(level, capacity, level == "p324_3")


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_ibz_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.arithmetic\n\n"
        "internal object IbzVectors {\n"
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
            raise SystemExit("Ibz vectors are stale; run tools/generate_ibz_vectors.py")
    else:
        OUTPUT.write_text(expected)
    print(f"Verified {sum(1 for _ in records())} ibz records")


if __name__ == "__main__":
    main()
