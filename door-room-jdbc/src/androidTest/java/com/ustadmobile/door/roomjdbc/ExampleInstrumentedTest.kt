package com.ustadmobile.door.roomjdbc

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ustadmobile.door.roomjdbc.basicdb.BasicRoomDb
import com.ustadmobile.door.roomjdbc.basicdb.BasicRoomEntity
import org.junit.Assert

import org.junit.Test

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleInstrumentedTest {

    @Test
    fun useTestDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, BasicRoomDb::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }

        val uid = db.basicDao.insert(BasicRoomEntity())
        println(uid)
        val con = ConnectionRoomJdbc(db)
        val preparedStmt = con.prepareStatement("SELECT COUNT(*) FROM BasicRoomEntity")
        val resultSet = preparedStmt.executeQuery()
        resultSet.next()
        val count = resultSet.getInt(1)
        assertEquals("Got count", 1, count)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        assertEquals(2 + 2, 4)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ustadmobile.door.roomjdbc.test", appContext.packageName)
    }
}