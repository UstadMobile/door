package com.ustadmobile.door.util

/**
 * Blocking wait expect/actual function. On Android/JVM this will use a simple thread.sleep. On
 * Javascript, it has to fallback to burning CPU cycles in a loop
 */
actual fun waitBlocking(delayInMs: Long) {
}