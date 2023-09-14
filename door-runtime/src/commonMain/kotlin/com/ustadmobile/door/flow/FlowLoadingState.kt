package com.ustadmobile.door.flow

/**
 * Represents the http loading state of a flow that was returned from a generated Repository.
 */
data class FlowLoadingState(
    val status: Status = Status.INACTIVE,
) {
    enum class Status {
        INACTIVE, LOADING, DONE, FAILED
    }
}