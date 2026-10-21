// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.divRem
import ch.trancee.sqisign.arithmetic.gcd
import ch.trancee.sqisign.arithmetic.minus

/*
 * The 4-vectors of the reference `dim4.c` that the lattice, ideal and signing layers call, as
 * `List<Ibz>`. Every operation keeps the reference operation order, so result bit lengths match
 * `ibz_t`. Variable-time.
 */

/** `ibz_vec_4_content`: the non-negative gcd of the entries, zero for the zero vector. */
internal fun List<Ibz>.content(): Ibz = drop(2).fold(this[0].gcd(this[1])) { g, x -> x.gcd(g) }

/** `ibz_vec_4_scalar_mul`. */
internal fun List<Ibz>.scaledBy(scalar: Ibz): List<Ibz> = map { it * scalar }

/** `ibz_vec_4_scalar_div`: quotients rounded toward zero; callers divide exactly. */
internal fun List<Ibz>.dividedBy(scalar: Ibz): List<Ibz> = map { it.divRem(scalar).quotient }

/** `ibz_vec_4_sub`. */
internal fun List<Ibz>.vectorMinus(other: List<Ibz>): List<Ibz> = zip(other) { a, b -> a - b }

/** `ibz_vec_4_is_zero`. */
internal fun List<Ibz>.isZeroVector(): Boolean = all { it.isZero }
