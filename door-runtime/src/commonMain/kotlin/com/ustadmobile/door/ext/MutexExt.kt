package com.ustadmobile.door.ext

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// As per https://gist.github.com/elizarov/9a48b9709ffd508909d34fab6786acfe
// see https://github.com/Kotlin/kotlinx.coroutines/issues/1686
suspend fun <T> Mutex.withReentrantLock(
    block: suspend () -> T,
): T {
    val key = ReentrantMutexContextKey(this)
    // call block directly when this mutex is already locked in the context
    if (coroutineContext[key] != null) return block()
    // otherwise add it to the context and lock the mutex
    return withContext(ReentrantMutexContextElement(key)) {
        withLock { block() }
    }
}

class ReentrantMutexContextElement(
    override val key: ReentrantMutexContextKey
) : CoroutineContext.Element

data class ReentrantMutexContextKey(
    val mutex: Mutex,
    val readOnly: Boolean = false
) : CoroutineContext.Key<ReentrantMutexContextElement> {}

