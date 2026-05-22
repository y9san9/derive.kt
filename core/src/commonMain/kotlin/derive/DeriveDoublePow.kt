@file:Suppress("MatchingDeclarationName")

package derive

import derive.internal.pow as doublePow
import kotlin.math.ln as kotlinLn

// pow extension that uses kotlin.math.pow without name collision
public fun DeriveDouble.pow(n: Double): DeriveDouble {
    val v = value
    return DeriveDouble(
        doublePow(v, n),
        derivative * n * doublePow(v, n - 1),
    )
}

public fun DeriveDouble.pow(other: DeriveDouble): DeriveDouble {
    val b = value
    val e = other.value
    return DeriveDouble(
        doublePow(b, e),
        derivative * e * doublePow(b, e - 1) +
            doublePow(b, e) * kotlinLn(b) * other.derivative,
    )
}
