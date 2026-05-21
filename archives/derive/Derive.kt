package derive

import kotlin.reflect.KFunction
import symbolic.*

public inline fun derive(
    @Symbolic function: KFunction<*>,
    x: Double,
    vararg args: Double,
): Double {
    val x = DeriveNode(x, 1.0)
    val args = listOf(x) + args.map(::DeriveNode)
    val result = symbolicFlowOf(function).evaluate(DeriveEvaluator, args) as DeriveNode
    return result.epsilon
}

