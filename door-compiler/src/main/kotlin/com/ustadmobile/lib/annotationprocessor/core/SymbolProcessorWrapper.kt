package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class SymbolProcessorWrapper(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val loggerWrapper = KSPLoggerWrapper(environment.logger)

    private val validatorProcessor = DoorValidatorProcessor(loggerWrapper, environment)

    private val jdbcProcessor = DoorJdbcProcessor(environment)

    private val processors = listOf(DoorReplicateWrapperProcessor(environment), DoorHttpServerProcessor(environment),
        DoorRepositoryProcessor(environment), jdbcProcessor)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            validatorProcessor.process(resolver)

            return if(!loggerWrapper.hasErrors) {
                jdbcProcessor.dbConnection = validatorProcessor.sqliteConnection
                processors.flatMap { processor ->
                    processor.process(resolver)
                }.distinct()
            }else {
                emptyList()
            }
        }finally {
            validatorProcessor.cleanup(resolver)
        }
    }
}