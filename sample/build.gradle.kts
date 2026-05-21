plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.derive)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm()

    explicitApi()

    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
    }

    dependencies {
        implementation(libs.derive.core)
    }
}
