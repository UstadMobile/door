package com.ustadmobile.door.util

import kotlinx.atomicfu.atomic

class TransactionDepthCounter() {

    private val transactionDepthInternal = atomic(0)

    val transactionDepth: Int
        get() = transactionDepthInternal.value

    internal fun incrementTransactionDepth() {
        transactionDepthInternal.incrementAndGet()
    }

    internal fun decrementTransactionDepth() {
        transactionDepthInternal.decrementAndGet()
    }


}
