package com.ustadmobile.door.http

data class LoadingState(
    val status: Status = Status.INACTIVE,
) {
    enum class Status {
        INACTIVE, LOADING, DONE, FAILED
    }
}