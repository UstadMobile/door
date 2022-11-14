package com.ustadmobile.door.shallowcopy

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile


fun FileSpec.Builder.addShallowCopyFunction(
    funDeclaration: KSFunctionDeclaration,
    logger: KSPLogger
): FileSpec.Builder{
    val classDecl = funDeclaration.extensionReceiver?.resolve()?.declaration as? KSClassDeclaration
    if(classDecl == null){
        logger.error("@ShallowCopy function: cannot resolve receiver type", funDeclaration)
        return this
    }

    val classProps = classDecl.getAllProperties()

    val hasBlockParam = funDeclaration.parameters.isNotEmpty()

    addFunction(
        FunSpec.builder(funDeclaration.simpleName.asString())
            .apply {
                classDecl.containingFile?.also { addOriginatingKSFile(it) }
                classDecl.getAllSuperTypes().forEach {
                    it.declaration.containingFile?.also { addOriginatingKSFile(it) }
                }
            }
            .receiver(classDecl.toClassName())
            .returns(classDecl.toClassName())
            .addModifiers(KModifier.ACTUAL)
            .apply {
                if(hasBlockParam) {
                    addParameter("block", LambdaTypeName.get(receiver = classDecl.toClassName(),
                        parameters = emptyList(), returnType = UNIT))
                }
            }
            .addCode(CodeBlock.builder()
                .beginControlFlow("return %T().also", classDecl.toClassName())
                .apply {
                    classProps.filter { it.isMutable }.forEach { prop ->
                        add("it.${prop.simpleName.asString()} = this.${prop.simpleName.asString()}\n")
                    }

                    if(hasBlockParam)
                        add("block(it)\n")
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
        val shallowCopyFunctions = resolver.getSymbolsWithAnnotation("com.ustadmobile.door.annotation.ShallowCopy")
            .filterIsInstance<KSFunctionDeclaration>()

        shallowCopyFunctions.forEach {  funDeclaration ->
            val receiveClassDecl = funDeclaration.extensionReceiver?.resolve()?.declaration as? KSClassDeclaration

            FileSpec.builder(funDeclaration.packageName.asString(), "${receiveClassDecl?.simpleName?.asString()}ShallowCopy")
                .addShallowCopyFunction(funDeclaration, environment.logger)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        return emptyList()
    }
}