package com.ustadmobile.door.shallowcopy

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.ustadmobile.door.annotation.ShallowCopyable


@OptIn(KspExperimental::class)
fun FileSpec.Builder.addShallowCopyFunction(
    classDecl: KSClassDeclaration,
): FileSpec.Builder{
    val classProps = classDecl.getAllProperties()
    val annotation = classDecl.getAnnotationsByType(ShallowCopyable::class).first()

    addFunction(
        FunSpec.builder(annotation.functionName)
            .apply {
                classDecl.containingFile?.also { addOriginatingKSFile(it) }
                classDecl.getAllSuperTypes().forEach {
                    it.declaration.containingFile?.also { addOriginatingKSFile(it) }
                }
            }
            .receiver(classDecl.toClassName())
            .returns(classDecl.toClassName())
            .apply {
                classProps.filter { it.isMutable }.forEach { prop ->
                    addParameter(
                        ParameterSpec.builder(prop.simpleName.asString(), prop.type.toTypeName())
                            .defaultValue(CodeBlock.of("this.${prop.simpleName.asString()}"))
                        .build()
                    )
                }
            }
            .addCode(CodeBlock.builder()
                .beginControlFlow("return %T().apply", classDecl.toClassName())
                .apply {
                    classProps.filter { it.isMutable }.forEach { prop ->
                        add("this.${prop.simpleName.asString()} = ${prop.simpleName.asString()}\n")
                    }
                }
                .endControlFlow()
                .build())
            .build()
    )

    return this
}

class ShallowCopyProcessor(
    private val environment: SymbolProcessorEnvironment,
): SymbolProcessor {


    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classesToCopy = resolver.getSymbolsWithAnnotation("com.ustadmobile.door.annotation.ShallowCopy")
            .filterIsInstance<KSClassDeclaration>()

        classesToCopy.forEach {  classDecl ->
            FileSpec.builder(classDecl.packageName.asString(), "${classDecl.simpleName.asString()}ShallowCopy")
                .addShallowCopyFunction(classDecl)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        return emptyList()
    }
}