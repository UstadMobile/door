package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.Query
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.annotation.QueryLiveTables
import com.ustadmobile.lib.annotationprocessor.core.applyIf
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder

fun KSFunctionDeclaration.toFunSpecBuilder(
    resolver: Resolver,
    containingType: KSType,
) : FunSpec.Builder {

    //Resolve TypeNames etc.
    val ksFunction = this.asMemberOf(containingType)

    return FunSpec.builder(simpleName.asString())
        .addModifiers(this.modifiers.mapNotNull { it.toKModifier() })
        .addModifiers(KModifier.OVERRIDE)
        .apply {
            ksFunction.returnType?.also {
                returns(it.toTypeName())
            }
        }
        .addParameters(parameters.mapIndexed { index, param ->
            ParameterSpec(param.name?.asString() ?: "_",
                ksFunction.parameterTypes[index]?.toTypeName() ?: resolver.builtIns.unitType.toTypeName())
        })
        .applyIf(this.extensionReceiver != null) {
            receiver(extensionReceiver!!.toTypeName())
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
