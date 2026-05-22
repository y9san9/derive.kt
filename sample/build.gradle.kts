plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.derive)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm()

    compilerOptions {
        extraWarnings = true
        progressiveMode = true
    }

    dependencies {
        implementation(libs.derive.core)
    }
}
