package com.ustadmobile.door.room

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.prepareStatementAsyncOrFallback
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*

actual open class InvalidationTracker(
    vararg tables: String,
) {



    actual open fun addObserver(observer: InvalidationTrackerObserver) {

    }

    actual open fun removeObserver(observer: InvalidationTrackerObserver) {

    }
}