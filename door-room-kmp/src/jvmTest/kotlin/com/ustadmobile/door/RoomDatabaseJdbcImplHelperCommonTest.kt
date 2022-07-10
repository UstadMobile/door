package com.ustadmobile.door

import androidx.room.InvalidationTracker
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

    @BeforeTest
    fun setup() {
        mockInvalidationTracker = mock() {}
        mockConnection = mock { }
        mockInvalidationTracker.setupSqliteTriggers(mockConnection)
        mockDataSource = mock {
            on { connection }.thenReturn(mockConnection)
        }
    }

    @Test
    fun givenUseConnectionAsyncCalled_whenUseConnectionAsyncCalledNested_thenShouldOpenOneConnection() {
        val helper = RoomDatabaseJdbcImplHelperCommon(mockDataSource, mock { }, listOf("ExampleEntity"), mock { })
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
        val helper = RoomDatabaseJdbcImplHelperCommon(mockDataSource, mock { }, listOf("ExampleEntity"), mock { })
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