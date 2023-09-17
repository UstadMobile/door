package com.ustadmobile.door.log

import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.LogLevel

actual class NapierAntilog actual constructor(minLogLevel: LogLevel) : AbstractNapierAntilog(minLogLevel) {
    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val logTag = tag ?: DoorTag.LOG_TAG

        val fullMessage = if (message != null) {
            if (throwable != null) {
                "$message\n${throwable.message}"
            } else {
                message
            }
        } else throwable?.message ?: return

        when (priority) {
            LogLevel.VERBOSE -> console.log("VERBOSE $logTag : $fullMessage")
            LogLevel.DEBUG -> console.log("DEBUG $logTag : $fullMessage")
            LogLevel.INFO -> console.info("INFO $logTag : $fullMessage")
            LogLevel.WARNING -> console.warn("WARNING $logTag : $fullMessage")
            LogLevel.ERROR -> console.error("ERROR $logTag : $fullMessage")
            LogLevel.ASSERT -> console.error("ASSERT $logTag : $fullMessage")
        }
    }
}