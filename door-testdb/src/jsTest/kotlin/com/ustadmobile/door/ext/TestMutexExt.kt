package com.ustadmobile.door.ext

import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestMutexExt {

    @Test
    fun givenReentrantLockAcquired_whenAnotherFunctionRequestsLock_shouldExecuteAfter()  = GlobalScope.promise {
        val mutex = Mutex()
        var time1 = 0L
        var time2 = 0L
        GlobalScope.launch {
            delay(100)
            mutex.withReentrantLock {
                time1 = systemTimeInMillis()
                println("second\n")
            }
        }
        mutex.withReentrantLock {
            mutex.withReentrantLock {
                delay(500)
                time2 = systemTimeInMillis()
                println("first\n")
            }
        }

        delay(500)

        assertNotEquals(0L, time1, "Time of execution for function 1 is set")
        assertNotEquals(0L, time2, "Time of execution for function 2 is set")
        assertTrue(time1 > time2, "Time of function 1 ($time1) was after function 2 ($time2) " +
                "(e.g. it waited due to lock)")
    }

}