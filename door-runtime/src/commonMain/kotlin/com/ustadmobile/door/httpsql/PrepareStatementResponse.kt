package com.ustadmobile.door.httpsql

import kotlinx.serialization.Serializable

@Serializable
class PrepareStatementResponse (val preparedStatementId: Int, val connectionId: Int){

}