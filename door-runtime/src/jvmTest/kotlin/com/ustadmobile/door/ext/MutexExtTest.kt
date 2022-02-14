package com.ustadmobile.door.ext

import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert
import org.junit.Test

class MutexExtTest {

    @Test
    fun basicTest() {
        val mutex = Mutex()
        runBlocking {
            var time1 = 0L
            var time2 = 0L
            GlobalScope.launch {
                delay(100)
                mutex.withReentrantLock {
                    time1 = systemTimeInMillis()
                    println("after")
                }
            }
            mutex.withReentrantLock {
                mutex.withReentrantLock {
                    delay(500)
                    time2 = systemTimeInMillis()
                    println("Not deadlocked")
                }
            }

            delay(500)
            Assert.assertTrue(time1 != 0L && time2 != 0L && time1 > time2)
        }

    }


}