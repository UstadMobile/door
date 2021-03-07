package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Query
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import com.ustadmobile.door.annotation.SyncableEntity
import org.jetbrains.annotations.Nullable
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement

/**
 * An ExecutableElement could be a normal Kotlin function, or it could be a getter method. If it is
 * a Java getter method, then we should access it as .propertyName , otherwise it should be accessed
 * as functionName()
 */
internal fun ExecutableElement.makeAccessorCodeBlock(): CodeBlock {
    val codeBlock = CodeBlock.builder()
    if(this.simpleName.toString().startsWith("get")) {
        codeBlock.add(simpleName.substring(3, 4).toLowerCase(Locale.ROOT) + simpleName.substring(4))
    }else {
        codeBlock.add("$simpleName()")
    }

    return codeBlock.build()
}

/**
 * Shorthand to resolve the given element to an ExecutableType (including the resolution of generics, etc)
 * using processingEnvironment TypeUtils
 */
fun ExecutableElement.asMemberOf(typeElement: TypeElement, processingEnvironment: ProcessingEnvironment) : ExecutableType {
    return processingEnvironment.typeUtils.asMemberOf(typeElement.asType() as DeclaredType, this) as ExecutableType
}

/**
 * Where the ExecutableElement represents a DAO method, determine if that DAO method is modifying
 * a syncable entity. This would include any update, delete, or insert method as well as any
 * query method that uses insert, update, or delete in the query.
 */
fun ExecutableElement.isDaoMethodModifyingSyncableEntity(daoTypeElement: TypeElement,
                                                         processingEnv: ProcessingEnvironment,
                                                         allKnownEntityTypesMap: Map<String, TypeElement>) : Boolean {
    val executableType = asMemberOf(daoTypeElement, processingEnv)

    val querySql = getAnnotation(Query::class.java)?.value
    val entityTypeEl: TypeElement? = if(querySql != null) {
        val entityModifiedSimpleName = findEntityModifiedByQuery(querySql,
                allKnownEntityTypesMap.keys.toList())
        allKnownEntityTypesMap[entityModifiedSimpleName]
    }else {
        executableType.parameterTypes.first().unwrapListOrArrayComponentType()
                .asTypeElement(processingEnv)
    }

    return entityTypeEl?.hasAnnotation(SyncableEntity::class.java) == true
}

/**
 * This ExecutableElement might represent a Kotlin property (where it starts with get)
 */
fun ExecutableElement.accessAsPropertyOrFunctionInvocationCall(): String {
    val methodName = simpleName.toString()
    if(simpleName.toString().startsWith("get")) {
        return methodName.substring(3, 4).toLowerCase(Locale.ROOT) + methodName.substring(4)
    }else {
        return "$simpleName()"
    }
}

/**
 * Create a Kotlin Poet FunSpec based on this ExecutableElement
 */
fun ExecutableElement.asFunSpecConvertedToKotlinTypes(enclosing: DeclaredType,
                                    processingEnv: ProcessingEnvironment,
                                    forceNullableReturn: Boolean = false,
                                    forceNullableParameterTypeArgs: Boolean = false,
                                    ignoreAbstract: Boolean = false): FunSpec.Builder {

    val funSpec = FunSpec.builder(simpleName.toString())

    if(!ignoreAbstract && Modifier.ABSTRACT in this.modifiers) {
        funSpec.addModifiers(KModifier.ABSTRACT)
    }

    funSpec.takeIf { Modifier.PROTECTED in this.modifiers }?.addModifiers(KModifier.PROTECTED)

    funSpec.takeIf { Modifier.PROTECTED in this.modifiers }?.addModifiers(KModifier.PRIVATE)

    val resolvedExecutableType = processingEnv.typeUtils.asMemberOf(enclosing, this) as ExecutableType

    var suspendedReturnType = null as TypeName?
    var suspendedParamEl = null as VariableElement?
    for(i in 0 until parameters.size) {
        val resolvedTypeName = resolvedExecutableType.parameterTypes[i].asTypeName().javaToKotlinType()

        if(isContinuationParam(resolvedTypeName)) {
            suspendedParamEl= parameters[i]
            suspendedReturnType = resolveReturnTypeIfSuspended(resolvedExecutableType)
            funSpec.addModifiers(KModifier.SUSPEND)
        }else {
            funSpec.addParameter(parameters[i].simpleName.toString(),
                    resolvedTypeName.copy(nullable = (parameters[i].getAnnotation(Nullable::class.java) != null)))
        }
    }

    if(suspendedReturnType != null && suspendedReturnType != UNIT) {
        funSpec.returns(suspendedReturnType.copy(nullable = forceNullableReturn
                || suspendedParamEl?.getAnnotation(Nullable::class.java) != null))
    }else if(suspendedReturnType == null) {
        var returnType = resolvedExecutableType.returnType.asTypeName().javaToKotlinType()
                .copy(nullable = forceNullableReturn || getAnnotation(Nullable::class.java) != null)
        if(forceNullableParameterTypeArgs && returnType is ParameterizedTypeName) {
            returnType = returnType.rawType.parameterizedBy(*returnType.typeArguments.map { it.copy(nullable = true)}.toTypedArray())
        }

        funSpec.returns(returnType)
    }

    funSpec.addAnnotations(annotationMirrors.map { AnnotationSpec.get(it) })

    return funSpec
}

fun ExecutableElement.asOverridingFunSpecConvertedToKotlinTypes(enclosing: DeclaredType,
                                                                processingEnv: ProcessingEnvironment,
                                                                forceNullableReturn: Boolean = false,
                                                                forceNullableParameterTypeArgs: Boolean = false) : FunSpec.Builder {
    return asFunSpecConvertedToKotlinTypes(enclosing, processingEnv, forceNullableReturn,
            forceNullableParameterTypeArgs, ignoreAbstract = true)
            .addModifiers(KModifier.OVERRIDE)
}