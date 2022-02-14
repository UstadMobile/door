package com.ustadmobile.door.ext

import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class MutexExtTest {

    @Test
    fun basicTest() {
        val mutex = Mutex()
        runBlocking {
            val time1 = AtomicLong(0)
            val time2 = AtomicLong(0)
            GlobalScope.launch {
                delay(100)
                mutex.withReentrantLock {
                    time1.set(systemTimeInMillis())
                    println("after")
                }
            }
            mutex.withReentrantLock {
                mutex.withReentrantLock {
                    delay(500)
                    time2.set(systemTimeInMillis())
                    println("Not deadlocked")
                }
            }

            delay(500)
            Assert.assertNotEquals("Time1 has been set", 0L, time1.get())
            Assert.assertNotEquals("Time2 has been set", 0L, time2.get())
            Assert.assertTrue("time1 > time2 due to waiting for lock", time1.get() > time2.get())
        }

    }


}