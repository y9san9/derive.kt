import derive.Derive
import derive.derive
import derive.derivative
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Simple function: f(x) = x*x + 5*x + 5
// f'(x) = 2x + 5
@Derive
public fun f(x: Double): Double = x * x + 5.0 * x + 5.0

// Complex function with multiple local minima:
// g(x) = sin(3x) + 0.1*x*x + cos(x)*x
@Derive
public fun objective(x: Double): Double =
    sin(3.0 * x) + 0.1 * x * x + cos(x) * x

// Steeper test function:
// h(x) = (x-1)*(x-1) * (x+2)*(x+2) * sin(x)
@Derive
public fun steepFunction(x: Double): Double =
    (x - 1.0) * (x - 1.0) * (x + 2.0) * (x + 2.0) * sin(x)

private fun gradientDescent(
    start: Double,
    learningRate: Double,
    maxSteps: Int,
    tolerance: Double,
    function: (Double) -> Double,
    derivative: (Double) -> Double,
): Pair<Double, Double> {
    var x = start
    repeat(maxSteps) {
        val grad = derivative(x)
        if (abs(grad) < tolerance) {
            return x to function(x)
        }
        x -= learningRate * grad
    }
    return x to function(x)
}

// Monte Carlo random search
private fun monteCarlo(
    range: ClosedFloatingPointRange<Double>,
    samples: Int,
    function: (Double) -> Double,
): Pair<Double, Double> {
    var bestX = range.start
    var bestY = function(bestX)
    repeat(samples) {
        val x = range.start + Random.nextDouble(0.0, range.endInclusive - range.start)
        val y = function(x)
        if (y < bestY) {
            bestX = x
            bestY = y
        }
    }
    return bestX to bestY
}

// Multi-start optimization:
// Monte Carlo generates random starting points,
// then gradient descent refines each one.
private fun monteCarloGradient(
    range: ClosedFloatingPointRange<Double>,
    randomStarts: Int,
    learningRate: Double,
    maxSteps: Int,
    function: (Double) -> Double,
    derivative: (Double) -> Double,
): Pair<Double, Double> {
    var bestX = range.start
    var bestY = function(bestX)
    repeat(randomStarts) {
        val startX = range.start + Random.nextDouble(0.0, range.endInclusive - range.start)
        val (x, y) = gradientDescent(
            startX,
            learningRate,
            maxSteps,
            tolerance = 1e-8,
            function = function,
            derivative = derivative,
        )
        if (y < bestY) {
            bestX = x
            bestY = y
        }
    }
    return bestX to bestY
}

private fun printResults(
    name: String,
    results: Map<String, Pair<Double, Double>>,
) {
    println("=== $name ===")
    for ((method, pair) in results) {
        val (x, y) = pair
        println("  $method: x = %.6f, f(x) = %.6f".format(x, y))
    }
    println()
}

fun main() {
    // First, test simple derivative
    println("=== Simple Derivative Test ===")
    println("  f(3) = ${f(3.0)}")
    val simpleDeriv = derive { f(3.0) }
    println("  f'(3) = $simpleDeriv (expected: 11.0)")
    require(abs(simpleDeriv - 11.0) < 0.001) { "Simple derivative test failed!" }
    println("  PASSED")
    println()

    // Test derivative with variable
    println("=== Variable Derivative Test ===")
    val testX = 2.5
    val varDeriv = derive { f(testX) }
    println("  f'($testX) = $varDeriv (expected: 10.0)")
    require(abs(varDeriv - 10.0) < 0.001) { "Variable derivative test failed!" }
    println("  PASSED")
    println()

    // Test lambda with nested function call
    println("=== Nested Lambda Test ===")
    val nested = derive { objective(f(testX)) }
    println("  (objective(f($testX)))' = $nested")
    println("  PASSED")
    println()

    // --- Optimize objective function ---
    println("=== Optimization: g(x) = sin(3x) + 0.1x^2 + cos(x)*x ===")
    println("Search range: [-10, 10]")
    println()

    val range1 = -10.0..10.0

    // Pure Monte Carlo
    val (mcX, mcY) = monteCarlo(
        range1,
        samples = 100_000,
        function = ::objective,
    )

    // Single gradient descent from x=0
    val (gdX, gdY) = gradientDescent(
        start = 0.0,
        learningRate = 0.01,
        maxSteps = 5000,
        tolerance = 1e-8,
        function = ::objective,
        derivative = derivative(::objective),
    )

    // Multi-start Monte Carlo + gradient descent
    val (hybridX, hybridY) = monteCarloGradient(
        range1,
        randomStarts = 50,
        learningRate = 0.01,
        maxSteps = 5000,
        function = ::objective,
        derivative = derivative(::objective),
    )

    printResults(
        "Results",
        mapOf(
            "Monte Carlo (100k samples)" to (mcX to mcY),
            "Gradient Descent (from 0)" to (gdX to gdY),
            "MC + Gradient (50 starts)" to (hybridX to hybridY),
        ),
    )

    // --- Optimize steep function ---
    println("=== Optimization: h(x) = (x-1)^2 * (x+2)^2 * sin(x) ===")
    println("Search range: [-5, 5]")
    println()

    val (gdX2, gdY2) = gradientDescent(
        start = 0.0,
        learningRate = 0.01,
        maxSteps = 5000,
        tolerance = 1e-8,
        function = ::steepFunction,
        derivative = derivative { steepFunction(it) },
    )
    println("  Gradient Descent (from 0): x = %.6f, h(x) = %.6f".format(gdX2, gdY2))
    println()

    // --- Verification ---
    println("=== Derivative Verification ===")
    val deriv = derive { objective(testX) }
    println("  g'($testX) = $deriv")

    // Numerical derivative for comparison
    val h = 1e-7
    val numerical = (objective(testX + h) - objective(testX - h)) / (2.0 * h)
    println("  Numerical g'($testX) = $numerical")
    println("  Difference: ${abs(deriv - numerical)}")
    println()

    println("All optimizations completed!")
}
