# Derive.kt

> differentiation as a compile-time transformation of ordinary Kotlin code.

**Derive.kt** is an experimental Kotlin compiler plugin and runtime that brings
*differentiable programming* to ordinary Kotlin code.

The library allows users to compute derivatives of regular Kotlin functions
using compile-time code generation, without exposing dual numbers, tapes, or
any differentiation-specific types in user code.

This project is designed as a **research prototype**.

## Motivation

Many modern systems rely on gradients:
- optimization
- machine learning
- physics simulation
- numerical methods

However, differentiation logic is often:
- intrusive (special types leak into user code),
- backend-specific (CPU vs GPU),
- hard to extend to custom numeric types.

## Core Idea

Users write normal Kotlin functions:

```kotlin
@Derive
fun loss(x: Double, y: Double): Double {
    return (x * x + 2.0 * y - 10.0) * (x * x + 2.0 * y - 10.0)
}
```

Derivatives are computed by selecting a **derivation strategy**:

```kotlin
val dx = derive(ForwardScalar) { loss(1.0, 1.0) }
```

The compiler plugin:
1. Analyzes the function body
2. Rewrites all operations into derivative-aware code
3. Selects implementations based on the chosen strategy
4. Emits optimized Kotlin bytecode

No special numeric types are visible to the user.

## Key Concepts

TODO()

## Non-goals

This project intentionally does **not** aim to:
- be a full ML framework,
- replace existing AD systems (JAX, PyTorch),
- provide a stable public API,
- support all Kotlin language features.

The focus is on **compiler architecture and extensibility**.

## Project Status

- Research prototype
- Under active development
- APIs and semantics are subject to change

## License

MIT

## Acknowledgements

This project is part of a scientific research work on
**Differentiable Programming** at **Moscow Institute of Physics and Technology (MIPT)**.

Inspired by:
- Jetpack Compose
- kotlinx.serialization
- Enzyme (LLVM)
