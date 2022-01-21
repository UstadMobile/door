package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class PreparedStatementArrayProxyTest {

    lateinit var mockPreparedStatement: PreparedStatement

    lateinit var mockConnection: Connection

    @Before
    fun setup() {
        mockPreparedStatement = mock<PreparedStatement> { }
        mockConnection = mock<Connection> {
            on {
                prepareStatement(any())
            }.thenReturn(mockPreparedStatement)
        }
    }


    @Test
    fun testEmptyParams() {
        val querySql = """
        SELECT UserSession.usClientNodeId
          FROM ScopedGrant
               JOIN PersonGroupMember 
                    ON PersonGroupMember.groupMemberGroupUid = ScopedGrant.sgGroupUid
               JOIN UserSession
                    ON UserSession.usPersonUid = PersonGroupMember.groupMemberPersonUid
         WHERE (ScopedGrant.sgTableId = 6 AND ScopedGrant.sgEntityUid IN (?))
            OR (ScopedGrant.sgTableId = 164 AND ScopedGrant.sgEntityUid IN 
                (SELECT clazzSchoolUid
                   FROM Clazz
                  WHERE clazzUid IN (?)))
        """



        val proxy = PreparedStatementArrayProxy(querySql, mockConnection)
        proxy.setArray(1, JdbcArrayProxy("BIGINT", arrayOf()))
        proxy.setArray(2, JdbcArrayProxy("BIGINT", arrayOf()))
        proxy.prepareStatement()

        verify(mockConnection).prepareStatement("""
        SELECT UserSession.usClientNodeId
          FROM ScopedGrant
               JOIN PersonGroupMember 
                    ON PersonGroupMember.groupMemberGroupUid = ScopedGrant.sgGroupUid
               JOIN UserSession
                    ON UserSession.usPersonUid = PersonGroupMember.groupMemberPersonUid
         WHERE (ScopedGrant.sgTableId = 6 AND ScopedGrant.sgEntityUid IN ())
            OR (ScopedGrant.sgTableId = 164 AND ScopedGrant.sgEntityUid IN 
                (SELECT clazzSchoolUid
                   FROM Clazz
                  WHERE clazzUid IN ()))
        """)

    }

    @Test
    fun testWithArrayAndNonArrayParams() {
        val querySql = """
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN (?) 
               AND SomeTable.active = ? 
        """

        val proxy = PreparedStatementArrayProxy(querySql, mockConnection)
        proxy.setArray(1, JdbcArrayProxy("BIGINT", arrayOf(1L,2L)))
        proxy.setBoolean(2, false)

        proxy.prepareStatement()
        verify(mockConnection).prepareStatement("""
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN (?,?) 
               AND SomeTable.active = ? 
        """)

        verify(mockPreparedStatement).setLong(1, 1)
        verify(mockPreparedStatement).setLong(2, 2)
        verify(mockPreparedStatement).setBoolean(3, false)
    }

    @Test
    fun givenTwoArrays_whenPrepared_shouldSetCorrectValsAtCorrectPosition() {
        val querySql = """
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN (?) 
               AND SomeTable.active IN (?)
        """
        val proxy = PreparedStatementArrayProxy(querySql, mockConnection)
        proxy.setArray(1, JdbcArrayProxy("BIGINT", arrayOf(1L, 2L)))
        proxy.setArray(2, JdbcArrayProxy("BIGINT", arrayOf(3L, 4L)))

        proxy.prepareStatement()
        verify(mockConnection).prepareStatement("""
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN (?,?) 
               AND SomeTable.active IN (?,?)
        """)
        verify(mockPreparedStatement).setLong(1, 1L)
        verify(mockPreparedStatement).setLong(2, 2L)
        verify(mockPreparedStatement).setLong(3, 3L)
        verify(mockPreparedStatement).setLong(4, 4L)
    }

    @Test
    fun givenOneEmptyArray_whenPrepared_shouldSetCorrectValsAtCorrectPosition() {
        val querySql = """
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN (?) 
               AND SomeTable.active IN (?)
        """
        val proxy = PreparedStatementArrayProxy(querySql, mockConnection)
        proxy.setArray(1, JdbcArrayProxy("BIGINT", arrayOf()))
        proxy.setArray(2, JdbcArrayProxy("BIGINT", arrayOf(3L, 4L)))

        proxy.prepareStatement()

        verify(mockConnection).prepareStatement("""
            SELECT SomeTable.someField
             WHERE SomeTable.uid IN () 
               AND SomeTable.active IN (?,?)
        """)
        verify(mockPreparedStatement).setLong(1, 3L)
        verify(mockPreparedStatement).setLong(2, 4L)
    }


}