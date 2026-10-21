// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.abs
import ch.trancee.sqisign.arithmetic.compareTo
import ch.trancee.sqisign.arithmetic.divRem
import ch.trancee.sqisign.arithmetic.gcd
import ch.trancee.sqisign.arithmetic.minus
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.unaryMinus

/**
 * The element `(c0 + c1 i + c2 j + c3 ij) / denominator` of the quaternion algebra `B_{p,inf}` of
 * the reference `quat_alg_elem_t`. Operations keep the reference `algebra.c` operation order, so
 * result bit lengths match `ibz_t`. Variable-time.
 */
internal class QuaternionElement(val denominator: Ibz, val coordinates: List<Ibz>) {
    init {
        require(coordinates.size == DIMENSION) { "A quaternion has four coordinates" }
    }

    /** Whether every coordinate is zero, as `quat_alg_elem_is_zero`. */
    val isZero: Boolean
        get() = coordinates.isZeroVector()

    /** `quat_alg_add`: both sides over `lcm`-style common denominator `(da/g)(db/g)g`. */
    operator fun plus(other: QuaternionElement): QuaternionElement {
        val g = denominator.gcd(other.denominator)
        val reduced = denominator.divRem(g).quotient
        val otherReduced = other.denominator.divRem(g).quotient
        val left = coordinates.scaledBy(otherReduced)
        val right = other.coordinates.scaledBy(reduced)
        return QuaternionElement(reduced * otherReduced * g, left.zip(right) { a, b -> a + b })
    }

    /** `quat_alg_conj`. */
    fun conjugate(): QuaternionElement =
        QuaternionElement(denominator, listOf(coordinates[0]) + coordinates.drop(1).map { -it })

    /** `quat_alg_elem_scalar_mul`. */
    fun scaledBy(scalar: Ibz): QuaternionElement =
        QuaternionElement(denominator, coordinates.scaledBy(scalar))

    /** `quat_alg_normalize`: coprime content and denominator, the denominator positive. */
    fun normalized(): QuaternionElement {
        val g = coordinates.content().gcd(denominator)
        val reduced = denominator.divRem(g).quotient
        val sign = Ibz.of(denominator.capacityBits, if (reduced > 0) 1 else -1, 2)
        return QuaternionElement(sign * reduced, coordinates.dividedBy(g).scaledBy(sign))
    }
}

/** A reduced norm `numerator / denominator` in lowest terms with both parts non-negative. */
internal class QuaternionNorm(val numerator: Ibz, val denominator: Ibz)

/** The algebra `B_{p,inf}` with `i^2 = -1` and `j^2 = -p`, as the reference `quat_alg_t`. */
internal class QuaternionAlgebra(private val p: Ibz) {
    /** `quat_alg_mul`. */
    fun multiply(a: QuaternionElement, b: QuaternionElement): QuaternionElement =
        QuaternionElement(a.denominator * b.denominator, product(a.coordinates, b.coordinates))

    /** `quat_alg_norm`: `(c0^2 + c1^2 + p (c2^2 + c3^2)) / d^2` in lowest terms. */
    fun norm(a: QuaternionElement): QuaternionNorm {
        val c = a.coordinates
        val total = (c[IJ] * c[IJ] + c[J] * c[J]) * p + c[REAL] * c[REAL] + c[I] * c[I]
        val square = a.denominator * a.denominator
        val g = total.gcd(square)
        return QuaternionNorm(
            total.divRem(g).quotient.abs(),
            square.divRem(g).quotient.abs(),
        )
    }

    /** `quat_alg_coord_mul`. */
    private fun product(a: List<Ibz>, b: List<Ibz>): List<Ibz> {
        val zero = Ibz.zero(p.capacityBits)
        fun Ibz.add(x: Ibz, y: Ibz) = this + x * y
        fun Ibz.sub(x: Ibz, y: Ibz) = this - x * y
        val real =
            (zero.sub(a[J], b[J]).sub(a[IJ], b[IJ]) * p).add(a[REAL], b[REAL]).sub(a[I], b[I])
        val i = (zero.add(a[J], b[IJ]).sub(a[IJ], b[J]) * p).add(a[REAL], b[I]).add(a[I], b[REAL])
        val j = zero.add(a[REAL], b[J]).add(a[J], b[REAL]).sub(a[I], b[IJ]).add(a[IJ], b[I])
        val ij = zero.add(a[REAL], b[IJ]).add(a[IJ], b[REAL]).sub(a[J], b[I]).add(a[I], b[J])
        return listOf(real, i, j, ij)
    }

    private companion object {
        const val REAL = 0
        const val I = 1
        const val J = 2
        const val IJ = 3
    }
}
