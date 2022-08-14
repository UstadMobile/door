package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
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
