import symbolic.*
import derive.*

@Symbolic
private fun f(x: Double): Double {
    return x * x + 5.0 * x + 5.0
}

private object `f$symbolic` {
    val signature = SymbolicSignature(
        name = "f",
        parameters = listOf(SymbolicType("kotlin.Double")),
    )

    object Flow : SymbolicFlow {
        override fun evaluate(
            evaluator: SymbolicEvaluator,
            arguments: List<SymbolicNode>,
        ): SymbolicNode {
            val x = arguments[0]
            return evaluator.call(
                signature = SymbolicSignature(
                    name = "kotlin.Double.plus",
                    parameters = listOf(
                        SymbolicType("kotlin.Double"),
                        SymbolicType("kotlin.Double"),
                    ),
                ),
                arguments = listOf(
                    evaluator.call(
                        signature = SymbolicSignature(
                            name = "kotlin.Double.plus",
                            parameters = listOf(
                                SymbolicType("kotlin.Double"),
                                SymbolicType("kotlin.Double"),
                            ),
                        ),
                        arguments = listOf(
                            evaluator.call(
                                signature = SymbolicSignature(
                                    name = "kotlin.Double.times",
                                    parameters = listOf(
                                        SymbolicType("kotlin.Double"),
                                        SymbolicType("kotlin.Double"),
                                    ),
                                ),
                                arguments = listOf(x, x),
                            ),
                            evaluator.call(
                                signature = SymbolicSignature(
                                    name = "kotlin.Double.times",
                                    parameters = listOf(
                                        SymbolicType("kotlin.Double"),
                                        SymbolicType("kotlin.Double"),
                                    ),
                                ),
                                arguments = listOf(evaluator.lift(5), x),
                            )
                        )
                    ),
                    evaluator.lift(5),
                )
            )
        }
    }
}

public fun main() {
    print(derive(::f, 10.0))
}
