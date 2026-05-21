package derive

import symbolic.*

public data class DeriveNode(
    val real: Double,
    val epsilon: Double = 0.0,
) : SymbolicNode
