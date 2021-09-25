package com.ustadmobile.door.transaction

import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.SQLException
import org.junit.Test
import java.sql.Connection
import org.mockito.kotlin.*

class TestDoorTransactionDataSourceWrapper {

    @Test
    fun givenTransactionDataSource_whenBlockCompletesSuccessfully_thenShouldCallCommitOnce() {
        val mockConnection = mock<Connection> { }
        val mockDataSource = mock<DataSource> {
            on { connection }.thenReturn(mockConnection)
        }

        val transactionDataSource = DoorTransactionDataSourceWrapper(mockDataSource)
        transactionDataSource.use {
            //pretend to do something that should be intercepted
            for(i in 0..2) {
                val con = it.connection
                con.autoCommit = false
                con.commit()
                con.autoCommit = true
            }
        }

        //Verify that these functions are in fact only called once
        verify(mockConnection, times(1)).autoCommit = false
        verify(mockConnection, times(1)).autoCommit = true
        verify(mockConnection, times(1)).commit()
        verify(mockConnection, times(1)).close()
    }

    @Test
    fun givenTransactionDataSource_whenBlockThrowsException_thenShouldCallRollback() {
        val mockConnection = mock<Connection> { }
        val mockDataSource = mock<DataSource> {
            on { connection }.thenReturn(mockConnection)
        }

        val transactionDataSource = DoorTransactionDataSourceWrapper(mockDataSource)
        try {
            transactionDataSource.use {
                throw SQLException("oops")
            }
        }catch(e: Exception) {

        }

        verify(mockConnection, times(1)).autoCommit = false
        verify(mockConnection, times(1)).autoCommit = true
        verify(mockConnection, times(1)).rollback()
        verify(mockConnection, times(1)).close()
    }

}