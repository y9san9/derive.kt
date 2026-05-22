package derive.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
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
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
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

@OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
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
    private val deriveDoubleToDerive: Map<String, IrSimpleFunctionSymbol>
    private val derivativeGetter: IrSimpleFunctionSymbol?

    init {
        val deriveClass = context.referenceClass(DeriveDoubleClassId)
            ?: error("Could not resolve $DeriveDoubleClassId")

        deriveDoubleType = deriveClass.defaultType
        deriveDoubleConstructor = deriveClass.constructors
            .single { it.owner.valueParameters.size == 2 }

        plusOpSymbol = deriveClass.getDeriveFunction("plus")!!
        minusOpSymbol = deriveClass.getDeriveFunction("minus")!!
        timesOpSymbol = deriveClass.getDeriveFunction("times")!!
        divOpSymbol = deriveClass.getDeriveFunction("div")!!
        unaryMinusOpSymbol = deriveClass.getDeriveFunction("unaryMinus")!!

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

        // Build fDerive functions directly in original files (not in generated stubs)
        // to ensure proper source info for JVM codegen
        deriveDoubleToDerive = deriveFunctions.associate { original ->
            val deriveName = "${original.name.asString()}Derive"
            // Remove stub from generated file if it exists
            deriveStubs[deriveName]?.let { stub ->
                (
                    stub.parent as? org.jetbrains.kotlin.ir.declarations
                        .IrDeclarationContainer
                    )?.declarations?.remove(stub)
            }
            val derived = buildDerivedFunction(original, deriveName)
            populateDerivedFunctionBody(original, derived)
            original.name.asString() to derived.symbol
        }

        // Populate overload stubs: f(x: DeriveDouble) -> fDerive(x)
        for ((originalName, overloadStub) in overloadStubs) {
            val deriveSymbol = deriveDoubleToDerive[originalName]
            if (deriveSymbol != null) {
                // Move overload stub from generated file to original file
                val original = deriveFunctions.find {
                    it.name.asString() == originalName
                }
                overloadStub.parent?.let { parent ->
                    (
                        parent as? org.jetbrains.kotlin.ir.declarations
                            .IrDeclarationContainer
                        )
                        ?.declarations?.remove(overloadStub)
                }
                original?.parent?.let { parent ->
                    (
                        parent as? org.jetbrains.kotlin.ir.declarations
                            .IrDeclarationContainer
                        )
                        ?.declarations?.add(overloadStub)
                }
                populateOverloadBody(overloadStub, deriveSymbol)
            }
        }

        // Get derivative property getter
        val deriveClassOwner = deriveClass.owner
        val derivativeProp = deriveClassOwner.declarations
            .filterIsInstance<IrProperty>()
            .find { it.name.asString() == "derivative" }
        derivativeGetter = derivativeProp?.getter?.symbol
    }

    private fun IrClassSymbol.getDeriveFunction(
        name: String,
    ): IrSimpleFunctionSymbol? = owner.declarations
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
        val parentPackage = owner.parent
            as? org.jetbrains.kotlin.ir.declarations.IrPackageFragment
        val callableId = CallableId(
            packageName = parentPackage?.packageFqName ?: FqName.ROOT,
            callableName = owner.name,
        )
        if (callableId == DeriveCallableId) {
            val lambdaArg = expression.getValueArgument(0)
            if (lambdaArg is IrFunctionExpression) {
                val transformed = transformDeriveLambda(lambdaArg)
                if (transformed != null) {
                    val getter = derivativeGetter ?: return expression
                    val derivativeCall = IrCallImpl(
                        startOffset = SYNTHETIC_OFFSET,
                        endOffset = SYNTHETIC_OFFSET,
                        type = doubleType,
                        symbol = getter,
                        origin = null,
                        superQualifierSymbol = null,
                    ).apply {
                        dispatchReceiver = transformed
                    }
                    return derivativeCall
                }
            }
        }
        if (callableId == DerivativeCallableId) {
            val arg = expression.getValueArgument(0)
            when (arg) {
                is IrFunctionExpression -> {
                    val transformed = transformDerivativeLambda(arg)
                    if (transformed != null) {
                        return transformed
                    }
                }
                is IrFunctionReference -> {
                    val transformed = transformDerivativeFunctionRef(
                        arg,
                        expression,
                    )
                    if (transformed != null) {
                        return transformed
                    }
                }
            }
        }
        return super.visitCall(expression)
    }

    private var derivativeCounter = 0

    private fun transformDerivativeLambda(
        expression: IrFunctionExpression,
    ): IrExpression? {
        val lambda = expression.function
        val lambdaBody = lambda.body

        val sourceExpression: IrExpression = when (lambdaBody) {
            is IrExpressionBody -> lambdaBody.expression
            is IrBlockBody -> {
                val lastStmt = lambdaBody.statements.lastOrNull()
                when (lastStmt) {
                    is IrReturn -> lastStmt.value
                    else -> return null
                }
            }
            else -> return null
        }

        val start = expression.startOffset.coerceAtLeast(0)
        val end = expression.endOffset.coerceAtLeast(0)

        val transformed = sourceExpression
            .deepCopyWithSymbols(lambda)
            .transform(DeriveBlockTransformer(), null)
            as IrExpression

        fixOffsetsRecursive(
            transformed,
            sourceExpression.startOffset,
            sourceExpression.endOffset,
        )

        val derivativeCall = IrCallImpl(
            startOffset = start,
            endOffset = end,
            type = doubleType,
            symbol = derivativeGetter!!,
            origin = null,
            superQualifierSymbol = null,
        ).apply {
            dispatchReceiver = transformed
        }

        lambda.body = context.irFactory
            .createExpressionBody(start, end, derivativeCall)

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

        val body = target.body ?: return null
        val sourceExpression: IrExpression = when (body) {
            is IrExpressionBody -> body.expression
            is IrBlockBody -> {
                val lastStmt = body.statements.lastOrNull()
                when (lastStmt) {
                    is IrReturn -> lastStmt.value
                    else -> return null
                }
            }
            else -> return null
        }

        val start = outerCall.startOffset.coerceAtLeast(0)
        val end = outerCall.endOffset.coerceAtLeast(0)

        val parent = target.parent as? IrDeclarationContainer ?: return null
        val funcName = Name.identifier(
            "derivative_ref_${derivativeRefCounter++}",
        )

        val derivedFunc = context.irFactory.buildFun {
            name = funcName
            origin = org.jetbrains.kotlin.ir.declarations
                .IrDeclarationOrigin.DEFINED
            modality = org.jetbrains.kotlin.descriptors
                .Modality.FINAL
            returnType = doubleType
        }.apply {
            this.parent = parent
            val newParam = addValueParameter(
                name = target.valueParameters[0].name,
                type = context.irBuiltIns.doubleType,
            )
            val oldParamSymbol = target.valueParameters[0].symbol
            val cloned = sourceExpression.deepCopyWithSymbols(this)
            cloned.transform(
                object : IrElementTransformerVoid() {
                    override fun visitGetValue(
                        expression: IrGetValue,
                    ): IrExpression {
                        if (expression.symbol == oldParamSymbol) {
                            expression.symbol = newParam.symbol
                        }
                        return super.visitGetValue(expression)
                    }
                },
                null,
            )
            val transformed = cloned
                .transform(DeriveBlockTransformer(), null)
                as IrExpression
            fixOffsetsRecursive(
                transformed,
                sourceExpression.startOffset,
                sourceExpression.endOffset,
            )

            val derivativeCall = IrCallImpl(
                startOffset = start,
                endOffset = end,
                type = doubleType,
                symbol = derivativeGetter!!,
                origin = null,
                superQualifierSymbol = null,
            ).apply {
                dispatchReceiver = transformed
            }

            this.body = context.irFactory
                .createExpressionBody(start, end, derivativeCall)
        }

        parent.declarations.add(derivedFunc)

        @Suppress("INVISIBLE_REFERENCE")
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

    private fun transformDeriveLambda(
        expression: IrFunctionExpression,
    ): IrExpression? {
        val lambda = expression.function
        val lambdaBody = lambda.body

        val sourceExpression: IrExpression = when (lambdaBody) {
            is IrExpressionBody -> lambdaBody.expression
            is IrBlockBody -> {
                val lastStmt = lambdaBody.statements.lastOrNull()
                when (lastStmt) {
                    is IrReturn -> lastStmt.value
                    else -> return null
                }
            }
            else -> return null
        }

        val clonedBody = sourceExpression.deepCopyWithSymbols(lambda.parent)
        val transformed = clonedBody.transform(
            DeriveBlockTransformer(),
            null,
        ) as IrExpression

        // Fix synthetic offsets to prevent JVM codegen assertion failures
        fixOffsetsRecursive(
            transformed,
            sourceExpression.startOffset,
            sourceExpression.endOffset,
        )

        return transformed
    }

    private fun fixOffsetsRecursive(
        element: org.jetbrains.kotlin.ir.IrElement,
        fallbackStart: Int,
        fallbackEnd: Int,
    ) {
        if (element is org.jetbrains.kotlin.ir.expressions.IrExpression) {
            if (element.startOffset == SYNTHETIC_OFFSET) {
                element.startOffset = fallbackStart
            }
            if (element.endOffset == SYNTHETIC_OFFSET) {
                element.endOffset = fallbackEnd
            }
        }
        element.acceptChildren(
            object :
                org.jetbrains.kotlin.ir.visitors.IrVisitorVoid() {
                override fun visitElement(
                    el: org.jetbrains.kotlin.ir.IrElement,
                ) {
                    if (el is org.jetbrains.kotlin.ir.expressions
                            .IrExpression
                    ) {
                        if (el.startOffset == SYNTHETIC_OFFSET) {
                            el.startOffset = fallbackStart
                        }
                        if (el.endOffset == SYNTHETIC_OFFSET) {
                            el.endOffset = fallbackEnd
                        }
                    }
                    super.visitElement(el)
                }
            },
            null,
        )
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration !is IrSimpleFunction) {
            return super.visitFunction(declaration)
        }
        return super.visitFunction(declaration)
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

        val copiedFunction = original.deepCopyWithSymbols(
            original.parent,
        ) as IrSimpleFunction

        copiedFunction.dispatchReceiverParameter?.let { param ->
            param.parent = derived
            param.type = param.type.remapDouble()
            derived.dispatchReceiverParameter = param
        }

        copiedFunction.extensionReceiverParameter?.let { param ->
            param.parent = derived
            param.type = param.type.remapDouble()
            derived.extensionReceiverParameter = param
        }

        derived.valueParameters =
            copiedFunction.valueParameters.map { param ->
                param.apply {
                    parent = derived
                    type = type.remapDouble()
                }
            }

        val parent = original.parent
        if (parent is IrDeclarationContainer) {
            parent.declarations.add(derived)
        }

        return derived
    }

    private fun populateDerivedFunctionBody(
        original: IrSimpleFunction,
        derived: IrSimpleFunction,
    ) {
        val copiedFunction = original.deepCopyWithSymbols(
            original.parent,
        ) as IrSimpleFunction

        val paramSymbolMap: Map<IrValueSymbol, IrValueSymbol> =
            copiedFunction.valueParameters.zip(derived.valueParameters)
                .associate { (src, dst) -> src.symbol to dst.symbol }

        val bodyCopied = copiedFunction.body
        val transformedBody = bodyCopied?.transform(
            FunctionBodyTransformer(
                derived.symbol,
                paramSymbolMap,
            ),
            null,
        )
        derived.body = transformedBody
    }

    private fun fixOffsets(
        body: org.jetbrains.kotlin.ir.expressions.IrBody?,
        fallbackStart: Int,
        fallbackEnd: Int,
    ) {
        body?.accept(
            object :
                org.jetbrains.kotlin.ir.visitors.IrVisitorVoid() {
                override fun visitElement(
                    element: org.jetbrains.kotlin.ir.IrElement,
                ) {
                    if (element is org.jetbrains.kotlin.ir.expressions
                            .IrExpression
                    ) {
                        if (element.startOffset == SYNTHETIC_OFFSET) {
                            element.startOffset = fallbackStart
                        }
                        if (element.endOffset == SYNTHETIC_OFFSET) {
                            element.endOffset = fallbackEnd
                        }
                    }
                    super.visitElement(element)
                }
            },
            null,
        )
    }

    private fun populateOverloadBody(
        overload: IrSimpleFunction,
        deriveSymbol: IrSimpleFunctionSymbol,
    ) {
        val param = overload.valueParameters.singleOrNull() ?: return
        val offset = overload.startOffset.coerceAtLeast(0)

        val call = IrCallImpl(
            startOffset = offset,
            endOffset = overload.endOffset.coerceAtLeast(0),
            type = deriveDoubleType,
            symbol = deriveSymbol,
            origin = null,
            superQualifierSymbol = null,
        ).apply {
            putValueArgument(
                0,
                IrGetValueImpl(
                    offset,
                    overload.endOffset.coerceAtLeast(0),
                    param.type,
                    param.symbol,
                ),
            )
        }

        overload.body = context.irFactory.createExpressionBody(
            offset,
            overload.endOffset.coerceAtLeast(0),
            call,
        )
    }

    private inner class DeriveBlockTransformer :
        IrElementTransformerVoid() {

        private var firstConstantSeen = false

        override fun visitExpression(expression: IrExpression): IrExpression {
            val transformed = super.visitExpression(expression)
                as IrExpression
            transformed.type = transformed.type.remapDouble()
            return transformed
        }

        override fun visitConst(expression: IrConst): IrExpression {
            if (expression.kind == IrConstKind.Double) {
                val derivative = if (!firstConstantSeen) {
                    firstConstantSeen = true
                    1.0
                } else {
                    0.0
                }
                val start = expression.startOffset.coerceAtLeast(0)
                val end = expression.endOffset.coerceAtLeast(0)
                return IrConstructorCallImpl.fromSymbolOwner(
                    start,
                    end,
                    deriveDoubleType,
                    deriveDoubleConstructor,
                ).apply {
                    putValueArgument(
                        0,
                        IrConstImpl.double(
                            start,
                            end,
                            doubleType,
                            expression.value as Double,
                        ),
                    )
                    putValueArgument(
                        1,
                        IrConstImpl.double(
                            start,
                            end,
                            doubleType,
                            derivative,
                        ),
                    )
                }
            }
            return super.visitConst(expression)
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            if (expression.type == doubleType) {
                val start = expression.startOffset.coerceAtLeast(0)
                val end = expression.endOffset.coerceAtLeast(0)
                return IrConstructorCallImpl.fromSymbolOwner(
                    start,
                    end,
                    deriveDoubleType,
                    deriveDoubleConstructor,
                ).apply {
                    putValueArgument(
                        0,
                        super.visitGetValue(expression),
                    )
                    putValueArgument(
                        1,
                        IrConstImpl.double(
                            start,
                            end,
                            doubleType,
                            1.0,
                        ),
                    )
                }
            }
            return super.visitGetValue(expression)
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val call = super.visitCall(expression) as IrCall
            val funcName = expression.symbol.owner.name.asString()

            val deriveOp = doubleOpToDerive[funcName]
            if (deriveOp != null) {
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
                    (0 until call.valueArgumentsCount).forEach { i ->
                        putValueArgument(i, call.getValueArgument(i))
                    }
                }
            }

            val mathReplace = mathToDerive[expression.symbol]
            if (mathReplace != null) {
                val args = (0 until call.valueArgumentsCount).map { i ->
                    call.getValueArgument(i)
                }
                val argAsReceiver = args.firstOrNull()
                    ?: call.dispatchReceiver
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

            // Replace f(Double) -> fDerive for @Derive functions
            val deriveFunc = deriveDoubleToDerive[funcName]
            if (deriveFunc != null) {
                val args = (0 until call.valueArgumentsCount).map { i ->
                    call.getValueArgument(i)
                }
                return IrCallImpl(
                    startOffset = SYNTHETIC_OFFSET,
                    endOffset = SYNTHETIC_OFFSET,
                    type = deriveDoubleType,
                    symbol = deriveFunc,
                    origin = null,
                    superQualifierSymbol = null,
                ).apply {
                    args.forEachIndexed { i, arg ->
                        putValueArgument(i, arg)
                    }
                }
            }

            return call
        }
    }

    private inner class FunctionBodyTransformer(
        private val returnTarget: IrReturnTargetSymbol,
        private val paramSymbolMap: Map<IrValueSymbol, IrValueSymbol>,
    ) : IrElementTransformerVoid() {

        override fun visitExpression(expression: IrExpression): IrExpression {
            val transformed = super.visitExpression(expression)
                as IrExpression
            transformed.type = transformed.type.remapDouble()
            return transformed
        }

        override fun visitConst(expression: IrConst): IrExpression {
            if (expression.kind == IrConstKind.Double) {
                return IrConstructorCallImpl.fromSymbolOwner(
                    SYNTHETIC_OFFSET,
                    SYNTHETIC_OFFSET,
                    deriveDoubleType,
                    deriveDoubleConstructor,
                ).apply {
                    putValueArgument(
                        0,
                        IrConstImpl.double(
                            SYNTHETIC_OFFSET,
                            SYNTHETIC_OFFSET,
                            doubleType,
                            expression.value as Double,
                        ),
                    )
                    putValueArgument(
                        1,
                        IrConstImpl.double(
                            SYNTHETIC_OFFSET,
                            SYNTHETIC_OFFSET,
                            doubleType,
                            0.0,
                        ),
                    )
                }
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
            val funcName = expression.symbol.owner.name.asString()
            val deriveOp = doubleOpToDerive[funcName]
            if (deriveOp != null) {
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
                    (0 until call.valueArgumentsCount).forEach { i ->
                        putValueArgument(i, call.getValueArgument(i))
                    }
                }
            }

            val mathReplace = mathToDerive[expression.symbol]
            if (mathReplace != null) {
                val args = (0 until call.valueArgumentsCount).map { i ->
                    call.getValueArgument(i)
                }
                val argAsReceiver = args.firstOrNull()
                    ?: call.dispatchReceiver
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

        override fun visitReturn(expression: IrReturn): IrExpression {
            val returnExpr = super.visitReturn(expression) as IrReturn
            (returnExpr as IrReturnImpl).returnTargetSymbol = returnTarget
            return returnExpr
        }
    }

    private fun IrType.remapDouble(): IrType =
        if (this == doubleType) deriveDoubleType else this
}
