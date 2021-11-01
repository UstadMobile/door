package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Insert
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDatabase
import javax.annotation.processing.ProcessingEnvironment
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

fun FunSpec.Builder.removeAnnotations(): FunSpec.Builder {
    annotations.clear()
    return this
}

/**
 * Turn this FunSpec into a function that will override wrapNewTransactionFor
 */
fun FunSpec.Builder.addOverrideWrapNewTransactionFun() : FunSpec.Builder {
    val typeVariableNameT = TypeVariableName.invoke("T", DoorDatabase::class)
    addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED, KModifier.OPEN)
    addTypeVariable(typeVariableNameT)
    addParameter("dbKClass", KClass::class.asClassName()
            .parameterizedBy(typeVariableNameT))
    addParameter("transactionDb", typeVariableNameT)
    returns(typeVariableNameT)
    return this
}
