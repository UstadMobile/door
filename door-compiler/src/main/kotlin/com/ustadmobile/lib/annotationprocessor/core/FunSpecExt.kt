package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Query
import androidx.room.RoomDatabase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.annotation.RepoHttpAccessible
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

/**
 * Simple shorthand function to check if the given function spec
 * contains the given annotation
 */
fun <A: Annotation> FunSpec.hasAnnotation(annotationClass: Class<A>) : Boolean {
    return annotations.any { it.className == annotationClass.asClassName() }
}

/**
 * Where this function represents a DAO function with a query, get the query SQL
 */
fun FunSpec.daoQuerySql() = annotations.daoQuerySql()

/**
 * Shorthand to check if this FunSpec is annotated with @Query and is a query that will
 * modify the database (e.g. it runs UPDATE, DELETE, or INSERT)
 */
val FunSpec.isAQueryThatModifiesTables: Boolean
    get() = hasAnnotation(Query::class.java) && daoQuerySql().isSQLAModifyingQuery()


//Shorthand to check if this function is suspended
val FunSpec.isSuspended: Boolean
    get() = KModifier.SUSPEND in modifiers


/**
 * Shorthand to check if this function has an actual return type
 */
val FunSpec.hasReturnType: Boolean
    get() = returnType != null && returnType != UNIT


/**
 * Shorthand to make the function non-abstract
 */
fun FunSpec.Builder.removeAbstractModifier(): FunSpec.Builder {
    if(KModifier.ABSTRACT in modifiers)
        modifiers.remove(KModifier.ABSTRACT)

    return this
}

fun FunSpec.Builder.removeAnnotations(): FunSpec.Builder {
    annotations.clear()
    return this
}

