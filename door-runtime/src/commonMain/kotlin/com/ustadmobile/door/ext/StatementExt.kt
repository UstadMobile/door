package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.AsyncCloseable
import com.ustadmobile.door.jdbc.Statement

suspend fun Statement.closeAsyncOrFallback() {
    if(this is AsyncCloseable)
        closeAsync()
    else
        close()
}