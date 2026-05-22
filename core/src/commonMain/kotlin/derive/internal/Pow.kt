package derive.internal

// Helper to call pow without interference from DeriveDouble.pow extensions
internal expect fun pow(base: Double, exponent: Double): Double
