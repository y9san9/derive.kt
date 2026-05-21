package symbolic

import kotlin.reflect.KFunction

public interface SymbolicFlow {
    public fun evaluate(
        evaluator: SymbolicEvaluator,
        arguments: List<SymbolicNode>,
    ): SymbolicNode
}

/**
 * Resolves compiler-generated flow for function that is marked with @Symbolic
 */
public inline fun symbolicFlowOf(
    @Symbolic reference: KFunction<*>,
): SymbolicFlow {
    val signature = symbolicSignatureOf(reference)
    return symbolicModule()[signature]
}
