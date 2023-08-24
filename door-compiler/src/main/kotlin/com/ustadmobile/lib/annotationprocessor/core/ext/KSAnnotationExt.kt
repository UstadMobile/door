package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.Entity
import androidx.room.Index
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
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
