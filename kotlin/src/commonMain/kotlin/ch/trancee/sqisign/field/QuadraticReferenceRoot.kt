// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

/** Shared checked boundary for curve algorithms that require a reference-sign root. */
internal fun <Element : QuadraticFieldElement<Element>> QuadraticField<Element>
    .requireReferenceRoot(
    value: Element,
    failureReason: String,
): Element = requireNotNull(squareRootWithReferenceSign(value)) { failureReason }

/** Algorithm 4.35 candidates; the caller must verify the square before returning a root. */
internal fun quadraticReferenceRootComponents(
    real: ByteArray,
    imaginary: ByteArray,
    modulus: FieldModulus,
): Pair<ByteArray, ByteArray> {
    val one = ByteArray(modulus.size).also { it[0] = 1 }
    val exponent = subtractFieldBytes(modulus.squareRootExponent, one)
    val norm =
        addFieldModulo(
            multiplyFieldModulo(real, real, modulus),
            multiplyFieldModulo(imaginary, imaginary, modulus),
            modulus,
        )
    val delta =
        if (isZeroFieldBytes(imaginary)) real
        else multiplyFieldModulo(norm, exponentiateField(norm, exponent, modulus), modulus)
    val sum = addFieldModulo(real, delta, modulus)
    val twiceSum = addFieldModulo(sum, sum, modulus)
    val power = exponentiateField(twiceSum, exponent, modulus)
    val realCandidate = multiplyFieldModulo(sum, power, modulus)
    val imaginaryCandidate = multiplyFieldModulo(imaginary, power, modulus)
    val twiceReal = addFieldModulo(realCandidate, realCandidate, modulus)
    return if (
        compareFieldBytes(multiplyFieldModulo(twiceReal, twiceReal, modulus), twiceSum) == 0
    ) {
        realCandidate to imaginaryCandidate
    } else {
        imaginaryCandidate to negateFieldModulo(realCandidate, modulus)
    }
}
