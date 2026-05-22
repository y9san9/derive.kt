package derive.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class DeriveFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::DeriveFirDeclarationGenerator
    }
}
