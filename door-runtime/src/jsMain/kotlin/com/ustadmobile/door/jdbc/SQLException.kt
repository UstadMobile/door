package com.ustadmobile.door.jdbc

actual class SQLException actual constructor(message: String?, cause: Throwable?) : Exception(message, cause) {

}
