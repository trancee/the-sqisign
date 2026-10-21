// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

private const val GLUING_HALVING_THRESHOLD = 16

/**
 * Algorithm 4.133: the `(2^length, 2^length)`-isogeny chain `E1 x E2 -> E3 x E4` with kernel
 * `[4]<(T1.P1, T1.P2), (T2.P1, T2.P2)>`, evaluated at [points]. It uses the reference's halving
 * strategy and its checks for [mode]; operations are variable-time.
 *
 * The kernel points must have order `2^(length + 2)` for success. Kernel points must be finite and
 * not `(0 : 1)`, and every pushed couple point must have a component at infinity.
 */
internal fun <Element : QuadraticFieldElement<Element>> thetaChain(
    length: Int,
    curves: CoupleCurve<Element>,
    kernel: ThetaChainKernel<Element>,
    points: List<CouplePoint<Element>>,
    mode: ThetaChainMode,
): ThetaChainOutcome<Element> {
    val field = curves.first.field
    val maximum = field.parameters.torsionExponent - 2
    require(length in 2..maximum) { "Theta chain length must be in 2..$maximum" }
    val generators = with(kernel) { listOf(t1.first, t1.second, t2.first, t2.second) }
    require(generators.none { it.x.multiply(it.z).isEqualTo(field.zero()) }) {
        "Theta chain kernel points must be finite and not (0:1)"
    }
    require(points.all { it.first.isInfinity() or it.second.isInfinity() }) {
        "Pushed couple points must have a component at infinity"
    }
    return ThetaChainOutcome.of {
        ThetaChainComputation(field, length, listOf(curves.first, curves.second), generators, mode)
            .run(points)
    }
}

/** The kernel generators `T1` and `T2` of a theta chain on `E1 x E2`. */
internal class ThetaChainKernel<Element : QuadraticFieldElement<Element>>(
    internal val t1: CouplePoint<Element>,
    internal val t2: CouplePoint<Element>,
)

/** The pair `([2^k]P, [2^k - 1]P)` of one kernel point `P`. */
private class Rung<Element : QuadraticFieldElement<Element>>(
    val even: MontgomeryPoint<Element>,
    val odd: MontgomeryPoint<Element>,
)

private class ThetaChainComputation<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    private val length: Int,
    private val curves: List<MontgomeryCurve<Element>>,
    kernel: List<MontgomeryPoint<Element>>,
    private val mode: ThetaChainMode,
) {
    private val verify = mode == ThetaChainMode.VERIFY
    private val space = 1 + Int.SIZE_BITS - (length - 1).countLeadingZeroBits()
    private val todo = IntArray(space)
    private val bases = kernel.map {
        MontgomeryPoint.fromCoordinates(field, it.x.multiply(it.z.inverse()), field.one())
    }
    private val a24 = curves.map {
        val two = field.one().add(field.one())
        it.montgomeryCoefficient().add(two).multiply(two.add(two).inverse())
    }
    private val ladders = bases.map { mutableListOf(Rung(it, MontgomeryPoint.infinity(field))) }
    private val firstKernel = mutableListOf<ThetaPoint<Element>>()
    private val secondKernel = mutableListOf<ThetaPoint<Element>>()

    fun run(points: List<CouplePoint<Element>>): ThetaChainCodomain<Element> {
        todo[0] = length
        var current = 0
        while (todo[current] != 1) {
            current++
            val previous = todo[current - 1]
            val doublings = if (previous >= GLUING_HALVING_THRESHOLD) previous / 2 else previous - 1
            for ((index, ladder) in ladders.withIndex()) {
                ladder += climb(index, ladder.last(), doublings)
            }
            todo[current] = previous - doublings
        }
        val (k1, k2) =
            ladders
                .map { it[current].even }
                .chunked(2) { (left, right) -> CouplePoint(left, right) }
        val gluing = ThetaGluing.compute(curves, k1, k2, verify)
        var images = points.map(gluing::evaluateSpecial)
        pushThroughGluing(gluing, current)
        current--
        var structure = ThetaStructure.fromNull(gluing.codomain)
        lateinit var dualNull: ThetaPoint<Element>
        var step = 1
        while (current >= 0) {
            while (todo[current] != 1) {
                current++
                val doublings = todo[current - 1] / 2
                firstKernel[current] = structure.double(firstKernel[current - 1], doublings)
                secondKernel[current] = structure.double(secondKernel[current - 1], doublings)
                todo[current] = todo[current - 1] - doublings
            }
            val isogeny =
                ThetaIsogeny.compute(
                    field,
                    firstKernel[current],
                    secondKernel[current],
                    step != 1,
                    verify and (step != length - 1),
                )
            images = images.map(isogeny::evaluate)
            for (level in 0 until current) {
                firstKernel[level] = isogeny.evaluate(firstKernel[level])
                secondKernel[level] = isogeny.evaluate(secondKernel[level])
                todo[level]--
            }
            current--
            structure = isogeny.codomain
            dualNull = isogeny.dualNull
            step++
        }
        return splitThetaProduct(field, dualNull, images, mode)
    }

    /** Kernel images through the gluing at every level below [top], as in `gluing_start_chain`. */
    private fun pushThroughGluing(gluing: ThetaGluing<Element>, top: Int) {
        val firstTop = push(gluing, 0, top, gluing.inverseImages[0], true)
        val secondTop = push(gluing, 1, top, gluing.inverseImages[1], true)
        val firstInverse = firstTop.inverse()
        val secondInverse = secondTop.inverse()
        repeat(space) {
            firstKernel += firstTop.hadamard()
            secondKernel += secondTop.hadamard()
        }
        todo[0]--
        for (level in 1 until top) {
            firstKernel[level] = push(gluing, 0, level, firstInverse, false)
            secondKernel[level] = push(gluing, 1, level, secondInverse, false)
            todo[level]--
        }
    }

    /** The gluing image of the [couple]-th kernel generator's multiple at [level]. */
    private fun push(
        gluing: ThetaGluing<Element>,
        couple: Int,
        level: Int,
        inverse: ThetaPoint<Element>,
        dual: Boolean,
    ): ThetaPoint<Element> =
        gluing.evaluateBary(bary(2 * couple, level), bary(2 * couple + 1, level), inverse, dual)

    private fun bary(index: Int, level: Int): BaryCoordinates<Element> =
        BaryCoordinates.of(ladders[index][level].even, bases[index], ladders[index][level].odd)

    /**
     * `gluing_ladder`: [times] combined doublings and differential additions with difference `P`.
     */
    private fun climb(index: Int, rung: Rung<Element>, times: Int): Rung<Element> {
        val coefficient = a24[index % 2]
        val difference = bases[index].x
        var even = rung.even
        var odd = rung.odd
        repeat(times) {
            val sum = even.x.add(even.z)
            val minus = even.x.subtract(even.z)
            val sumSquare = sum.square()
            val minusSquare = minus.square()
            val cross = sum.multiply(odd.x.subtract(odd.z))
            val other = minus.multiply(odd.x.add(odd.z))
            val delta = sumSquare.subtract(minusSquare)
            even =
                MontgomeryPoint.fromCoordinates(
                    field,
                    sumSquare.multiply(minusSquare),
                    minusSquare.add(coefficient.multiply(delta)).multiply(delta),
                )
            odd =
                MontgomeryPoint.fromCoordinates(
                    field,
                    cross.add(other).square(),
                    cross.subtract(other).square().multiply(difference),
                )
        }
        return Rung(even, odd)
    }
}
