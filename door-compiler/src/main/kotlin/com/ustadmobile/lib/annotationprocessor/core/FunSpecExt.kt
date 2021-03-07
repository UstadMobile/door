package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Insert
import com.squareup.kotlinpoet.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

/**
 * Simple shorthand function to check if the given function spec
 * contains the given annotation
 */
fun <A: Annotation> FunSpec.hasAnnotation(annotationClass: Class<A>) : Boolean {
    return annotations.any { it.className == annotationClass.asClassName() }
}

/**
 * Simple shorthand to see if the given FunSpec has any of the annotations from the vararg params
 */
fun <A : Annotation> FunSpec.hasAnyAnnotation(vararg annotationsClasses: Class<out A>) : Boolean {
    val annotationClassNames = annotationsClasses.map { it.asClassName() }
    return annotations.any { annotationSpec ->
        annotationClassNames.any { it == annotationSpec.className }
    }
}

fun <A: Annotation> FunSpec.getAnnotationSpec(annotationClass: Class<A>): AnnotationSpec? {
    return annotations.firstOrNull { it.className.canonicalName == annotationClass.canonicalName }
}

/**
 * Where this function represents a DAO function with a query, get the query SQL
 */
fun FunSpec.daoQuerySql() = annotations.daoQuerySql()

/**
 * Determines if this FunSpec represents a DAO query function that where the return result
 * includes syncable entities
 */
fun FunSpec.isQueryWithSyncableResults(processingEnv: ProcessingEnvironment) =
        hasAnnotation(Query::class.java) &&
        returnType?.hasSyncableEntities(processingEnv) == true

/**
 * Shorthand to check if this FunSpec is annotated with @Query and is a query that will
 * modify the database (e.g. it runs UPDATE, DELETE, or INSERT)
 */
val FunSpec.isAQueryThatModifiesTables: Boolean
    get() = hasAnnotation(Query::class.java) && daoQuerySql().isSQLAModifyingQuery()

/**
 * Where this FunSpec represents a DAO annotated by with @Query, this function will determine
 * what entities (if any) it modifies.
 */
fun FunSpec.getDaoFunEntityModifiedByQuery(allKnownEntityTypesMap: Map<String, TypeElement>): ClassName? {
    val modifiedEntityName = findEntityModifiedByQuery(daoQuerySql(), allKnownEntityTypesMap.keys.toList())
    if(modifiedEntityName != null) {
        return allKnownEntityTypesMap[modifiedEntityName]?.asClassName()
    }else {
        return null
    }
}


/**
 * Gets a list of the syncable entities that are used in the given FunSpec where this is a DAO
 * function.
 * For functions annotated with Query, this will return any syncable entities that are in the
 * return result of a select query,
 */
fun FunSpec.daoFunSyncableEntityTypes(processingEnv: ProcessingEnvironment,
                                      allKnownEntityTypesMap: Map<String, TypeElement>) : List<ClassName>{
    if(hasAnnotation(Query::class.java)) {
        val querySql = daoQuerySql()
        if(querySql.isSQLAModifyingQuery()) {
            val modifiedEntity = findEntityModifiedByQuery(querySql,
                    allKnownEntityTypesMap.keys.toList())
            if(modifiedEntity != null) {
                val modifiedTypeEl = allKnownEntityTypesMap[modifiedEntity]
                        ?: throw IllegalArgumentException("${this.name} is modifying an unknown entity!")
                return modifiedTypeEl.asClassName().syncableEntities(processingEnv)
            }else {
                return listOf()
            }
        }else {
            return returnType?.unwrapQueryResultComponentType()?.syncableEntities(processingEnv) ?: listOf()
        }
    }else if(hasAnyAnnotation(Delete::class.java, Update::class.java, Insert::class.java)) {
        return parameters[0].type.unwrapListOrArrayComponentType().syncableEntities(processingEnv)
    }else {
        return listOf()
    }
}

//Shorthand to check if this function is suspended
val FunSpec.isSuspended: Boolean
    get() = KModifier.SUSPEND in modifiers


/**
 * Shorthand to check if this function has an actual return type
 */
val FunSpec.hasReturnType: Boolean
    get() = returnType != null && returnType != UNIT

/**
 * Shorthand to get the type of entity component type for an update, insert, or delete function. This
 * will unwrap list or arrays to give the actual component (singular) type name
 */
val FunSpec.entityParamComponentType: TypeName
    get() = parameters.first().type.unwrapListOrArrayComponentType()


/**
 * Shorthand to make the function non-abstract
 */
fun FunSpec.Builder.removeAbstractModifier(): FunSpec.Builder {
    if(KModifier.ABSTRACT in modifiers)
        modifiers.remove(KModifier.ABSTRACT)

    return this
}

