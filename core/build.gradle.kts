plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
    `maven-publish`
}

kotlin {
    jvm()

    explicitApi()

    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
    }
}

version = libs.versions.derive.get()
group = "me.y9san9.derive"
