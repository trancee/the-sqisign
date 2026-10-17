// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.ec.MontgomeryCurveP324
import ch.trancee.sqisign.ec.MontgomeryPointP324
import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp324
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

private const val DOUBLED_POINT_NUMERATOR = 9
private const val DOUBLED_POINT_DENOMINATOR = 40

/** Variable-time x-only operations on synthetic public points of A=0. */
@State(Scope.Benchmark)
public open class MontgomeryP324Benchmark {
    /** The same public point with a short or dense full-width scalar. */
    @Param("SHORT", "FULL_WIDTH") public var scalarShape: String = "SHORT"

    private lateinit var curve: MontgomeryCurveP324
    private lateinit var point: MontgomeryPointP324
    private lateinit var doubled: MontgomeryPointP324
    private lateinit var scalar: UInt328

    /** Constructs the curve and projective inputs outside the timed operations. */
    @Setup
    public fun prepare() {
        curve = MontgomeryCurveP324.fromCoefficients(field(0), field(1))
        point = MontgomeryPointP324.fromCoordinates(field(2), field(1))
        doubled =
            MontgomeryPointP324.fromCoordinates(
                field(DOUBLED_POINT_NUMERATOR),
                field(DOUBLED_POINT_DENOMINATOR),
            )
        val bytes =
            when (scalarShape) {
                "SHORT" -> ByteArray(41).also { it[0] = 17 }
                "FULL_WIDTH" -> ByteArray(41) { 0xff.toByte() }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public curve scalar shape: $scalarShape",
                    )
            }
        scalar = UInt328.fromLittleEndian(bytes)
    }

    /** Includes inversion and canonical field result allocation. */
    @Benchmark public open fun coefficient(): Any = curve.montgomeryCoefficient()

    /** Retains both immutable projective coordinates without affine normalization. */
    @Benchmark public open fun doublePoint(): Any = curve.double(point)

    /** Adds P and [2]P with the known difference -P; includes result allocation. */
    @Benchmark
    public open fun differentialAdd(): Any =
        MontgomeryPointP324.differentialAdd(point, doubled, point)

    /** Includes scalar export, the complete ladder, and all intermediate/result allocations. */
    @Benchmark public open fun multiply(): Any = curve.multiply(point, scalar)

    private fun field(value: Int): Fp2P324 =
        Fp2P324.fromComponents(Fp324.fromInt(value), Fp324.zero())
}
