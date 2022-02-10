package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.Connection

expect inline fun <R> Connection.useConnection(block: (Connection) -> R) : R
