package com.ustadmobile.door.httpsql

import kotlinx.serialization.Serializable

@Serializable
class PreparedStatementExecRequest(val params: List<PreparedStatementParam>) {

}