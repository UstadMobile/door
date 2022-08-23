package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.lib.annotationprocessor.core.ext.*

fun FileSpec.Builder.addActualClassForExpectedType(
    dbKSClassDeclaration: KSClassDeclaration,
    target: DoorTarget,
    resolver: Resolver,
    logger: KSPLogger,
): FileSpec.Builder {
    val classKSType = dbKSClassDeclaration.asType(emptyList())
    val superClass = dbKSClassDeclaration.superTypes
        .map { it.resolve() }
        .filter { (it.resolveActualTypeIfAliased().declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS  }
        .firstOrNull()

    val superInterfaces = dbKSClassDeclaration.superTypes
        .map { it.resolve() }
        .filter { (it.resolveActualTypeIfAliased().declaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE }
        .toList()

    addType(TypeSpec.classBuilder(dbKSClassDeclaration.toClassName())
        .applyIf(superClass != null) {
            superclass(superClass!!.toTypeName())
        }
        .apply {
            superInterfaces.forEach {
                addSuperinterface(it.toTypeName())
            }
        }
        .addModifiers(dbKSClassDeclaration.modifiers.mapNotNull { it.toKModifier() }.filter { it != KModifier.EXPECT })
        .addModifiers(KModifier.ACTUAL)
        .apply {
            dbKSClassDeclaration.getDeclaredFunctions().filter { !it.isConstructor() } .forEach { ksFunDec ->
                addFunction(ksFunDec.toFunSpecBuilder(resolver, classKSType, logger)
                    .removeModifier(KModifier.EXPECT)
                    .addModifiers(KModifier.ACTUAL)
                    .build())
            }

            dbKSClassDeclaration.getDeclaredProperties().forEach { ksPropDec ->
                addProperty(ksPropDec.toPropSpecBuilder(classKSType)
                    .removeModifier(KModifier.EXPECT)
                    .addModifiers(KModifier.ACTUAL)
                    .build())
            }
        }
        .build())
    return this
}


class DoorExpectTypeAliasProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Database")
            .filterIsInstance<KSClassDeclaration>()
            .filter { Modifier.EXPECT in it.modifiers }

        val daoSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Dao")
            .filterIsInstance<KSClassDeclaration>()
            .filter { Modifier.EXPECT in it.modifiers }

        val target = environment.doorTarget(resolver)

        (dbSymbols + daoSymbols).forEach { dbKSClass ->
            FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString())
                .addActualClassForExpectedType(dbKSClass, target, resolver, environment.logger)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        return emptyList()
    }

    companion object {

        const val SUFFIX_DOOR_ACTUAL = "_DoorActual"


    }
}