package com.ustadmobile.door.log

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

abstract class AbstractNapierAntilog(private val minLogLevel: LogLevel = LogLevel.DEBUG) : Antilog(){

    override fun isEnable(priority: LogLevel, tag: String?): Boolean {
        return priority.ordinal >= minLogLevel.ordinal
    }
}