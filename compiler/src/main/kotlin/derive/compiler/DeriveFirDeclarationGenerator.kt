package derive.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.constructClassType
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val DERIVE_CLASS_ID = ClassId(
    FqName("derive"),
    Name.identifier("Derive"),
)

private val DERIVE_DOUBLE_CLASS_ID = ClassId(
    FqName("derive"),
    Name.identifier("DeriveDouble"),
)

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal class DeriveFirDeclarationGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    companion object {
        private val PREDICATE = LookupPredicate.create {
            annotated(DERIVE_CLASS_ID.asSingleFqName())
        }
    }

    private val predicateBasedProvider = session.predicateBasedProvider

    private val matchedFunctions: List<FirNamedFunctionSymbol> by lazy {
        predicateBasedProvider
            .getSymbolsByPredicate(PREDICATE)
            .filterIsInstance<FirNamedFunctionSymbol>()
    }

    private val deriveDoubleType by lazy {
        session.findDeriveDoubleType()
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    override fun getTopLevelCallableIds(): Set<CallableId> {
        val ids = mutableSetOf<CallableId>()
        for (symbol in matchedFunctions) {
            // fDerive(x: DeriveDouble): DeriveDouble
            ids.add(
                CallableId(
                    symbol.callableId.packageName,
                    Name.identifier(
                        "${symbol.callableId.callableName.identifier}Derive",
                    ),
                ),
            )
            // f(x: DeriveDouble): DeriveDouble overload
            ids.add(
                CallableId(
                    symbol.callableId.packageName,
                    symbol.callableId.callableName,
                ),
            )
        }
        return ids
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        if (context != null) return emptyList()

        val (originalFunction, isDeriveVersion) =
            findOriginalFunction(callableId) ?: return emptyList()

        val function = createTopLevelFunction(
            key = if (isDeriveVersion) DerivePluginKey else DeriveOverloadKey,
            callableId = callableId,
            returnType = deriveDoubleType,
        ) {
            for (param in originalFunction.valueParameterSymbols) {
                valueParameter(
                    name = param.name,
                    type = deriveDoubleType,
                )
            }
            withGeneratedDefaultBody()
        }

        return listOf(function.symbol)
    }

    private fun findOriginalFunction(
        callableId: CallableId,
    ): Pair<FirNamedFunctionSymbol, Boolean>? {
        val callableName = callableId.callableName.identifier
        val isDeriveVersion = callableName.endsWith("Derive")
        val originalName = if (isDeriveVersion) {
            callableName.removeSuffix("Derive")
        } else {
            callableName
        }

        val original = matchedFunctions.firstOrNull { symbol ->
            symbol.callableId.packageName == callableId.packageName &&
                symbol.callableId.callableName.identifier == originalName
        } ?: return null

        return original to isDeriveVersion
    }

    private fun FirSession.findDeriveDoubleType(): ConeKotlinType =
        DERIVE_DOUBLE_CLASS_ID
            .toLookupTag()
            .constructClassType(emptyArray(), false)

    internal data object DerivePluginKey : GeneratedDeclarationKey()
    internal data object DeriveOverloadKey : GeneratedDeclarationKey()
}
