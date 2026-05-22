package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val DeriveFqName = FqName("derive.Derive")

public class DeriveIrGenerationExtension : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val deriveFunctions = mutableListOf<IrSimpleFunction>()
        val deriveStubs = mutableMapOf<String, IrSimpleFunction>()
        val overloadStubs = mutableMapOf<String, IrSimpleFunction>()

        val deriveDoubleClassId = ClassId(
            FqName("derive"),
            Name.identifier("DeriveDouble"),
        )

        val allFunctions = moduleFragment.files
            .flatMap { it.declarations.filterIsInstance<IrSimpleFunction>() }

        for (function in allFunctions) {
            if (function.isTopLevel && function.hasAnnotation(DeriveFqName)) {
                deriveFunctions.add(function)
            }

            val name = function.name.asString()
            val isDeriveAnnotated = function.hasAnnotation(DeriveFqName)
            if (name.endsWith("Derive") && !isDeriveAnnotated) {
                deriveStubs[name] = function
            } else if (function.valueParameters.size == 1) {
                val paramClassId = function.valueParameters[0].type
                    .classOrNull
                    ?.owner
                    ?.let { owner ->
                        ClassId(
                            owner.packageFqName ?: return@let null,
                            owner.name,
                        )
                    }
                if (paramClassId == deriveDoubleClassId) {
                    overloadStubs[name] = function
                }
            }
        }

        val transformer = DeriveIrTransformer(
            pluginContext,
            deriveFunctions,
            deriveStubs,
            overloadStubs,
        )
        moduleFragment.transform(transformer, null)
    }
}
