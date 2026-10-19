// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticFieldElement

/** An x-only point `(P1, P2)` on a product `E1 x E2` of Montgomery curves. */
internal class CouplePoint<Element : QuadraticFieldElement<Element>>(
    internal val first: MontgomeryPoint<Element>,
    internal val second: MontgomeryPoint<Element>,
)

/** A product `E1 x E2` of Montgomery curves. */
internal class CoupleCurve<Element : QuadraticFieldElement<Element>>(
    internal val first: MontgomeryCurve<Element>,
    internal val second: MontgomeryCurve<Element>,
)
