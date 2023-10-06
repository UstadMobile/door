package com.ustadmobile.door.log

import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier

class NapierDoorLogger(
    private val tag: String = DoorTag.LOG_TAG
): DoorLogger {

    private fun DoorLogLevel.toNapierLogLevel() = when(this) {
        DoorLogLevel.VERBOSE -> LogLevel.VERBOSE
        DoorLogLevel.DEBUG -> LogLevel.DEBUG
        DoorLogLevel.INFO -> LogLevel.INFO
        DoorLogLevel.WARNING -> LogLevel.WARNING
        DoorLogLevel.ERROR -> LogLevel.ERROR
        DoorLogLevel.ASSERT -> LogLevel.ASSERT
    }

    override fun log(level: DoorLogLevel, message: String, throwable: Throwable?) {
        Napier.log(level.toNapierLogLevel(), tag = tag, throwable = throwable, message = message)
    }

    override fun log(level: DoorLogLevel, throwable: Throwable?, message: () -> String) {
        val logLevel = level.toNapierLogLevel()
        if(Napier.isEnable(logLevel, tag))
            Napier.log(logLevel, tag = tag, throwable = throwable, message = message())
    }

}