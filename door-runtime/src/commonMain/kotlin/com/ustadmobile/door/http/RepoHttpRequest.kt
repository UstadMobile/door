package com.ustadmobile.door.http

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier


inline fun <R> DoorDatabaseRepository.repoHttpRequest(
    repoPath: String,
    block: () -> R
): R {
    return try {
        block()
    }catch(e: Exception) {
        Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
            "$this: repoHttpRequest: exception for $repoPath"
        }
        throw e
    }
}

inline fun <R> DoorDatabaseRepository.repoHttpRequestWithFallback(
    repoPath: String,
    http: () -> R,
    fallback: () -> R,
): R {
    return try {
        http()
    }catch(e: Exception) {
        Napier.v(tag = DoorTag.LOG_TAG, throwable = e) {
            "$this: repoHttpRequestWithFallback: exception for $repoPath"
        }
        fallback()
    }
}

inline fun DoorDatabaseRepository.repoReplicateHttpRequest(
    repoPath: String,
    block: () -> Unit
) {
    try {
        block()
    }catch(e: Exception) {
        Napier.v(tag = DoorTag.LOG_TAG, throwable = e) {
            "$this: repoHttpRequestWithFallback: exception for $repoPath"
        }
    }
}
