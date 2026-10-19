# SPDX-License-Identifier: Apache-2.0
"""Dimension-two (2,2)-isogeny chains from specification section 4.5.

Independent of the reference strategy: kernel multiples come from affine scalar multiplication,
and every generic step re-doubles the full-order kernel image instead of reusing a strategy tree.
Failure names follow the reference diagnostics so both checks can compare rejection causes.
"""

from generate_curve_vectors import ONE, ZERO
from isogeny_oracle import curve_rhs


class ChainFailure(Exception):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


INFINITY = (ONE, ZERO)
I = (0, 1)


def projective(point):
    return INFINITY if point is None else (point[0], ONE)


def hadamard(o, p):
    x, y, z, t = p
    return (o.add(o.add(x, y), o.add(z, t)), o.add(o.subtract(x, y), o.subtract(z, t)),
            o.subtract(o.add(x, y), o.add(z, t)), o.subtract(o.subtract(x, y), o.subtract(z, t)))


def squares(o, p):
    return tuple(o.multiply(c, c) for c in p)


def products(o, p, q):
    return tuple(o.multiply(a, b) for a, b in zip(p, q, strict=True))


def to_squared(o, p):
    return hadamard(o, squares(o, p))


def invert(o, p):
    x, y, z, t = p
    xy, zt = o.multiply(x, y), o.multiply(z, t)
    return o.multiply(y, zt), o.multiply(x, zt), o.multiply(t, xy), o.multiply(z, xy)


def is_zero(value):
    return value == ZERO


def double(o, point, null):
    """Algorithm 4.111 in whichever coordinates `null` is expressed."""
    squared = squares(o, to_squared(o, point))
    return products(o, hadamard(o, products(o, squared, invert(o, to_squared(o, null)))),
                    invert(o, null))


def lift(o, coefficient, x):
    y = o.reference_root(curve_rhs(o, coefficient, x))
    assert y is not None, "kernel x-coordinate is not on the curve"
    return x, y


def bary(o, p, q, difference):
    """Cross-addition components (u, v, w) of x(P), x(Q), x(P - Q)."""
    (xp, zp), (xq, zq), (xd, zd) = p, q, difference
    first = o.multiply(o.subtract(xp, zp), o.add(xq, zq))
    second = o.multiply(o.add(xp, zp), o.subtract(xq, zq))
    lam, mu = o.add(first, second), o.subtract(first, second)
    a = o.multiply(mu, xd)
    a = o.multiply(a, a)
    b = o.multiply(lam, zd)
    b = o.multiply(b, b)
    w = o.multiply(o.multiply(o.multiply(mu, mu), xd), zd)
    return o.add(a, b), o.subtract(a, b), o.add(w, w)


def symmetric_element(o, first, second):
    """Algorithm 4.115 with the reference precedence: the last matching special case wins."""
    (x1, z1), (x2, z2) = first, second
    case = None
    for index, value in enumerate((o.subtract(x1, z1), o.add(x1, z1), o.subtract(x2, z2), o.add(x2, z2))):
        if is_zero(value):
            case = index
    cross = o.multiply(I, o.multiply(o.add(x1, z1), o.subtract(x2, z2)))
    other = o.multiply(o.add(x2, z2), o.subtract(x1, z1))
    if is_zero(o.add(cross, other)):
        case = 4
    if is_zero(o.subtract(cross, other)):
        case = 5
    a, b = second if case in (0, 1) else first
    aa, bb = o.multiply(a, a), o.multiply(b, b)
    delta, r1 = o.subtract(aa, bb), o.add(aa, bb)
    r2 = o.multiply((2, 0), o.multiply(a, b))
    g1 = (o.negate(r1), r2, o.negate(r2))
    signed = delta if case is None or case % 2 else o.negate(delta)
    g2 = (ZERO, signed, signed)
    if case in (0, 1):
        g1, g2 = g2, g1
    rotate = lambda value, positive: o.multiply(I if positive else o.negate(I), value)  # noqa: E731
    first_flag, second_flag = case in (0, 3, 4), case in (1, 2, 5)
    g3 = (rotate(r2, first_flag), rotate(r1, second_flag), rotate(r1, first_flag))
    if case in (4, 5):
        g2, g3 = g3, g2
    return delta, g1, g2, g3


