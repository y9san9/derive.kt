plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    explicitApi()

    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
    }
}

gradlePlugin {
    plugins {
        create("derive") {
            id = "me.y9san9.derive"
            version = libs.versions.derive.get()
            implementationClass = "derive.plugin.DeriveSubplugin"
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin.api)
}
