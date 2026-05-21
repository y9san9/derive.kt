package symbolic

/**
 * SymbolicModule contains custom bindings to signatures and is used to change
 * how functions behave without reflection.
 */
public interface SymbolicModule {
    public operator fun contains(signature: SymbolicSignature): Boolean
    public operator fun get(signature: SymbolicSignature): SymbolicFlow
}

/**
 * Resolves all annotated with @Symbolic functions within current classpath into
 * this module
 */
public inline fun symbolicModule(): SymbolicModule {
    error("This is implemented as compiler plugin intrinsic and has no implementation")
}
