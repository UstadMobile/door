package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.lib.annotationprocessor.core.applyIf

fun KSFunctionDeclaration.toOverridingFunSpecBuilder(
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