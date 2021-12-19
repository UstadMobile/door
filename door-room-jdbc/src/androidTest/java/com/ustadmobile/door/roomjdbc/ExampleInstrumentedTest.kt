package com.ustadmobile.door.roomjdbc

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ustadmobile.door.ext.prepareAndUseStatement
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.roomjdbc.basicdb.BasicRoomDb
import com.ustadmobile.door.roomjdbc.basicdb.BasicRoomEntity
import kotlinx.coroutines.runBlocking
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
    fun prepareAndUseStatement() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, BasicRoomDb::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        db.prepareAndUseStatement("INSERT INTO BasicRoomEntity(uid, name, orgId) VALUES(?, ?, ?)") { stmt ->
            paramsToInsert.forEach {
                stmt.setLong(1, it.first)
                stmt.setString(2, it.second)
                stmt.setInt(3, it.third)
                stmt.executeUpdate()
            }

        }

        val entityFound = db.basicDao.findByUid(42L)
        assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
    }

    @Test
    fun prepareAndUseStmtInTransaction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, BasicRoomDb::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        runBlocking {
            db.withDoorTransactionAsync(BasicRoomDb::class) { transactDb ->
                transactDb.prepareAndUseStatementAsync("INSERT INTO BasicRoomEntity(uid, name, orgId) VALUES(?, ?, ?)") { stmt ->
                    paramsToInsert.forEach {
                        stmt.setLong(1, it.first)
                        stmt.setString(2, it.second)
                        stmt.setInt(3, it.third)
                        stmt.executeUpdateAsyncKmp()
                    }
                }
            }
        }

        val entityFound = db.basicDao.findByUid(42L)
        assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        assertEquals(2 + 2, 4)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ustadmobile.door.roomjdbc.test", appContext.packageName)
    }
}