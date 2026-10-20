// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.fixedPointReciprocal
import ch.trancee.sqisign.arithmetic.highProduct
import ch.trancee.sqisign.arithmetic.parse
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Variable-time fixed-point kernels of the lattice reduction at each level's `FP_P_BITS` precision
 * over deterministic public operands of exactly that many bits.
 */
@State(Scope.Benchmark)
public open class IbzFixedPointBenchmark {
    /** Selects the `ibz_t` capacity and `FP_P_BITS` precision of NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    internal lateinit var left: Ibz
    internal lateinit var right: Ibz
    internal var precision: Int = 0

    /** Parses operands outside the measured operations. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        precision = fixedPointPrecision(parameterSet)
        left = parse(capacity, ibzBenchmarkOperand(precision, negative = false))
        right = parse(capacity, ibzBenchmarkOperand(precision, negative = true))
    }

    /** Scaled product `floor(left * right / 2^precision)` at `precision + 4` bits. */
    @Benchmark
    public open fun highProduct(): Any =
        left.highProduct(right, precision, precision + RECIPROCAL_GUARD_BITS)

    /** Newton reciprocal of a full-precision value with the reference round count. */
    @Benchmark
    public open fun reciprocal(): Any = left.fixedPointReciprocal(precision, NEWTON_ROUNDS)
}

internal const val NEWTON_ROUNDS = 5
internal const val RECIPROCAL_GUARD_BITS = 4
private const val HEX_RADIX = 16
private const val NIST_I_PRECISION = 640
private const val NIST_III_PRECISION = 832
private const val NIST_V_PRECISION = 1152

/** The reference `FP_P_BITS` of each level's lattice reduction. */
internal fun fixedPointPrecision(parameterSet: String): Int =
    when (parameterSet) {
        "NIST_I" -> NIST_I_PRECISION
        "NIST_III" -> NIST_III_PRECISION
        "NIST_V" -> NIST_V_PRECISION
        else -> throw IllegalArgumentException("Unknown parameter set: $parameterSet")
    }

private fun parse(capacity: Int, text: String): Ibz =
    requireNotNull(Ibz.parse(capacity, text, HEX_RADIX))
