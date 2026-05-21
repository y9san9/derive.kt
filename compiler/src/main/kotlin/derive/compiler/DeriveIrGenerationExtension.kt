package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

public class DeriveIrGenerationExtension : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        moduleFragment.transform(DeriveIrTransformer(pluginContext), null)
        // val redactedTransformer =
        //     RedactedIrVisitor(
        //         pluginContext,
        //         redactedAnnotations,
        //         unRedactedAnnotations,
        //         replacementString,
        //     )
        // moduleFragment.transform(redactedTransformer, null)
    }
}
