# SPDX-License-Identifier: Apache-2.0
"""Independent affine model changes and full Velu sums, not the x-only theta formulas."""

from math import isqrt

from generate_curve_vectors import ONE, ZERO


def curve_rhs(oracle, coefficient, x):
    return oracle.multiply(x, oracle.add(oracle.add(
        oracle.multiply(x, x), oracle.multiply(coefficient, x)), ONE))


def is_exact_order_four(oracle, coefficient, point):
    if point is None:
        return False
    twice = oracle.affine_add(coefficient, point, point)
    return twice is not None and oracle.affine_add(coefficient, twice, twice) is None


def normalization_choices(oracle, coefficient, four):
    """Six origin-preserving maps x -> (x - r) / s between isomorphic Montgomery models."""
    assert is_exact_order_four(oracle, coefficient, four)
    assert oracle.affine_add(coefficient, four, four)[0] != ZERO
    t = four[0]
    t_square = oracle.multiply(t, t)
    inverse_twice_t = oracle.inverse(oracle.multiply((2, 0), t))
    u = oracle.multiply(oracle.add(t_square, ONE), inverse_twice_t)
    inverse_u = oracle.inverse(u)
    assert coefficient == oracle.negate(oracle.add(u, inverse_u))
    first_scale = oracle.multiply(oracle.subtract(t_square, ONE), inverse_twice_t)
    second_scale = oracle.multiply((0, 1), oracle.multiply(first_scale, inverse_u))
    choices = []
    for shift, scale in ((ZERO, ONE), (u, first_scale), (inverse_u, second_scale)):
        for signed_scale in (scale, oracle.negate(scale)):
            target = oracle.multiply(
                oracle.add(coefficient, oracle.multiply((3, 0), shift)),
                oracle.inverse(signed_scale))
            choices.append((target, shift, signed_scale))
    return choices


def select_normalization(oracle, coefficient, four):
    """Definition 4.2.1 maximum: imaginary component first, then real component."""
    return max(normalization_choices(oracle, coefficient, four), key=lambda choice: choice[0][::-1])


def velu_four(oracle, coefficient, kernel, points):
    """Full affine Velu sums followed by the ordinary Montgomery model change."""
    assert is_exact_order_four(oracle, coefficient, kernel)
    twice = oracle.affine_add(coefficient, kernel, kernel)
    assert twice[0] != ZERO
    subgroup = [kernel, twice, (kernel[0], oracle.negate(kernel[1]))]
    t = kernel[0]
    t_square = oracle.multiply(t, t)
    alpha = oracle.multiply(oracle.multiply(t, oracle.add(t_square, ONE)), oracle.inverse((2, 0)))
    beta = oracle.multiply(
        oracle.subtract(oracle.subtract(
            oracle.multiply((5, 0), oracle.multiply(t_square, t_square)),
            oracle.multiply((2, 0), t_square)), (3, 0)),
        oracle.inverse((4, 0)))
    alpha_root = oracle.reference_root(alpha)
    assert alpha_root is not None
    target = oracle.subtract(oracle.multiply(alpha, coefficient), oracle.multiply((3, 0), beta))
    images = []
    for point in points:
        if point is None or any(point[0] == member[0] for member in subgroup):
            images.append(None)
            continue
        x, y = point
        for member in subgroup:
            translated = oracle.affine_add(coefficient, point, member)
            assert translated is not None
            x = oracle.add(x, oracle.subtract(translated[0], member[0]))
            y = oracle.add(y, oracle.subtract(translated[1], member[1]))
        image = (oracle.add(oracle.multiply(alpha, x), beta),
                 oracle.multiply(oracle.multiply(alpha, alpha_root), y))
        assert oracle.multiply(image[1], image[1]) == curve_rhs(oracle, target, image[0])
        images.append(image)
    return target, images


def two_isogeny_chain(oracle, coefficient, seed, exponent):
    """Uniform square-root checkpoints, unlike the production halving strategy."""
    current = coefficient
    spacing = isqrt(exponent) + 1
    checkpoints = {0: seed}
    for doublings in range(spacing, exponent + 2, spacing):
        checkpoints[doublings] = oracle.affine_multiply(
            current, checkpoints[doublings - spacing], 1 << spacing)
    remaining = exponent + 2
    for _ in range(exponent // 2):
        target = remaining - 2
        nearest = max(doublings for doublings in checkpoints if doublings <= target)
        kernel = oracle.affine_multiply(current, checkpoints[nearest], 1 << (target - nearest))
        kept = sorted(doublings for doublings in checkpoints if doublings < target)
        current, images = velu_four(oracle, current, kernel, [checkpoints[k] for k in kept])
        checkpoints = dict(zip(kept, images, strict=True))
        remaining -= 2
    assert remaining == 2 and oracle.affine_multiply(current, checkpoints[0], 4) is None
    return select_normalization(oracle, current, checkpoints[0])[0]
