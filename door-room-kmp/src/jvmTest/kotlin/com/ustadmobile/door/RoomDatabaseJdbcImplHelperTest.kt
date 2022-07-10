package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test

class RoomDatabaseJdbcImplHelperTest {


    lateinit var mockConnection: Connection

    lateinit var mockDataSource: DataSource

    @BeforeTest
    fun setup() {
        mockConnection = mock { }
        mockDataSource = mock {
            on { connection }.thenReturn(mockConnection)
        }
    }

    @Test
    fun givenUseConnectionCalled_whenUseConnectionCalledNestedFromSameThread_thenShouldUseOneConnection() {
        val jdbcHelper = RoomDatabaseJdbcImplHelper(mockDataSource, mock { }, listOf("ExampleEntity"), mock { })

        jdbcHelper.dbType //This results in a call to getConnection, so call it at the start
        verify(mockDataSource, times(1)).connection

        jdbcHelper.useConnection {
            jdbcHelper.useConnection {

            }
        }

        verify(mockDataSource, times(2)).connection
        verify(mockConnection, times(2)).close()
    }

    @Test
    fun givenUseConnectionCalled_whenUseConnectionCalledFromDifferentThread_thenShouldUseTwoConnections() {
        val jdbcHelper = RoomDatabaseJdbcImplHelper(mockDataSource, mock { }, listOf("ExampleEntity"), mock { })

        jdbcHelper.dbType //This results in a call to getConnection, so call it at the start
        verify(mockDataSource, times(1)).connection

        val threads = (1..2).map {
            Thread {
                jdbcHelper.useConnection {
                    Thread.sleep(100)
                }
            }.also {
                it.start()
            }
        }

        threads.forEach {
            it.join()
        }

        verify(mockDataSource, times(3)).connection
        verify(mockConnection, times(3)).close()
    }

}