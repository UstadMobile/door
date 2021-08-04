package com.ustadmobile.door

import com.ustadmobile.door.jdbc.*

expect class PreparedStatementArrayProxy(
    query: String,
    connection: Connection
) : PreparedStatementArrayProxyCommon {

}