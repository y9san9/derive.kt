package derive.internal

internal actual fun pow(base: Double, exponent: Double): Double =
    java.lang.Math.pow(base, exponent)
