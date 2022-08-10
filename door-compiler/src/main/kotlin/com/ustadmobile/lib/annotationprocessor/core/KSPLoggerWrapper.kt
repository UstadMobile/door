package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

class KSPLoggerWrapper(
    private val destLogger: KSPLogger
) : KSPLogger by destLogger {

    var hasErrors: Boolean = false
        private set

    override fun error(message: String, symbol: KSNode?) {
        hasErrors = true
        destLogger.error(message, symbol)
    }
}
