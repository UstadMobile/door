package com.ustadmobile.door

data class SyncRequest<T>(var incomingChanges: List<T> = listOf())
