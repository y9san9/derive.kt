package symbolic

public interface SymbolicEvaluator {
    public val module: SymbolicModule

    public fun lift(constant: Any?): SymbolicNode

    /**
     * Resolution of the [SymbolicFlow] is Evaluator's responsibility.
     *
     * Even if function is marked with @Symbolic, it needs to be resolved.
     * Usually these function live in [module] that should be respected.
     */
    public fun call(
        signature: SymbolicSignature,
        arguments: List<SymbolicNode>,
    ): SymbolicNode
}
