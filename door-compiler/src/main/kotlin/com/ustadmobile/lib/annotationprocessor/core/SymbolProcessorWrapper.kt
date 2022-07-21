package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class SymbolProcessorWrapper(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val processors = listOf(ReplicateWrapperProcessor(environment), DbHttpServerProcessor(environment))

    override fun process(resolver: Resolver): List<KSAnnotated> {
        return processors.flatMap { processor ->
            processor.process(resolver)
        }.distinct()
    }
}