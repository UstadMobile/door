package com.ustadmobile.door.http

/**
 * Simple data class that is used on HttpFlowExt
 */
data class ValueAndLoadingState<T>(
    val value: T,
    val loadingState: LoadingState?,
)
