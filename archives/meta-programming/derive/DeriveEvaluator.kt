package derive

import symbolic.*

public open class DeriveEvaluator(
    final override val module: SymbolicModule = symbolicModule(),
) : SymbolicEvaluator {

    final override fun lift(constant: Any?): SymbolicNode {
        require(constant is Double) { "Only double arithmetic is supported" }
        return DeriveNode(constant)
    }

    /**
     * Resolution of the [SymbolicFlow] is Evaluator's responsibility.
     *
     * This only fires for unknown functions, if called function is marked with
     * @Symbolic evaluation will continue recursively without this call.
     */
    final override fun call(
        signature: SymbolicSignature,
        arguments: List<SymbolicNode>,
    ): SymbolicNode {
        return when (signature.name) {
            "kotlin.Double.plus" -> TODO("...")
            "kotlin.Double.times" -> TODO("...")
            else -> error("Unsupported signature")
        }
    }

    public companion object : DeriveEvaluator()
}
