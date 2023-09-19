package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.Entity
import androidx.room.Index
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.annotation.Triggers
import kotlin.reflect.KClass

fun <A:Annotation> KSAnnotation.isAnnotationClass(annotationKClass: KClass<A>): Boolean {
    return shortName.getShortName() == annotationKClass.simpleName && annotationType.resolve().declaration
        .qualifiedName?.asString() == annotationKClass.qualifiedName
}

@Suppress("UNCHECKED_CAST")
fun KSAnnotation.getArgValueAsClassList(argName: String): List<KSClassDeclaration> {
    val ksTypeList = arguments.firstOrNull { it.name?.asString() == argName }?.value as?
            List<KSType> ?: emptyList()
    return ksTypeList.mapNotNull { it.declaration as? KSClassDeclaration }
}


fun Iterable<KSValueArgument>.firstOrNullByName(name :String): KSValueArgument? {
    return firstOrNull { it.name?.asString() == name }
}

fun KSAnnotation.getArgumentValueByName(
    name: String
): KSValueArgument? {
    return arguments.firstOrNull { it.name?.asString() == name }
}

@Suppress("UNCHECKED_CAST")
fun KSAnnotation.getArgumentValueByNameAsAnnotationList(
    name: String
): List<KSAnnotation>? {
    return getArgumentValueByName(name)?.value?.let { it as List<KSAnnotation> }
}

fun KSAnnotation.getArgumentValueByNameAsStringList(
    name: String
): List<String>? {
    return getArgumentValueByName(name)?.value?.let { it as List<*> }?.mapNotNull { it as? String }
}

fun KSAnnotation.getArgumentValueByNameAsString(
    name: String
): String? {
    return getArgumentValueByName(name)?.value as? String
}

fun KSAnnotation.getArgumentValueByNameAsKSType(
    name: String
): KSType? {
    return getArgumentValueByName(name)?.value as? KSType
}

fun KSAnnotation.getArgumentValueByNameAsKSTypeList(
    name: String
): List<KSType>? {
    return getArgumentValueByName(name)?.value?.let { it as List<*> }?.mapNotNull { it as? KSType }
}

/**
 * Due to bug that crashes JS processing, we can't use the normal getAnnotation
 *  https://github.com/google/ksp/issues/1503
 *
 * In the meantime, use KSAnnotation instead.
 */
fun KSAnnotation.toIndex(): Index {
    assertQualifiedNameIs("androidx.room.Index")
    val nameArg = arguments.firstOrNullByName("name")?.value as? String
    val colNameValues: List<*> = arguments.firstOrNullByName("value")?.value as List<*>
    val isUnique = arguments.firstOrNull { it.name?.asString() == "unique" }?.value as? Boolean ?: false

    return Index(
        value = colNameValues.mapNotNull { it as? String }.toTypedArray(),
        name = nameArg ?: "",
        unique = isUnique,
    )
}


/**
 * Due to bug that crashes JS processing, we can't use the normal getAnnotation
 *  https://github.com/google/ksp/issues/1503
 *
 * In the meantime, use KSAnnotation instead.
 */
fun KSAnnotation.toEntity(): Entity {
    assertQualifiedNameIs("androidx.room.Entity")
    val entityTableNameArg = arguments.firstOrNullByName("tableName")?.value as? String

    val indices: List<Index> = getArgumentValueByNameAsAnnotationList("indices")?.map {
        it.toIndex()
    } ?: emptyList()

    return Entity(
        tableName = entityTableNameArg ?: "",
        indices = indices.toTypedArray(),
        inheritSuperIndices = false,
        primaryKeys = getArgumentValueByNameAsStringList("primaryKeys")?.toTypedArray() ?: emptyArray(),
        foreignKeys = emptyArray(),
        ignoredColumns = emptyArray(),
    )
}

private fun KSAnnotation.assertQualifiedNameIs(qualifiedName: String) {
    val qualifiedNameStr = annotationType.resolve().declaration.qualifiedName?.asString()
    if(qualifiedNameStr != qualifiedName) {
        throw IllegalArgumentException("Called toTrigger on KSAnnotation that is not a Trigger: it is $qualifiedNameStr")
    }
}

