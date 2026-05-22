package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName

private val DeriveFqName = FqName("derive.Derive")

public class DeriveIrGenerationExtension : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val deriveFunctions = mutableListOf<IrSimpleFunction>()
        val deriveStubs = mutableMapOf<String, IrSimpleFunction>()
        val overloadStubs = mutableMapOf<String, IrSimpleFunction>()

        for (file in moduleFragment.files) {
            for (decl in file.declarations) {
                if (decl is IrSimpleFunction) {
                    if (decl.isTopLevel && decl.hasAnnotation(DeriveFqName)) {
                        deriveFunctions.add(decl)
                    }
                    val name = decl.name.asString()
                    if (name.endsWith("Derive") &&
                        !decl.hasAnnotation(DeriveFqName)
                    ) {
                        deriveStubs[name] = decl
                    } else if (decl.valueParameters.size == 1 &&
                        !decl.hasAnnotation(DeriveFqName)
                    ) {
                        val paramType = decl.valueParameters[0].type
                        val classId = paramType.classOrNull?.owner?.symbol
                            ?.let { sym ->
                                org.jetbrains.kotlin.name.ClassId(
                                    sym.owner.packageFqName
                                        ?: return@let null,
                                    sym.owner.name,
                                )
                            }
                        if (classId?.asString() == "derive/DeriveDouble") {
                            overloadStubs[name] = decl
                        }
                    }
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
