plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `maven-publish`
}

kotlin {
    explicitApi()

    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
        optIn.add(
            "org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
        )
        optIn.add(
            "org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi",
        )
        optIn.add(
            "org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI",
        )
    }
}

group = "me.y9san9.derive"
version = libs.versions.derive.get()

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler)
}
