package com.ustadmobile.door

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import kotlinx.coroutines.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.Test


class RoomDatabaseJdbcImplHelperCommonTest {


    lateinit var mockConnection: Connection

    lateinit var mockDataSource: DataSource

    lateinit var mockInvalidationTracker: InvalidationTracker

    private class RoomDatabaseJdbcImplHelperCommonImpl(
        dataSource: DataSource,
        db: RoomDatabase,
        tableNames: List<String>,
        invalidationTracker: InvalidationTracker,
    ): RoomDatabaseJdbcImplHelperCommon(dataSource, db, tableNames, invalidationTracker) {
        override suspend fun Connection.setupSqliteTriggersAsync() {

        }
    }

    @BeforeTest
    fun setup() {
        mockInvalidationTracker = mock {
            onBlocking { findChangedTablesOnConnectionAsync(any()) }.thenReturn(listOf())
        }
        mockConnection = mock { }
        mockDataSource = mock {
            on { connection }.thenReturn(mockConnection)
        }

    }

    @Test
    fun givenUseConnectionAsyncCalled_whenUseConnectionAsyncCalledNested_thenShouldOpenOneConnection() {
        val helper = RoomDatabaseJdbcImplHelperCommonImpl(mockDataSource, mock { }, listOf("ExampleEntity"),
            mockInvalidationTracker)
        runBlocking {
            helper.useConnectionAsync {
                withContext(Dispatchers.IO) {
                    helper.useConnectionAsync {

                    }
                }

            }
        }

        verify(mockDataSource, times(1)).connection
        verify(mockConnection, times(1)).close()
    }

    @Test
    fun givenUseConnectionAsyncCalled_whenUseConnectionAsyncCalledInNewContext_thenShouldOpenTwoConnections() {
        val helper = RoomDatabaseJdbcImplHelperCommonImpl(mockDataSource, mock { }, listOf("ExampleEntity"),
            mockInvalidationTracker)
        runBlocking {
            repeat(2) {
                launch {
                    helper.useConnectionAsync {
                        delay(100)
                    }
                }
            }
        }

        verify(mockDataSource, times(2)).connection
        verify(mockConnection, times(2)).close()
    }

}