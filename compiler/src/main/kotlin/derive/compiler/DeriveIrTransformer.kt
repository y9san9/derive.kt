package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val DeriveFqName = FqName("derive.Derive")
private val DeriveNumberFqName = FqName("derive.DeriveNumber")

public class DeriveIrTransformer(
    private val context: IrPluginContext,
) : IrTransformer<Nothing?>() {

    private val deriveNumberType: IrType by lazy {
        context.referenceClass(DeriveNumberFqName)?.defaultType
            ?: error("Could not resolve $DeriveNumberFqName – is the derive runtime on the classpath?")
    }

    override fun visitFunction(
        declaration: IrFunction,
        data: Nothing?,
    ): IrStatement {
        if (declaration !is IrSimpleFunction) return declaration
        if (!declaration.hasAnnotation(DeriveFqName)) return declaration

        val derived = buildDerivedFunction(declaration)

        return IrCompositeImpl(
            startOffset = declaration.startOffset,
            endOffset   = declaration.endOffset,
            type        = context.irBuiltIns.unitType,
            origin      = null,
            statements  = listOf(declaration, derived),
        )
    }

    private fun buildDerivedFunction(original: IrSimpleFunction): IrSimpleFunction {
        val derived = context.irFactory.buildFun {
            name       = Name.identifier("f\$derive")
            origin     = original.origin
            modality   = original.modality
            visibility = original.visibility
            isInline   = original.isInline
            isSuspend  = original.isSuspend
            returnType = original.returnType.remapDouble()
        }

        derived.parent = original.parent

        derived.dispatchReceiverParameter =
            original.dispatchReceiverParameter?.copyTo(derived)
        derived.extensionReceiverParameter =
            original.extensionReceiverParameter?.copyTo(
                derived,
                type = original.extensionReceiverParameter!!.type.remapDouble(),
            )

        derived.valueParameters = original.valueParameters.map { param ->
            param.copyTo(derived, type = param.type.remapDouble())
        }

        // Deep-copy body, then remap all Double types/expressions inside
        derived.body = original.body
            ?.deepCopyWithSymbols(derived)
            ?.also { it.transform(DoubleRemapper(), null) }

        return derived
    }

    private fun IrType.remapDouble(): IrType =
        if (this == context.irBuiltIns.doubleType) deriveNumberType else this

    /**
     * Walks the copied body and rewrites every node that mentions Double:
     *  - IrConst<Double>  → re-stamped with DeriveNumber type
     *  - IrGetValue       → type updated if the referenced symbol is Double
     *  - IrCall           → return type + type arguments updated
     */
    private inner class DoubleRemapper : IrElementTransformerVoid() {

        override fun visitConst(expression: IrConst<*>): IrExpression {
            if (expression.type != context.irBuiltIns.doubleType) return expression
            return IrConstImpl(
                startOffset = expression.startOffset,
                endOffset   = expression.endOffset,
                type        = deriveNumberType,
                kind        = expression.kind,
                value       = expression.value,
            )
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            val remapped = super.visitGetValue(expression) as IrGetValue
            if (remapped.type == context.irBuiltIns.doubleType) {
                remapped.type = deriveNumberType
            }
            return remapped
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val remapped = super.visitCall(expression) as IrCall
            if (remapped.type == context.irBuiltIns.doubleType) {
                remapped.type = deriveNumberType
            }
            // Remap any Double type arguments (e.g. generic calls)
            for (i in 0 until remapped.typeArgumentsCount) {
                if (remapped.getTypeArgument(i) == context.irBuiltIns.doubleType) {
                    remapped.putTypeArgument(i, deriveNumberType)
                }
            }
            return remapped
        }
    }
}
