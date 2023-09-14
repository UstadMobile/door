package com.ustadmobile.door.http

import com.ustadmobile.door.flow.FlowLoadingState

/**
 * Simple data class that is used on HttpFlowExt
 */
data class ValueAndLoadingState<T>(
    val value: T,
    val loadingState: FlowLoadingState?,
)
