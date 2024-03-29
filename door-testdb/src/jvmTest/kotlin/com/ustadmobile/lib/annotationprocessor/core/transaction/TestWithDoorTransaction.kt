package com.ustadmobile.lib.annotationprocessor.core.transaction

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.withDoorTransaction
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.room.InvalidationTrackerObserver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import repdb.RepDb
import repdb.RepEntity
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.naming.InitialContext
import javax.naming.NamingException


class TestWithDoorTransaction {

    private lateinit var initContext: InitialContext

    private lateinit var connectionCount: AtomicInteger

    private lateinit var lastConnection: AtomicReference<Connection>

    private lateinit var datasourceSpy: DataSource

    private lateinit var db: RepDb

    private var dbIndex = 0

    private var connectionCountBefore = 0

    private lateinit var invalidationObserver: InvalidationTrackerObserver

    @Before
    fun setup() {
        initContext = InitialContext()
        connectionCount = AtomicInteger()
        lastConnection = AtomicReference<Connection>()
        datasourceSpy = spy(initContext.lookup("java:/comp/env/jdbc/RepDb") as DataSource)

        datasourceSpy.stub {
            on { connection }.thenAnswer { invocation ->
                connectionCount.incrementAndGet()
                spy(invocation.callRealMethod()).also {
                    lastConnection.set(it as Connection)
                }
            }
        }

        val dbIndexToBind = dbIndex + 1

        try {
            initContext.bind("java:/comp/env/jdbc/RepDbSpy$dbIndexToBind", datasourceSpy)
        }catch(e: NamingException) {
            initContext.rebind("java:/comp/env/jdbc/RepDbSpy$dbIndexToBind", datasourceSpy)
        }


        db = DatabaseBuilder.databaseBuilder(
            RepDb::class, "java:/comp/env/jdbc/RepDbSpy$dbIndexToBind", dbIndexToBind.toLong()
        ).build()
            .apply {
                clearAllTables()
            }

        connectionCountBefore = connectionCount.get()

        val realInvalidationObserver : InvalidationTrackerObserver = object: InvalidationTrackerObserver(arrayOf("RepEntity")) {
            override fun onInvalidated(tables: Set<String>) {

            }
        }

        invalidationObserver = spy(realInvalidationObserver)
        db.invalidationTracker.addObserver(invalidationObserver)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun assertInvocationCounts() {
        Assert.assertEquals("Real connection retrieved count incremented by one",
            connectionCountBefore + 1, connectionCount.get())

        val lastConnectionSpy = lastConnection.get()
        verify(lastConnectionSpy, times(1)).autoCommit = false
        verify(lastConnectionSpy, times(1)).commit()


        //Check that the onTablesInvalidated is only called once at the end of the transaction
        verify(invalidationObserver, timeout(5000).times(1)).onInvalidated(argWhere {
            "RepEntity" in it && "DoorNode" in it
        })
    }

    @Test
    fun givenTransactionStarted_whenMultipleChangesOccur_commitWillBeCalledOnce() {
        db.withDoorTransaction { txDb ->
            val entity = RepEntity().apply {
                reNumField = 50
                rePrimaryKey = txDb.repDao.insert(this)
            }

            entity.reNumField = 51
            txDb.repDao.update(entity)

            txDb.repDao.insertDoorNode(DoorNode())
        }

        assertInvocationCounts()
    }

    @Test
    fun givenTransactionStarted_whenMultipleChangesOccurAsync_commitWillBeCalledOnce() = runBlocking {
        db.withDoorTransactionAsync { txDb ->
            val entity = RepEntity().apply {
                reNumField = 50
                rePrimaryKey = txDb.repDao.insertAsync(this)
            }

            entity.reNumField = 51
            txDb.repDao.updateAsync(entity)

            txDb.repDao.insertDoorNodeAsync(DoorNode())
        }

        assertInvocationCounts()
    }

    @Test
    fun givenTransactionStarted_whenNestedTranslationOccurs_commitWillBeCalledOnce() {
        db.withDoorTransaction { txDb ->
            txDb.withDoorTransaction { txDbNested ->
                val entity = RepEntity().apply {
                    reNumField = 50
                    rePrimaryKey = txDbNested.repDao.insert(this)
                }
                entity.reNumField = 51
                txDbNested.repDao.update(entity)
            }

            txDb.repDao.insertDoorNode(DoorNode())
        }

    }

    @Test
    fun givenTransactionStarted_whenExceptionThrown_thenConnectionShouldClose() {
        try {
            db.withDoorTransaction {
                throw Exception("Exception in transaction!")
            }
        }catch(e: Exception) {
            //do nothing
        }


        val lastConnectionSpy = lastConnection.get()
        verify(lastConnectionSpy, times(1)).rollback()
        verify(lastConnectionSpy, times(1)).close()
    }

    @Test
    fun givenTransactionStartedAsync_whenExceptionThrown_thenConnectionShouldRollbackAndClose() {
        runBlocking {
            try {
                db.withDoorTransactionAsync {
                    throw Exception("Exception in transaction!")
                }
            }catch (e: Exception) {
                //do nothing
            }

            val lastConnectionSpy = lastConnection.get()
            verify(lastConnectionSpy, times(1)).rollback()
            verify(lastConnectionSpy, times(1)).close()
        }
    }

}