package com.ustadmobile.door

import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DoorLiveDataJdbcImplTest {

    @Test
    fun givenEmptyLiveData_whenActive_shouldCallFetchFnAndAddChangeListener() {
        val mockDb = mock<DoorDatabase>()
        val fetchFnCount = AtomicInteger()
        val fetchCountdownLatch = CountDownLatch(1)
        val liveDataJdbc = DoorLiveDataJdbcImpl<Int>(mockDb, listOf("magic")) {
            fetchFnCount.incrementAndGet()
            fetchCountdownLatch.countDown()
            42
        }

        liveDataJdbc.observeForever(mock())
        verify(mockDb, timeout(5000)).addChangeListener(argThat { tableNames.contains("magic") })
        fetchCountdownLatch.await(5, TimeUnit.SECONDS)
        Assert.assertEquals("FetchFn called once", 1, fetchFnCount.get())
    }

    @Test
    fun givenEmptyLiveData_whenInactive_shouldRemoveChangeListener() {
        val mockDb = mock<DoorDatabase>()
        val fetchFnCount = AtomicInteger()
        val liveDataJdbc = DoorLiveDataJdbcImpl<Int>(mockDb, listOf("magic")) {
            fetchFnCount.incrementAndGet()
            42
        }

        val mockObserver = mock<DoorObserver<Int>>()
        liveDataJdbc.observeForever(mockObserver)
        liveDataJdbc.removeObserver(mockObserver)

        verify(mockDb, timeout(5000)).addChangeListener(argThat { tableNames.contains("magic") })
        verify(mockDb, timeout(5000)).removeChangeListener(argThat { tableNames.contains("magic") })
    }

}