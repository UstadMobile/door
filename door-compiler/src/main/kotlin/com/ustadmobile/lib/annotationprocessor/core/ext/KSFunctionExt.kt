package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSType

/**
 * Where this function represents a DAO Update, Insert, or Delete function the first (and only)
 * parameter will be an entity, or a list/array of entities.
 *
 * This function will return the KSType for the entity
 */
fun KSFunction.firstParamEntityType(
    resolver: Resolver
): KSType {
    return parameterTypes.first()?.unwrapComponentTypeIfListOrArray(resolver)
        ?: throw IllegalArgumentException("firstParamEntityComponentType: No component type!")
}

fun KSFunction.hasReturnType(resolver: Resolver): Boolean {
    return returnType != null && returnType != resolver.builtIns.unitType
}
