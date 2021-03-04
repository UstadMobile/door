package com.ustadmobile.door

data class SyncResponse<T>(var remoteChanges: List<T> = listOf())
