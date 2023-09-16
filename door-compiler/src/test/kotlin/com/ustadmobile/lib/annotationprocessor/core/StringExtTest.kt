package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.ext.sqlToPostgresSql
import org.junit.Assert
import org.junit.Test

class StringExtTest {

    @Test
    fun givenNoPostgresSection_whenToPostgresSqlCalled_thenShouldBeRemoved() {
        val sql = """
            SELECT *
              FROM SomeTable
              --notpsql
              WHERE SOMETHING
              --endnotpsql
              GROUP BY foo
        """
        val postgresSQl = sql.sqlToPostgresSql()

        Assert.assertFalse(postgresSQl.contains("WHERE SOMETHING"))

    }

}