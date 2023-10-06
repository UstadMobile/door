package com.ustadmobile.door.log

/**
 * Common interface for logging
 */
interface DoorLogger {

    fun log(level: DoorLogLevel, message: String, throwable: Throwable? = null)

    fun log(level: DoorLogLevel, throwable: Throwable? = null, message: () -> String)


}