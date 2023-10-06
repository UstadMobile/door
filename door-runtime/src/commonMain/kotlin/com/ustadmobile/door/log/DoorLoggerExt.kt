package com.ustadmobile.door.log

/**
 * Logging extension functions are 1) shorthand that make logging statements shorter and make it easier to strip out
 * debug logging in production builds using R8/Proguard.
 */
fun DoorLogger.v(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.VERBOSE, throwable, message)
}

fun DoorLogger.v(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.VERBOSE, message, throwable)
}

fun DoorLogger.d(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.DEBUG, throwable, message)
}

fun DoorLogger.d(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.DEBUG, message, throwable)
}

fun DoorLogger.i(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.INFO, throwable, message)
}

fun DoorLogger.i(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.INFO, message, throwable)
}


fun DoorLogger.w(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.WARNING, throwable, message)
}

fun DoorLogger.w(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.WARNING, message, throwable)
}


fun DoorLogger.e(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.ERROR, throwable, message)
}

fun DoorLogger.e(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.ERROR, message, throwable)
}


fun DoorLogger.a(throwable: Throwable? = null, message: () -> String) {
    log(DoorLogLevel.ASSERT, throwable, message)
}

fun DoorLogger.a(message: String, throwable: Throwable? = null){
    log(DoorLogLevel.ASSERT, message, throwable)
}


