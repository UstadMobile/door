package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.AsyncConnection
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.DataSourceAsync

/**
 * Open this connection asynchronously if supoprted, otherwise fallback to using the synchronous method
 */
suspend fun DataSource.getConnectionAsyncOrFallback(): Connection {
    return if(this is DataSourceAsync) {
        getConnectionAsync()
    }else {
        getConnection()
    }
}