def gluing_matrix(o, k1, k2):
    """Algorithm 4.116 from the four-torsion couple points K1 = (P1, P2) and K2 = (Q1, Q2)."""
    d1, a1, a2, a3 = symmetric_element(o, k1[0], k2[0])
    d2, b1, b2, b3 = symmetric_element(o, k1[1], k2[1])
    m, s, p = o.multiply, o.subtract, o.add
    d12 = m(d1, d2)
    row0 = [s(p(p(d12, m(a1[0], b1[0])), m(a2[0], b2[0])), m(a3[0], b3[0])),
            s(p(m(a1[0], b1[1]), m(a2[0], b2[1])), m(a3[0], b3[1])),
            s(p(m(a1[1], b1[0]), m(a2[1], b2[0])), m(a3[1], b3[0])),
            s(p(m(a1[1], b1[1]), m(a2[1], b2[1])), m(a3[1], b3[1]))]
    row1 = [p(m(b2[0], row0[0]), m(b2[2], row0[1])), s(m(b2[1], row0[0]), m(b2[0], row0[1])),
            p(m(b2[0], row0[2]), m(b2[2], row0[3])), s(m(b2[1], row0[2]), m(b2[0], row0[3]))]
    row2 = [p(m(a1[0], row0[0]), m(a1[2], row0[2])), p(m(a1[0], row0[1]), m(a1[2], row0[3])),
            s(m(a1[1], row0[0]), m(a1[0], row0[2])), s(m(a1[1], row0[1]), m(a1[0], row0[3]))]
    row3 = [p(m(a1[0], row1[0]), m(a1[2], row1[2])), p(m(a1[0], row1[1]), m(a1[2], row1[3])),
            s(m(a1[1], row1[0]), m(a1[0], row1[2])), s(m(a1[1], row1[1]), m(a1[0], row1[3]))]
    return ([m(c, d12) for c in row0], [m(c, d1) for c in row1], [m(c, d2) for c in row2], row3)


def apply_matrix(o, matrix, point):
    return tuple(
        o.add(o.add(o.multiply(row[0], point[0]), o.multiply(row[1], point[1])),
              o.add(o.multiply(row[2], point[2]), o.multiply(row[3], point[3])))
        for row in matrix)


def couple_theta(o, matrix, first, second):
    (x1, z1), (x2, z2) = first, second
    return apply_matrix(o, matrix, (o.multiply(x1, x2), o.multiply(x1, z2), o.multiply(z1, x2), o.multiply(z1, z2)))


def is_two_torsion(o, coefficient, point):
    x, z = point
    if is_zero(z):
        return False
    return is_zero(x) or is_zero(o.add(o.add(o.multiply(x, x), o.multiply(coefficient, o.multiply(x, z))),
                                       o.multiply(z, z)))


def same_x(o, left, right):
    if is_zero(left[1]) or is_zero(right[1]):
        return is_zero(left[1]) and is_zero(right[1])
    return o.multiply(left[0], right[1]) == o.multiply(left[1], right[0])


