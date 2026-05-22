package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val DeriveFqName = FqName("derive.Derive")
private val DeriveDoubleClassId =
    ClassId(FqName("derive"), Name.identifier("DeriveDouble"))
private val DeriveCallableId = CallableId(
    FqName("derive"),
    Name.identifier("derive"),
)
private val DerivativeCallableId = CallableId(
    FqName("derive"),
    Name.identifier("derivative"),
)

@Suppress("DEPRECATION")
@OptIn(ExperimentalCompilerApi::class)
public class DeriveIrTransformer(
    private val context: IrPluginContext,
    private val deriveFunctions: List<IrSimpleFunction>,
    private val deriveStubs: Map<String, IrSimpleFunction>,
    private val overloadStubs: Map<String, IrSimpleFunction>,
) : IrElementTransformerVoid() {

    private val doubleType get() = context.irBuiltIns.doubleType

    private val deriveDoubleType: IrType
    private val deriveDoubleConstructor: IrConstructorSymbol
    private val plusOpSymbol: IrSimpleFunctionSymbol
    private val minusOpSymbol: IrSimpleFunctionSymbol
    private val timesOpSymbol: IrSimpleFunctionSymbol
    private val divOpSymbol: IrSimpleFunctionSymbol
    private val unaryMinusOpSymbol: IrSimpleFunctionSymbol
    private val sinSymbol: IrSimpleFunctionSymbol
    private val cosSymbol: IrSimpleFunctionSymbol
    private val expSymbol: IrSimpleFunctionSymbol
    private val lnSymbol: IrSimpleFunctionSymbol
    private val sqrtSymbol: IrSimpleFunctionSymbol
    private val kotlinMathSin: IrSimpleFunctionSymbol
    private val kotlinMathCos: IrSimpleFunctionSymbol
    private val kotlinMathExp: IrSimpleFunctionSymbol
    private val kotlinMathLn: IrSimpleFunctionSymbol
    private val kotlinMathSqrt: IrSimpleFunctionSymbol
    private val mathToDerive:
        Map<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol>
    private val doubleOpToDerive: Map<String, IrSimpleFunctionSymbol>
    private val deriveDoubleToDerive =
        mutableMapOf<String, IrSimpleFunctionSymbol>()
    private val derivativeGetter: IrSimpleFunctionSymbol

    init {
        val deriveClass = context.referenceClass(DeriveDoubleClassId)
            ?: error("Could not resolve $DeriveDoubleClassId")

        deriveDoubleType = deriveClass.defaultType
        deriveDoubleConstructor = deriveClass.constructors
            .single { it.owner.valueParameters.size == 2 }

        plusOpSymbol = deriveClass.findMember("plus")!!
        minusOpSymbol = deriveClass.findMember("minus")!!
        timesOpSymbol = deriveClass.findMember("times")!!
        divOpSymbol = deriveClass.findMember("div")!!
        unaryMinusOpSymbol = deriveClass.findMember("unaryMinus")!!

        sinSymbol = context.referenceFunctions(
            CallableId(FqName("derive"), Name.identifier("sin")),
        ).single { it.isExtensionOnDeriveDouble() }
        cosSymbol = context.referenceFunctions(
            CallableId(FqName("derive"), Name.identifier("cos")),
        ).single { it.isExtensionOnDeriveDouble() }
        expSymbol = context.referenceFunctions(
            CallableId(FqName("derive"), Name.identifier("exp")),
        ).single { it.isExtensionOnDeriveDouble() }
        lnSymbol = context.referenceFunctions(
            CallableId(FqName("derive"), Name.identifier("ln")),
        ).single { it.isExtensionOnDeriveDouble() }
        sqrtSymbol = context.referenceFunctions(
            CallableId(FqName("derive"), Name.identifier("sqrt")),
        ).single { it.isExtensionOnDeriveDouble() }

        kotlinMathSin = context.referenceFunctions(
            CallableId(FqName("kotlin.math"), Name.identifier("sin")),
        ).single { it.isSingleDoubleParam() }
        kotlinMathCos = context.referenceFunctions(
            CallableId(FqName("kotlin.math"), Name.identifier("cos")),
        ).single { it.isSingleDoubleParam() }
        kotlinMathExp = context.referenceFunctions(
            CallableId(FqName("kotlin.math"), Name.identifier("exp")),
        ).single { it.isSingleDoubleParam() }
        kotlinMathLn = context.referenceFunctions(
            CallableId(FqName("kotlin.math"), Name.identifier("ln")),
        ).single { it.isSingleDoubleParam() }
        kotlinMathSqrt = context.referenceFunctions(
            CallableId(FqName("kotlin.math"), Name.identifier("sqrt")),
        ).single { it.isSingleDoubleParam() }

        mathToDerive = mapOf(
            kotlinMathSin to sinSymbol,
            kotlinMathCos to cosSymbol,
            kotlinMathExp to expSymbol,
            kotlinMathLn to lnSymbol,
            kotlinMathSqrt to sqrtSymbol,
        )

        doubleOpToDerive = mapOf(
            "plus" to plusOpSymbol,
            "minus" to minusOpSymbol,
            "times" to timesOpSymbol,
            "div" to divOpSymbol,
            "unaryMinus" to unaryMinusOpSymbol,
        )

        relocateAndBuildDerivedFunctions(deriveClass)
        populateOverloadStubs()

        val derivativeProp = deriveClass.owner.declarations
            .filterIsInstance<IrProperty>()
            .find { it.name.asString() == "derivative" }
        derivativeGetter = derivativeProp?.getter?.symbol
            ?: error("DeriveDouble.derivative property not found")
    }

    private fun relocateAndBuildDerivedFunctions(deriveClass: IrClassSymbol) {
        for (original in deriveFunctions) {
            val deriveName = "${original.name.asString()}Derive"
            deriveStubs[deriveName]?.let { stub ->
                stub.parentAsContainer()?.declarations?.remove(stub)
            }
            val derived = buildDerivedFunction(original, deriveName)
            populateDerivedFunctionBody(original, derived)
            deriveDoubleToDerive[original.name.asString()] = derived.symbol
        }
    }

    private fun populateOverloadStubs() {
        for ((originalName, overloadStub) in overloadStubs) {
            val deriveSymbol = deriveDoubleToDerive[originalName] ?: continue
            val original = deriveFunctions.find {
                it.name.asString() == originalName
            } ?: continue

            overloadStub.parentAsContainer()?.declarations?.remove(overloadStub)
            original.parentAsContainer()?.declarations?.add(overloadStub)
            populateOverloadBody(overloadStub, deriveSymbol)
        }
    }

    private fun IrDeclaration.parentAsContainer(): IrDeclarationContainer? =
        parent as? IrDeclarationContainer

    private fun IrClassSymbol.findMember(name: String): IrSimpleFunctionSymbol? =
        owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .find { it.name.asString() == name }
            ?.symbol

    private fun IrSimpleFunctionSymbol.isSingleDoubleParam(): Boolean =
        owner.valueParameters.size == 1 &&
            owner.valueParameters[0].type == doubleType

    private fun IrSimpleFunctionSymbol.isExtensionOnDeriveDouble(): Boolean =
        owner.extensionReceiverParameter?.type == deriveDoubleType

    override fun visitCall(expression: IrCall): IrExpression {
        val owner = expression.symbol.owner
        val parentPackage = owner.parent as? org.jetbrains.kotlin.ir.declarations.IrPackageFragment
        val callableId = CallableId(
            packageName = parentPackage?.packageFqName ?: FqName.ROOT,
            callableName = owner.name,
        )

        if (callableId == DeriveCallableId) {
            val lambdaArg = expression.getValueArgument(0)
            if (lambdaArg is IrFunctionExpression) {
                transformDeriveLambda(lambdaArg)?.let { transformed ->
                    return makeDerivativeGetterCall(transformed, expression)
                }
            }
        }

        if (callableId == DerivativeCallableId) {
            when (val arg = expression.getValueArgument(0)) {
                is IrFunctionExpression -> {
                    transformDerivativeLambda(arg)?.let { return it }
                }
                is IrFunctionReference -> {
                    transformDerivativeFunctionRef(arg, expression)?.let { return it }
                }
            }
        }

        return super.visitCall(expression)
    }

    private fun makeDerivativeGetterCall(
        transformed: IrExpression,
        context: IrExpression,
    ): IrExpression {
        val call = IrCallImpl(
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET,
            type = doubleType,
            symbol = derivativeGetter,
            origin = null,
            superQualifierSymbol = null,
        ).apply {
            dispatchReceiver = transformed
        }
        return call
    }

    private fun extractLambdaBodyExpression(lambdaBody: IrBody?): IrExpression? {
        return when (lambdaBody) {
            is IrExpressionBody -> lambdaBody.expression
            is IrBlockBody -> {
                val lastStmt = lambdaBody.statements.lastOrNull()
                (lastStmt as? IrReturn)?.value
            }
            else -> null
        }
    }

    private fun transformDerivativeLambda(
        expression: IrFunctionExpression,
    ): IrExpression? {
        val lambda = expression.function
        val sourceExpression = extractLambdaBodyExpression(lambda.body) ?: return null

        val start = expression.startOffset.coerceAtLeast(0)
        val end = expression.endOffset.coerceAtLeast(0)

        val transformed = sourceExpression
            .deepCopyWithSymbols(lambda)
            .transform(DeriveBlockTransformer(), null)

        fixOffsetsRecursive(transformed, start, end)

        val derivativeCall = IrCallImpl(
            startOffset = start,
            endOffset = end,
            type = doubleType,
            symbol = derivativeGetter,
            origin = null,
            superQualifierSymbol = null,
        ).apply {
            dispatchReceiver = transformed
        }

        lambda.body = context.irFactory.createExpressionBody(start, end, derivativeCall)
        return expression
    }

    private var derivativeRefCounter = 0

    private fun transformDerivativeFunctionRef(
        ref: IrFunctionReference,
        outerCall: IrCall,
    ): IrExpression? {
        val target = ref.symbol.owner as? IrSimpleFunction ?: return null
        if (target.valueParameters.size != 1) return null
        if (target.returnType != context.irBuiltIns.doubleType) return null

        val sourceExpression = extractLambdaBodyExpression(target.body) ?: return null

        val start = outerCall.startOffset.coerceAtLeast(0)
        val end = outerCall.endOffset.coerceAtLeast(0)

        val parent = target.parentAsContainer() ?: return null
        val funcName = Name.identifier("derivative_ref_${derivativeRefCounter++}")

        val derivedFunc = context.irFactory.buildFun {
            name = funcName
            origin = IrDeclarationOrigin.DEFINED
            modality = Modality.FINAL
            returnType = doubleType
        }.apply {
            this.parent = parent
            val newParam = addValueParameter(
                name = target.valueParameters[0].name,
                type = context.irBuiltIns.doubleType,
            )
            val oldParamSymbol = target.valueParameters[0].symbol
            val cloned = sourceExpression.deepCopyWithSymbols(this)

            remapParameterSymbol(cloned, oldParamSymbol, newParam.symbol)

            val transformed = cloned.transform(DeriveBlockTransformer(), null)
            fixOffsetsRecursive(transformed, start, end)

            val derivativeCall = IrCallImpl(
                startOffset = start,
                endOffset = end,
                type = doubleType,
                symbol = derivativeGetter,
                origin = null,
                superQualifierSymbol = null,
            ).apply {
                dispatchReceiver = transformed
            }

            this.body = context.irFactory.createExpressionBody(
                start,
                end,
                derivativeCall,
            )
        }

        parent.declarations.add(derivedFunc)

        return IrFunctionReferenceImpl(
            startOffset = start,
            endOffset = end,
            type = outerCall.type,
            symbol = derivedFunc.symbol,
            typeArgumentsCount = 2,
        ).apply {
            putTypeArgument(0, context.irBuiltIns.doubleType)
            putTypeArgument(1, context.irBuiltIns.doubleType)
        }
    }

    private fun remapParameterSymbol(
        element: IrElement,
        oldSymbol: IrValueSymbol,
        newSymbol: IrValueSymbol,
    ) {
        element.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    if (expression.symbol == oldSymbol) {
                        expression.symbol = newSymbol
                    }
                    return super.visitGetValue(expression)
                }
            },
            null,
        )
    }

    private fun transformDeriveLambda(
        expression: IrFunctionExpression,
    ): IrExpression? {
        val lambda = expression.function
        val sourceExpression = extractLambdaBodyExpression(lambda.body) ?: return null

        val clonedBody = sourceExpression.deepCopyWithSymbols(lambda.parent)
        val transformed = clonedBody.transform(DeriveBlockTransformer(), null)

        fixOffsetsRecursive(transformed, clonedBody.startOffset, clonedBody.endOffset)
        return transformed
    }

    private fun fixOffsetsRecursive(
        element: IrElement,
        fallbackStart: Int,
        fallbackEnd: Int,
    ) {
        fun fixExpression(expr: IrExpression) {
            if (expr.startOffset == SYNTHETIC_OFFSET) expr.startOffset = fallbackStart
            if (expr.endOffset == SYNTHETIC_OFFSET) expr.endOffset = fallbackEnd
        }

        if (element is IrExpression) fixExpression(element)

        element.acceptChildren(
            object : IrVisitorVoid() {
                @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                override fun visitElement(elem: IrElement) {
                    if (elem is IrExpression) fixExpression(elem)
                    super.visitElement(elem)
                }
            },
            null,
        )
    }

    private fun buildDerivedFunction(
        original: IrSimpleFunction,
        deriveName: String,
    ): IrSimpleFunction {
        val derived = context.irFactory.buildFun {
            name = Name.identifier(deriveName)
            origin = original.origin
            modality = original.modality
            visibility = original.visibility
            isInline = original.isInline
            isSuspend = original.isSuspend
            returnType = deriveDoubleType
        }

        derived.parent = original.parent

        val copied = original.deepCopyWithSymbols(original.parent)

        copied.dispatchReceiverParameter?.let { param ->
            param.parent = derived
            param.type = param.type.remapDouble()
            derived.dispatchReceiverParameter = param
        }

        copied.extensionReceiverParameter?.let { param ->
            param.parent = derived
            param.type = param.type.remapDouble()
            derived.extensionReceiverParameter = param
        }

        derived.valueParameters = copied.valueParameters.map { param ->
            param.apply {
                parent = derived
                type = type.remapDouble()
            }
        }

        original.parentAsContainer()?.declarations?.add(derived)

        return derived
    }

    private fun populateDerivedFunctionBody(
        original: IrSimpleFunction,
        derived: IrSimpleFunction,
    ) {
        val copied = original.deepCopyWithSymbols(original.parent)

        val paramSymbolMap = copied.valueParameters.zip(derived.valueParameters)
            .associate { (src, dst) -> src.symbol to dst.symbol }

        val bodyCopied = copied.body
        derived.body = bodyCopied?.transform(
            FunctionBodyTransformer(derived.symbol, paramSymbolMap),
            null,
        )
    }

    private fun populateOverloadBody(
        overload: IrSimpleFunction,
        deriveSymbol: IrSimpleFunctionSymbol,
    ) {
        val param = overload.valueParameters.singleOrNull() ?: return
        val offset = overload.startOffset.coerceAtLeast(0)
        val end = overload.endOffset.coerceAtLeast(0)

        val call = IrCallImpl(
            startOffset = offset,
            endOffset = end,
            type = deriveDoubleType,
            symbol = deriveSymbol,
            origin = null,
            superQualifierSymbol = null,
        ).apply {
            putValueArgument(
                0,
                IrGetValueImpl(offset, end, param.type, param.symbol),
            )
        }

        overload.body = context.irFactory.createExpressionBody(offset, end, call)
    }

    private fun transformMathOrOpCall(call: IrCall): IrCall {
        val funcName = call.symbol.owner.name.asString()

        doubleOpToDerive[funcName]?.let { deriveOp ->
            return IrCallImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = deriveDoubleType,
                symbol = deriveOp,
                origin = null,
                superQualifierSymbol = null,
            ).apply {
                dispatchReceiver = call.dispatchReceiver
                extensionReceiver = call.extensionReceiver
                repeat(call.valueArgumentsCount) { i ->
                    putValueArgument(i, call.getValueArgument(i))
                }
            }
        }

        mathToDerive[call.symbol]?.let { mathReplace ->
            val args = (0 until call.valueArgumentsCount).map { i ->
                call.getValueArgument(i)
            }
            val argAsReceiver = args.firstOrNull() ?: call.dispatchReceiver
            return IrCallImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = deriveDoubleType,
                symbol = mathReplace,
                origin = null,
                superQualifierSymbol = null,
            ).apply {
                extensionReceiver = argAsReceiver
                args.drop(1).forEachIndexed { i, arg ->
                    putValueArgument(i, arg)
                }
            }
        }

        return call
    }

    private inner class DeriveBlockTransformer : IrElementTransformerVoid() {

        private var independentVariableSeen = false

        override fun visitExpression(expression: IrExpression): IrExpression {
            val transformed = super.visitExpression(expression)
            transformed.type = transformed.type.remapDouble()
            return transformed
        }

        override fun visitConst(expression: IrConst): IrExpression {
            if (expression.kind == IrConstKind.Double) {
                val derivative = if (!independentVariableSeen) {
                    independentVariableSeen = true
                    1.0
                } else {
                    0.0
                }
                return buildDeriveDoubleConst(expression, derivative)
            }
            return super.visitConst(expression)
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            if (expression.type == doubleType) {
                return buildDeriveDoubleConst(expression, 1.0)
            }
            return super.visitGetValue(expression)
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val call = super.visitCall(expression) as IrCall

            transformMathOrOpCall(call).let { replaced ->
                if (replaced !== call) return replaced
            }

            val funcName = expression.symbol.owner.name.asString()
            deriveDoubleToDerive[funcName]?.let { deriveFunc ->
                return IrCallImpl(
                    startOffset = SYNTHETIC_OFFSET,
                    endOffset = SYNTHETIC_OFFSET,
                    type = deriveDoubleType,
                    symbol = deriveFunc,
                    origin = null,
                    superQualifierSymbol = null,
                ).apply {
                    repeat(call.valueArgumentsCount) { i ->
                        putValueArgument(i, call.getValueArgument(i))
                    }
                }
            }

            return call
        }

        private fun buildDeriveDoubleConst(
            value: IrExpression,
            derivative: Double,
        ): IrConstructorCallImpl {
            val start = value.startOffset.coerceAtLeast(0)
            val end = value.endOffset.coerceAtLeast(0)
            return IrConstructorCallImpl.fromSymbolOwner(
                start, end, deriveDoubleType, deriveDoubleConstructor,
            ).apply {
                putValueArgument(0, value)
                putValueArgument(1, IrConstImpl.double(start, end, doubleType, derivative))
            }
        }
    }

    private inner class FunctionBodyTransformer(
        private val returnTarget: IrReturnTargetSymbol,
        private val paramSymbolMap: Map<IrValueParameterSymbol, IrValueSymbol>,
    ) : IrElementTransformerVoid() {

        override fun visitExpression(expression: IrExpression): IrExpression {
            val transformed = super.visitExpression(expression)
            transformed.type = transformed.type.remapDouble()
            return transformed
        }

        override fun visitConst(expression: IrConst): IrExpression {
            if (expression.kind == IrConstKind.Double) {
                System.err.println("Here before crash!")
                return buildDeriveDoubleConstZero(expression.value as Double)
            }
            return super.visitConst(expression)
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            val mappedSymbol = paramSymbolMap[expression.symbol]
            val targetSymbol = mappedSymbol ?: expression.symbol
            return IrGetValueImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = expression.type.remapDouble(),
                symbol = targetSymbol,
            )
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val call = super.visitCall(expression) as IrCall
            return transformMathOrOpCall(call)
        }

        override fun visitReturn(expression: IrReturn): IrExpression {
            val returnExpr = super.visitReturn(expression) as IrReturn
            (returnExpr as IrReturnImpl).returnTargetSymbol = returnTarget
            return returnExpr
        }

        private fun buildDeriveDoubleConstZero(value: Double): IrConstructorCallImpl =
            IrConstructorCallImpl.fromSymbolOwner(
                SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                deriveDoubleType, deriveDoubleConstructor,
            ).apply {
                putValueArgument(
                    0,
                    IrConstImpl.double(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, doubleType, value),
                )
                putValueArgument(
                    1,
                    IrConstImpl.double(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, doubleType, 0.0),
                )
            }
    }

    private fun IrType.remapDouble(): IrType =
        if (this == doubleType) deriveDoubleType else this
}
