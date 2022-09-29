package com.ustadmobile.door.httpsql

import kotlinx.serialization.Serializable

@Serializable
class PrepareStatementRequest(val sql: String, val generatedKeys: Int) {
}