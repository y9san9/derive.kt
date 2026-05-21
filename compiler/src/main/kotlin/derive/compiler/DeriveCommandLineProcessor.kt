package derive.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

public class DeriveCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = "derive.compiler"

    override val pluginOptions: Collection<AbstractCliOption> = emptyList()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        error("Unexpected option ${option.optionName}")
    }
}
