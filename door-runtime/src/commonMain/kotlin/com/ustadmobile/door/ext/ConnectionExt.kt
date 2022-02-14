package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.Connection

expect fun <R> Connection.useConnection(block: (Connection) -> R) : R

expect suspend fun <R> Connection.useConnectionAsync(block: suspend (Connection) -> R): R
