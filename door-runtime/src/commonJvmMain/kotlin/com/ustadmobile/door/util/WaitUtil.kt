package com.ustadmobile.door.util

actual fun waitBlocking(delayInMs: Long) = Thread.sleep(delayInMs)
