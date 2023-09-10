package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.Query
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.ustadmobile.door.annotation.QueryLiveTables
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.RepoHttpBodyParam
import com.ustadmobile.lib.annotationprocessor.core.applyIf
import com.ustadmobile.lib.annotationprocessor.core.isHttpQueryQueryParam
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder

fun KSFunctionDeclaration.toFunSpecBuilder(
    resolver: Resolver,
    containingType: KSType,
    logger: KSPLogger,
) : FunSpec.Builder {

    //Resolve TypeNames etc.
    val ksFunction = this.asMemberOf(containingType)

    val containerDecl = containingType.declaration as? KSClassDeclaration

    return FunSpec.builder(simpleName.asString())
        .addModifiers(this.modifiers.mapNotNull { it.toKModifier() })
        .apply {
            ksFunction.returnType?.also {
                try {
                    returns(it.resolveActualTypeIfAliased().toTypeName(
                        containerDecl?.typeParameters?.toTypeParameterResolver() ?: TypeParameterResolver.EMPTY))
                }catch(e: Exception){
                    logger.error("Invalid return type for function ${this@toFunSpecBuilder.simpleName.asString()}: " +
                            "Ret Type=${this@toFunSpecBuilder.returnType}  " +
                            "resolved=${this@toFunSpecBuilder.returnType?.resolve()} Error=$e", this@toFunSpecBuilder)
                }
            }
        }
        .addParameters(parameters.mapIndexedNotNull { index, param ->
            try {
                ParameterSpec(param.name?.asString() ?: "_",
                    ksFunction.parameterTypes[index]?.toTypeName() ?: resolver.builtIns.unitType.toTypeName())
            }catch (e: Exception) {
                logger.error("Invalid parameter type", param)
                null
            }

        })
        .applyIf(this.extensionReceiver != null) {
            try {
                receiver(extensionReceiver!!.toTypeName())
            }catch(e: Exception) {
                logger.error("Invalid receiver type: $e", this@toFunSpecBuilder)
            }
        }
}


fun KSFunctionDeclaration.hasReturnType(
    resolver: Resolver
): Boolean {
    return this.returnType?.resolve() != null && this.returnType?.resolve() != resolver.builtIns.unitType
}

val KSFunctionDeclaration.isSuspended: Boolean
    get() = Modifier.SUSPEND in modifiers

fun KSFunctionDeclaration.getQueryTables(
    logger: KSPLogger
): List<String> {
    val tablesToWatch = mutableListOf<String>()
    val specifiedLiveTables = getAnnotation(QueryLiveTables::class)
    val querySql = getAnnotation(Query::class)?.value
    if(specifiedLiveTables == null) {
        try {
            val select = CCJSqlParserUtil.parse(querySql ?: "SELECT 0") as Select
            val tablesNamesFinder = TablesNamesFinder()
            tablesToWatch.addAll(tablesNamesFinder.getTableList(select))
        }catch(e: Exception) {
            logger.error("Sorry: JSQLParser could not parse the query : " +
                    querySql +
                    "Please manually specify the tables to observe using @QueryLiveTables annotation", this)
        }
    }else {
        tablesToWatch.addAll(specifiedLiveTables.value)
    }

    return tablesToWatch.toList()
}

fun KSFunctionDeclaration.hasAnyListOrArrayParams(resolver: Resolver): Boolean {
    return parameters.any { it.type.resolve().isListOrArrayType(resolver) }
}

val KSFunctionDeclaration.useSuspendedQuery: Boolean
    get() {
        val returnTypeDecl: KSDeclaration? by lazy {
            returnType?.resolve()?.resolveActualTypeIfAliased()?.declaration
        }

        return isSuspended ||
                returnTypeDecl?.isFlow() == true ||
                returnTypeDecl?.isPagingSource() == true
    }

fun KSFunctionDeclaration.getDaoFunHttpAccessibleDoorReplicationEntities(
    resolver: Resolver
): List<EmbeddedEntityAndPath> {
    if(!hasAnnotation(RepoHttpAccessible::class))
        return emptyList()

    val resultClassDeclaration = returnType?.resolve()?.unwrapResultType(resolver)?.unwrapComponentTypeIfListOrArray(resolver)
        ?.declaration as? KSClassDeclaration
    return resultClassDeclaration?.getDoorReplicateEntityComponents() ?: emptyList()
}

/**
 * Where the receiver KSFunctionDeclaration represents a DAO function with the @RepoHttpAccessible annotation, determine
 * the applicable RepoHttpAccessible.ClientStrategy that will be used.
 */
fun KSFunctionDeclaration.getDaoFunHttpAccessibleEffectiveStrategy(
    resolver: Resolver
): RepoHttpAccessible.ClientStrategy {
    val repoHttpAccessibleAnnotation = getAnnotation(RepoHttpAccessible::class)

    return if(repoHttpAccessibleAnnotation?.clientStrategy == RepoHttpAccessible.ClientStrategy.AUTO) {
        if(getDaoFunHttpAccessibleDoorReplicationEntities(resolver).isNotEmpty())
            RepoHttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES
        else
            RepoHttpAccessible.ClientStrategy.HTTP_WITH_FALLBACK
    }else {
        repoHttpAccessibleAnnotation?.clientStrategy ?: RepoHttpAccessible.ClientStrategy.LOCAL_DB_ONLY
    }
}


/**
 * Where the receiver KSFunctionDeclaration represents a function on a DAO, this returns the Http method that should be
 * used. GET will be used where all parameters are either primitives or lists/arrays thereof (and would be sent in the
 * query parameters of the request).
 */
fun KSFunctionDeclaration.getDaoFunHttpMethodToUse(): String {
    val httpAccessibleAnnotation = getAnnotation(RepoHttpAccessible::class)
        ?: throw IllegalArgumentException("function ${simpleName.asString()} is not annotated RepoHttpAccessible")
    return when {
        httpAccessibleAnnotation.httpMethod == RepoHttpAccessible.HttpMethod.AUTO &&
                parameters.any { param -> param.hasAnnotation(RepoHttpBodyParam::class) } -> {
                    RepoHttpAccessible.HttpMethod.POST.name
        }
        httpAccessibleAnnotation.httpMethod == RepoHttpAccessible.HttpMethod.AUTO -> {
            RepoHttpAccessible.HttpMethod.GET.name
        }
        else -> httpAccessibleAnnotation.httpMethod.name
    }
}

