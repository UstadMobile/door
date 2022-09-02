package com.ustadmobile.door.testandroid

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc
import dbonly.VanillaDatabase
import dbonly.VanillaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert

import org.junit.Test

import org.junit.Assert.*

class RoomJdbcTest {

    @Test
    fun useTestDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, VanillaDatabase::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }

        val uid = db.vanillaDao.insert(VanillaEntity())
        println(uid)
        val con = ConnectionRoomJdbc(db)
        val preparedStmt = con.prepareStatement("SELECT COUNT(*) FROM VanillaEntity")
        val resultSet = preparedStmt.executeQuery()
        resultSet.next()
        val count = resultSet.getInt(1)
        assertEquals("Got count", 1, count)
    }

    @Test
    fun prepareAndUseStatement() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, VanillaDatabase::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        db.prepareAndUseStatement("INSERT INTO VanillaEntity(vanillaUid, vanillaStr, vanillaNum) VALUES(?, ?, ?)") { stmt ->
            paramsToInsert.forEach {
                stmt.setLong(1, it.first)
                stmt.setString(2, it.second)
                stmt.setInt(3, it.third)
                stmt.executeUpdate()
            }

        }

        val entityFound = db.vanillaDao.findEntityByPk(42L)
        assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
    }

    @Test
    fun prepareAndUseStatementThenInsert() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, VanillaDatabase::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        runBlocking {
            db.withDoorTransactionAsync(VanillaDatabase::class) { txDb ->
                txDb.prepareAndUseStatementAsync("INSERT INTO VanillaEntity(vanillaUid, vanillaStr, vanillaNum) VALUES(?, ?, ?)") { stmt ->
                    paramsToInsert.forEach {
                        stmt.setLong(1, it.first)
                        stmt.setString(2, it.second)
                        stmt.setInt(3, it.third)
                        stmt.executeUpdateAsyncKmp()
                    }

                }
            }

            db.vanillaDao.insert(VanillaEntity().apply {
                vanillaUid = 1000
            })

            db.vanillaDao.insert(VanillaEntity().apply {
                vanillaUid = 1002
            })

        }



    }

    @Test
    fun prepareAndUseStmtInTransaction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, VanillaDatabase::class.java, "BasicRoomDb")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        runBlocking {
            db.withDoorTransactionAsync(VanillaDatabase::class) { transactDb ->
                transactDb.prepareAndUseStatementAsync("INSERT INTO VanillaEntity(vanillaUid, vanillaStr, vanillaNum) VALUES(?, ?, ?)") { stmt ->
                    paramsToInsert.forEach {
                        stmt.setLong(1, it.first)
                        stmt.setString(2, it.second)
                        stmt.setInt(3, it.third)
                        stmt.executeUpdateAsyncKmp()
                    }
                }
            }
        }

        val entityFound = db.vanillaDao.findEntityByPk(42L)
        assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
    }

    @Test
    fun runTransactionAndGetInvalidation() {

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, VanillaDatabase::class.java, "VanillaDatabase")
            .fallbackToDestructiveMigration()
            .build().also {
                it.clearAllTables()
            }

        val invalidatedTables = mutableListOf<String>()

        db.getInvalidationTracker().addObserver(object: InvalidationTracker.Observer(arrayOf("VanillaEntity")) {
            override fun onInvalidated(tables: MutableSet<String>) {
                invalidatedTables.addAll(tables)
            }
        })

        Thread.sleep(1000)


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        db.withDoorTransaction(VanillaDatabase::class) { transactDb ->
            transactDb.prepareAndUseStatement("INSERT INTO VanillaEntity(vanillaUid, vanillaStr, vanillaNum) VALUES(?, ?, ?)") { stmt ->
                paramsToInsert.forEach {
                    stmt.setLong(1, it.first)
                    stmt.setString(2, it.second)
                    stmt.setInt(3, it.third)
                    stmt.executeUpdate()
                }
            }
        }


        Thread.sleep(2000)
        Assert.assertTrue(invalidatedTables.isNotEmpty())
    }

}