class Gluing:
    """Algorithms 4.117-4.119: codomain, cross-addition evaluation, and evaluation of (P, 0) or (0, P)."""

    def __init__(self, o, curves, k1, k2, verify):
        self.o = o
        multiply = lambda c, p, k: projective(o.affine_multiply(c, p, k))  # noqa: E731
        k1_8 = [multiply(c, p, 1) for c, p in zip(curves, k1, strict=True)]
        k2_8 = [multiply(c, p, 1) for c, p in zip(curves, k2, strict=True)]
        if verify:
            twos = [multiply(c, p, 4) for c, p in zip(curves * 2, k1 + k2, strict=True)]
            if not all(is_two_torsion(o, c, p) for c, p in zip(curves * 2, twos, strict=True)) or \
                    same_x(o, twos[0], twos[2]) or same_x(o, twos[1], twos[3]):
                raise ChainFailure("gluing-torsion")
        k1_4 = [multiply(c, p, 2) for c, p in zip(curves, k1, strict=True)]
        k2_4 = [multiply(c, p, 2) for c, p in zip(curves, k2, strict=True)]
        self.matrix = gluing_matrix(o, k1_4, k2_4)
        tt1 = to_squared(o, couple_theta(o, self.matrix, *k1_8))
        tt2 = to_squared(o, couple_theta(o, self.matrix, *k2_8))
        if not (is_zero(tt1[3]) and is_zero(tt2[3])):
            raise ChainFailure("gluing-theta")
        if any(is_zero(c) for c in (tt1[0], tt2[0], tt1[1], tt2[2], tt1[2])):
            raise ChainFailure("gluing-zero")
        dual = (o.multiply(tt1[0], tt2[0]), o.multiply(tt1[1], tt2[0]), o.multiply(tt1[0], tt2[2]), ZERO)
        self.inverse_dual = (o.multiply(tt1[1], tt2[2]), dual[2], dual[1], ZERO)
        k1_image = o.multiply(tt1[2], self.inverse_dual[2]), o.multiply(tt1[0], self.inverse_dual[0])
        k2_image = o.multiply(tt2[1], self.inverse_dual[1]), o.multiply(tt2[0], self.inverse_dual[0])
        self.inverse_images = ((k1_image[0], k1_image[0], k1_image[1], k1_image[1]),
                               (k2_image[0], k2_image[1], k2_image[0], k2_image[1]))
        self.codomain = hadamard(o, dual)

    def evaluate_special(self, couple):
        o = self.o
        squared = to_squared(o, couple_theta(o, self.matrix, *couple))
        if not is_zero(squared[3]):
            raise ChainFailure("gluing-evaluation")
        return hadamard(o, (*products(o, squared[:3], self.inverse_dual[:3]), ZERO))

    def evaluate_cross(self, first, second, inverse_auxiliary, dual):
        o = self.o
        (u1, v1, w1), (u2, v2, w2) = first, second
        sum_term = (o.add(o.multiply(u1, u2), o.multiply(v1, v2)), o.multiply(u1, w2), o.multiply(w1, u2),
                    o.multiply(w1, w2))
        difference = (o.subtract(o.multiply(o.add(u1, v1), o.add(u2, v2)), sum_term[0]), o.multiply(v1, w2),
                      o.multiply(w1, v2), ZERO)
        left = squares(o, apply_matrix(o, self.matrix, sum_term))
        right = squares(o, apply_matrix(o, self.matrix, difference))
        image = products(o, hadamard(o, tuple(o.subtract(a, b) for a, b in zip(left, right, strict=True))),
                         inverse_auxiliary)
        return image if dual else hadamard(o, image)


def generic_step(o, k1, k2, dual_domain, verify):
    """Algorithm 4.112; returns the codomain dual null point and the evaluation multiplier."""
    if dual_domain:
        k1, k2 = hadamard(o, k1), hadamard(o, k2)
    tt1, tt2 = to_squared(o, k1), to_squared(o, k2)
    if verify and any(is_zero(c) for c in (*tt2, tt1[0], tt1[1])):
        raise ChainFailure("generic")
    t1, t2 = o.multiply(tt1[1], tt2[3]), o.multiply(tt1[0], tt2[2])
    multiplier = (o.multiply(t1, tt2[2]), o.multiply(t2, tt2[3]), o.multiply(t1, tt2[0]), o.multiply(t2, tt2[1]))
    if verify and o.multiply(tt1[2], multiplier[2]) != o.multiply(tt1[3], multiplier[3]):
        raise ChainFailure("generic")
    shared = o.multiply(tt2[0], tt2[1])
    dual_null = (o.multiply(shared, tt1[0]), o.multiply(shared, tt1[1]), multiplier[3], multiplier[2])
    return dual_null, multiplier


def generic_evaluate(o, point, multiplier, dual_domain):
    return products(o, to_squared(o, hadamard(o, point) if dual_domain else point), multiplier)


def theta_to_montgomery(o, theta):
    """Algorithm 4.124 with the reference candidate order; returns A, the x-only isomorphism and its index."""
    a, b = theta
    ia, ib = o.multiply(o.negate(I), a), o.multiply(o.negate(I), b)
    candidates = []
    for x, z in ((a, b), (o.add(a, b), o.subtract(a, b)), (o.add(ia, b), o.add(a, ib))):
        x4, z4 = squares(o, squares(o, (x, z)))
        denominator = o.subtract(x4, z4)
        if is_zero(denominator):
            return None
        candidates.append(o.multiply(o.multiply((2, 0), o.add(x4, z4)), o.inverse(denominator)))
    candidates += [o.negate(c) for c in candidates]
    index = 0
    for candidate in range(1, 6):
        if candidates[index][::-1] < candidates[candidate][::-1]:
            index = candidate
    ma, mb = o.negate(a), o.negate(b)
    matrix = (ma if index in (1, 4) else ia if index in (2, 5) else b,
              b if index in (1, 4) else ib if index in (2, 5) else a,
              mb if index in (2, 3, 4) else b,
              ma if index in (0, 1, 5) else a)
    return candidates[index], matrix, index


