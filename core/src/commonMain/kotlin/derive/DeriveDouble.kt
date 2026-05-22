package derive

import kotlin.math.cos as kotlinCos
import kotlin.math.exp as kotlinExp
import kotlin.math.ln as kotlinLn
import kotlin.math.sin as kotlinSin
import kotlin.math.sqrt as kotlinSqrt

/**
 * A dual-number type for forward-mode automatic differentiation.
 *
 * Holds both a function value and its derivative at a given point.
 * Arithmetic operations and elementary functions propagate derivatives
 * according to the chain rule.
 */
public data class DeriveDouble(val value: Double, val derivative: Double) {

    public operator fun plus(other: DeriveDouble): DeriveDouble =
        DeriveDouble(value + other.value, derivative + other.derivative)

    public operator fun minus(other: DeriveDouble): DeriveDouble =
        DeriveDouble(value - other.value, derivative - other.derivative)

    public operator fun times(other: DeriveDouble): DeriveDouble = DeriveDouble(
        value * other.value,
        derivative * other.value + value * other.derivative,
    )

    public operator fun div(other: DeriveDouble): DeriveDouble = DeriveDouble(
        value / other.value,
        (derivative * other.value - value * other.derivative) /
            (other.value * other.value),
    )

    public operator fun unaryMinus(): DeriveDouble =
        DeriveDouble(-value, -derivative)
}

/** sin(d) with derivative cos(d.value) * d.derivative */
public fun DeriveDouble.sin(): DeriveDouble = DeriveDouble(
    kotlinSin(value),
    derivative * kotlinCos(value),
)

/** cos(d) with derivative -sin(d.value) * d.derivative */
public fun DeriveDouble.cos(): DeriveDouble = DeriveDouble(
    kotlinCos(value),
    -derivative * kotlinSin(value),
)

/** exp(d) with derivative exp(d.value) * d.derivative */
public fun DeriveDouble.exp(): DeriveDouble = DeriveDouble(
    kotlinExp(value),
    derivative * kotlinExp(value),
)

/**
 * ln(d) with derivative d.derivative / d.value.
 * Produces NaN/Infinity when [value] is zero or negative.
 */
public fun DeriveDouble.ln(): DeriveDouble = DeriveDouble(
    kotlinLn(value),
    derivative / value,
)

/**
 * sqrt(d) with derivative d.derivative / (2 * sqrt(d.value)).
 * Produces NaN when [value] is negative, Infinity when [value] is zero.
 */
public fun DeriveDouble.sqrt(): DeriveDouble = DeriveDouble(
    kotlinSqrt(value),
    derivative / (2.0 * kotlinSqrt(value)),
)

/**
 * Evaluates [block] using dual-number arithmetic and returns the derivative.
 *
 * The first [Double] literal or variable encountered in [block] is treated as
 * the independent variable (seeded with derivative = 1.0). All other constants
 * are seeded with derivative = 0.0.
 *
 * Example:
 * ```kotlin
 * val derivativeAt3 = derive { f(3.0) }
 * ```
 */
public fun derive(block: () -> Double): Double = block()

/**
 * Returns a function that computes the derivative of [block].
 *
 * Accepts either a lambda or a function reference:
 * ```kotlin
 * val deriv1 = derivative { f(it) }
 * val deriv2 = derivative(::f)
 * ```
 */
public fun derivative(block: (Double) -> Double): (Double) -> Double = block
