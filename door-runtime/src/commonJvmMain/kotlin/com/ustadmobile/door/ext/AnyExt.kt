package com.ustadmobile.door.ext

import java.lang.System

actual val Any.doorIdentityHashCode: Int
    get() = System.identityHashCode(this)
