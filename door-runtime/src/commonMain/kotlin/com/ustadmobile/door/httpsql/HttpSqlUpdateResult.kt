package com.ustadmobile.door.httpsql

import kotlinx.serialization.Serializable

@Serializable
data class HttpSqlUpdateResult(val updates: Int) {
}