def apply_isomorphism(o, matrix, point):
    x, z = point
    image = (o.add(o.multiply(x, matrix[0]), o.multiply(z, matrix[1])),
             o.add(o.multiply(x, matrix[2]), o.multiply(z, matrix[3])))
    return None if is_zero(image[1]) else o.multiply(image[0], o.inverse(image[1]))


def split(o, null, points, mode):
    """Algorithms 4.125-4.127 with the reference (x : z) / (x : y) factor order and checks."""
    change = lambda p: (o.add(p[0], p[2]), o.add(p[1], p[3]), o.subtract(p[1], p[3]), o.subtract(p[0], p[2]))  # noqa: E731
    is_product = lambda p: o.multiply(p[0], p[3]) == o.multiply(p[1], p[2])  # noqa: E731
    null = change(null)
    if any(is_zero(c) for c in null):
        raise ChainFailure("splitting-zero")
    if not is_product(null):
        raise ChainFailure("splitting-product")
    images = []
    for point in points:
        point = change(point)
        if not is_product(point):
            raise ChainFailure("point-product")
        images.append(((point[0], point[2]), (point[0], point[1])))
    first, second = (null[0], null[2]), (null[0], null[1])
    if mode == -1:
        x4, z4 = squares(o, squares(o, second))
        if is_zero(second[0]) or is_zero(second[1]) or x4 == z4:
            raise ChainFailure("splitting-invalid")
    curves, mapped = [None, None], [[None, None] for _ in points]
    for side, theta, wanted in ((0, first, (0, 1, -1)), (1, second, (0, 2))):
        if mode not in wanted:
            continue
        model = theta_to_montgomery(o, theta)
        if model is None:
            raise ChainFailure("first-curve" if side == 0 else "second-curve")
        curves[side] = model[0]
        for index, image in enumerate(images):
            # The reference returns (0 : 0) as a successful image; the port rejects it instead.
            if all(is_zero(c) for c in image[side]):
                raise ChainFailure("degenerate-image")
            mapped[index][side] = apply_isomorphism(o, model[1], image[side])
    return curves, mapped


def push(o, length, curves, kernel, points, verify):
    """Algorithm 4.133 before splitting: the final dual null point and the pushed theta points.

    kernel = affine x of (T1.P1, T1.P2, T2.P1, T2.P2) of order 2^(length+2). The reference's
    verification checks only add failures, so a verified push also serves every unverified mode.
    """
    assert length >= 2, "the reference leaves the doubling data unset for single-step chains"
    curves = list(curves)
    lifted = [lift(o, curves[index % 2], x) for index, x in enumerate(kernel)]
    t1, t2 = lifted[:2], lifted[2:]
    scale = 1 << (length - 1)
    k1 = [o.affine_multiply(c, p, scale) for c, p in zip(curves, t1, strict=True)]
    k2 = [o.affine_multiply(c, p, scale) for c, p in zip(curves, t2, strict=True)]
    gluing = Gluing(o, curves, k1, k2, verify)
    images = [gluing.evaluate_special(tuple(projective(None if x is None else (x, None)) for x in point))
              for point in points]
    kernel_images = []
    for t, inverse in zip((t1, t2), gluing.inverse_images, strict=True):
        components = []
        for c, p in zip(curves, t, strict=True):
            components.append(bary(o, projective(o.affine_multiply(c, p, scale)), projective(p),
                                   projective(o.affine_multiply(c, p, scale - 1))))
        kernel_images.append(gluing.evaluate_cross(*components, inverse, False))
    null, dual = gluing.codomain, False
    for step in range(1, length):
        eights = []
        for image in kernel_images:
            for _ in range(length - 1 - step):
                image = double(o, image, null)
            eights.append(image)
        null, multiplier = generic_step(o, *eights, dual, verify and step != length - 1)
        images = [generic_evaluate(o, p, multiplier, dual) for p in images]
        kernel_images = [generic_evaluate(o, p, multiplier, dual) for p in kernel_images]
        dual = True
    return null, images


def chain(o, length, curves, kernel, points, mode):
    return split(o, *push(o, length, curves, kernel, points, mode == -1), mode)
