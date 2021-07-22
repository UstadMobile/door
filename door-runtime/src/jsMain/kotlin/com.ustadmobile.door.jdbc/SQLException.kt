package com.ustadmobile.door.jdbc

actual class SQLException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
