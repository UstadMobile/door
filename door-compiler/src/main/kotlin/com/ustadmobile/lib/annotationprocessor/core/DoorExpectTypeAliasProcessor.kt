package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.*
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.lib.annotationprocessor.core.ext.*

fun FileSpec.Builder.addActualClassForExpectedType(
    ksClassDeclaration: KSClassDeclaration,
    target: DoorTarget,
    resolver: Resolver,
    logger: KSPLogger,
): FileSpec.Builder {
    val classKSType = ksClassDeclaration.asType(emptyList())
    val superClass = ksClassDeclaration.superTypes
        .map { it.resolve() }
        .filter { (it.resolveActualTypeIfAliased().declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS  }
        .firstOrNull()

    val superInterfaces = ksClassDeclaration.superTypes
        .map { it.resolve() }
        .filter { (it.resolveActualTypeIfAliased().declaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE }
        .toList()

    val builder = if(ksClassDeclaration.classKind == ClassKind.INTERFACE) {
        TypeSpec.interfaceBuilder(ksClassDeclaration.toClassName())
    }else {
        TypeSpec.classBuilder(ksClassDeclaration.toClassName())
    }

    addType(builder
        .addOriginatingKSClass(ksClassDeclaration)
        .applyIf(superClass != null) {
            superclass(superClass!!.toTypeName())
        }
        .apply {
            superInterfaces.forEach {
                addSuperinterface(it.toTypeName())
            }
        }
        .addModifiers(ksClassDeclaration.modifiers.mapNotNull { it.toKModifier() }.filter { it != KModifier.EXPECT })
        .addModifiers(KModifier.ACTUAL)
        .applyIf(target == DoorTarget.ANDROID) {
            if(ksClassDeclaration.hasAnnotation(DoorDao::class)) {
                addAnnotation(AnnotationSpec.builder(ClassName("androidx.room", "Dao")).build())
            }else if(ksClassDeclaration.hasAnnotation(DoorDatabase::class)) {
                val annotationSpec = ksClassDeclaration.annotations.toList().first {
                    (it.annotationType.resolve().declaration as? KSClassDeclaration)?.simpleName?.asString() == "DoorDatabase"
                }.toAnnotationSpec()
                addAnnotation(AnnotationSpec.builder(ClassName("androidx.room", "Database"))
                    .apply {
                        annotationSpec.members.forEach {
                            addMember(it)
                        }
                    }
                    .build())
            }
        }
        .apply {
            ksClassDeclaration.getDeclaredFunctions().filter { !it.isConstructor() } .forEach { ksFunDec ->
                addFunction(ksFunDec.toFunSpecBuilder(resolver, classKSType, logger)
                    .removeModifier(KModifier.EXPECT)
                    .addModifiers(KModifier.ACTUAL)
                    .applyIf(ksClassDeclaration.classKind == ClassKind.INTERFACE) {
                        addModifiers(KModifier.ABSTRACT)
                    }
                    .applyIf(target == DoorTarget.ANDROID) {
                        val annotationsToCopy = listOf("Insert", "Query", "RawQuery", "Delete", "Update")
                        copyAnnotations(ksFunDec) {
                            it.annotationType.resolve().declaration.simpleName.asString() in annotationsToCopy
                        }
                    }
                    .build())
            }

            ksClassDeclaration.getDeclaredProperties().forEach { ksPropDec ->
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
        val dbSymbols = resolver.getSymbolsWithAnnotation("com.ustadmobile.door.annotation.DoorDatabase")
            .filterIsInstance<KSClassDeclaration>()
            .filter { Modifier.EXPECT in it.modifiers }

        val daoSymbols = resolver.getSymbolsWithAnnotation("com.ustadmobile.door.annotation.DoorDao")
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