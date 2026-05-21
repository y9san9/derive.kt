package symbolic

import kotlin.reflect.KFunction

public data class SymbolicSignature(
    val name: String,
    val parameters: List<SymbolicType>,
)

/**
 * Resolves compiler-generated signature for function that is marked as @Symbolic
 */
public inline fun symbolicSignatureOf(
    @Symbolic reference: KFunction<*>,
): SymbolicSignature {
    error("This is implemented as compiler plugin intrinsic and has no implementation")
}
