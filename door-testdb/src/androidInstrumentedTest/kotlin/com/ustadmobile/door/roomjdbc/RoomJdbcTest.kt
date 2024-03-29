package com.ustadmobile.door.roomjdbc

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.ext.prepareAndUseStatement
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.withDoorTransaction
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import dbonly.VanillaDatabase
import dbonly.VanillaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

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
        Assert.assertEquals("Got count", 1, count)
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
        Assert.assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
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
            db.withDoorTransactionAsync { txDb ->
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
            db.withDoorTransactionAsync { transactDb ->
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
        Assert.assertNotNull("Found entity inserted by prepareAndUseStatement:", entityFound)
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

        db.invalidationTracker.addObserver(object: InvalidationTracker.Observer(arrayOf("VanillaEntity")) {
            override fun onInvalidated(tables: Set<String>) {
                invalidatedTables.addAll(tables)
            }
        })

        Thread.sleep(1000)


        val paramsToInsert = listOf(
            Triple(42L, "Bob", 20),
            Triple(50L, "Belinda", 21)
        )

        db.withDoorTransaction { transactDb ->
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