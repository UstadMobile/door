package com.ustadmobile.door

import com.ustadmobile.door.lifecycle.Observer
import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.RoomDatabase
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LiveDataImplTest {

    @Test
    fun givenEmptyLiveData_whenActive_shouldCallFetchFnAndAddChangeListener() {
        val mockInvalidationTracker = mock<InvalidationTracker>()
        val mockDb = mock<RoomDatabase> {
            on {
                invalidationTracker
            }.thenReturn(mockInvalidationTracker)
        }

        val fetchFnCount = AtomicInteger()
        val fetchCountdownLatch = CountDownLatch(1)
        val liveDataJdbc = LiveDataImpl<Int>(mockDb, listOf("magic")) {
            fetchFnCount.incrementAndGet()
            fetchCountdownLatch.countDown()
            42
        }

        liveDataJdbc.observeForever(mock())
        verify(mockInvalidationTracker, timeout(5000)).addObserver(argThat {
            tables.contains("magic")
        })
        fetchCountdownLatch.await(5, TimeUnit.SECONDS)
        Assert.assertEquals("FetchFn called once", 1, fetchFnCount.get())
    }

    @Test
    fun givenEmptyLiveData_whenInactive_shouldRemoveChangeListener() {
        val mockInvalidationTracker = mock<InvalidationTracker>()
        val mockDb = mock<RoomDatabase>() {
            on { invalidationTracker }.thenReturn(mockInvalidationTracker)
        }
        val fetchFnCount = AtomicInteger()
        val liveDataJdbc = LiveDataImpl<Int>(mockDb, listOf("magic")) {
            fetchFnCount.incrementAndGet()
            42
        }

        val mockObserver = mock<Observer<Int>> {

        }
        liveDataJdbc.observeForever(mockObserver)
        liveDataJdbc.removeObserver(mockObserver)

        verify(mockInvalidationTracker, timeout(5000)).addObserver(argThat { "magic" in tables })
        verify(mockInvalidationTracker, timeout(5000)).removeObserver(argThat { "magic" in tables })
    }

}