fun KSAnnotation.toReplicateEntity(): ReplicateEntity {
    assertQualifiedNameIs("com.ustadmobile.door.annotation.ReplicateEntity")
    return ReplicateEntity(
        tableId = arguments.firstOrNull { it.name?.asString() == "tableId" }?.value?.toString()?.toInt()
            ?: throw IllegalArgumentException("tableId is mandatory"),
        remoteInsertStrategy = getArgumentValueByNameAsKSType("remoteInsertStrategy")?.let {
            ReplicateEntity.RemoteInsertStrategy.valueOf(it.declaration.simpleName.asString())
        } ?: ReplicateEntity.RemoteInsertStrategy.CALLBACK,
    )
}

fun KSAnnotation.toTrigger() : Trigger{
    assertQualifiedNameIs("com.ustadmobile.door.annotation.Trigger")
    arguments.firstOrNull { it.name?.asString() == "" }?.value

    return Trigger(
        name = getArgumentValueByNameAsString("name") ?: throw IllegalArgumentException("name is mandatory"),
        order = getArgumentValueByNameAsKSType("order")?.let { Trigger.Order.valueOf(it.declaration.simpleName.asString()) }
            ?: throw java.lang.IllegalArgumentException("order is mandatory"),
        events = getArgumentValueByNameAsKSTypeList("events")?.map {
            Trigger.Event.valueOf(it.declaration.simpleName.asString())
        }?.toTypedArray() ?: throw IllegalArgumentException("events is mandatory"),
        on = getArgumentValueByNameAsKSType("on")?.let { Trigger.On.valueOf(it.declaration.simpleName.asString()) }
            ?: throw IllegalArgumentException("on is mandatory"),
        sqlStatements = getArgumentValueByNameAsStringList("sqlStatements")?.toTypedArray() ?: emptyArray(),
        postgreSqlStatements = getArgumentValueByNameAsStringList("postgreSqlStatements")?.toTypedArray() ?: emptyArray(),
        conditionSql = getArgumentValueByNameAsString("conditionSql") ?: "",
        conditionSqlPostgres = getArgumentValueByNameAsString("conditionSqlPostgres") ?: "",
    )
}

fun KSAnnotation.toTriggers(): Triggers {
    assertQualifiedNameIs("com.ustadmobile.door.annotation.Triggers")
    return Triggers(getArgumentValueByNameAsAnnotationList("value")?.map {
        it.toTrigger()
    }?.toTypedArray() ?: emptyArray())
}

/**
 * The HttpServerFunctionCall annotation can reference a different DAO if explicitly specified via the "functionDao"
 * parameter, otherwise, by default, the HttpServerFunctionCall refers to the DAO that has the HttpAccessible annotation
 * itself.
 *
 * @receiver a KSAnnotation that represents an HttpServerFunctionCall
 * @param daoKSClassDeclaration the KSClassDeclaration of the DAO which has the HttpAccessible function, of which the
 *        receiver is a part.
 * @return the KSClassDeclaration for the specified DAO e.g. if specified by functionDao=OtherDao::class, or the
 *         daoKSClassDeclaration if unspecified.
 */
fun KSAnnotation.getHttpServerFunctionCallDaoKSClass(
    daoKSClassDeclaration: KSClassDeclaration,
    resolver: Resolver
): KSClassDeclaration {
    assertQualifiedNameIs("com.ustadmobile.door.annotation.HttpServerFunctionCall")
    val daoClassArg = getArgumentValueByNameAsKSType("functionDao")

    return if(daoClassArg != null && daoClassArg != resolver.builtIns.anyType) {
        daoClassArg.declaration as KSClassDeclaration
    }else {
        daoKSClassDeclaration
    }
}

/**
 * Where the given receiver KSAnnotation represents a HttpServerFunctionCall annotation, lookup the function declaration
 * that it is referring to. This will check the functionDao (if specified) and return the matching function declaration.
 *
 * If there is no matching function NoSuchElement exception will be thrown.
 *
 */
fun KSAnnotation.httpServerFunctionFunctionDecl(
    daoKSClassDeclaration: KSClassDeclaration,
    resolver: Resolver
): KSFunctionDeclaration {
    assertQualifiedNameIs("com.ustadmobile.door.annotation.HttpServerFunctionCall")
    val functionName = getArgumentValueByNameAsString("functionName")
    val functionCallDaoKSClass = getHttpServerFunctionCallDaoKSClass(daoKSClassDeclaration, resolver)

    return functionCallDaoKSClass.getAllFunctions().first {
        it.simpleName.asString() == functionName
    }
}
