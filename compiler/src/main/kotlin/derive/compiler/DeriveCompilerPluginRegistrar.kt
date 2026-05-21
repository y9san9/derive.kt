package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

public class DeriveCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val pluginId: String = "derive.compiler"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration,
    ) {
        IrGenerationExtension.registerExtension(
            DeriveIrGenerationExtension(),
        )
    }
}
