package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
        val jdbcHelper = RoomDatabaseJdbcImplHelper(mockDataSource)

        jdbcHelper.useConnection {
            jdbcHelper.useConnection {

            }
        }

        verify(mockDataSource, times(1)).connection
        verify(mockConnection).close()
    }

    @Test
    fun givenUseConnectionCalled_whenUseConnectionCalledFromDifferentThread_thenShouldUseTwoConnections() {
        val jdbcHelper = RoomDatabaseJdbcImplHelper(mockDataSource)
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

        verify(mockDataSource, times(2)).connection
        verify(mockConnection, times(2)).close()
    }

}