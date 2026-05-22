package derive

import kotlin.math.cos as kotlinCos
import kotlin.math.exp as kotlinExp
import kotlin.math.ln as kotlinLn
import kotlin.math.sin as kotlinSin
import kotlin.math.sqrt as kotlinSqrt

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

public fun DeriveDouble.sin(): DeriveDouble = DeriveDouble(
    kotlinSin(value),
    derivative * kotlinCos(value),
)

public fun DeriveDouble.cos(): DeriveDouble = DeriveDouble(
    kotlinCos(value),
    -derivative * kotlinSin(value),
)

public fun DeriveDouble.exp(): DeriveDouble = DeriveDouble(
    kotlinExp(value),
    derivative * kotlinExp(value),
)

public fun DeriveDouble.ln(): DeriveDouble = DeriveDouble(
    kotlinLn(value),
    derivative / value,
)

public fun DeriveDouble.sqrt(): DeriveDouble = DeriveDouble(
    kotlinSqrt(value),
    derivative / (2.0 * kotlinSqrt(value)),
)

public fun derive(block: () -> Double): Double = block()

public fun derivative(block: (Double) -> Double): (Double) -> Double = block
