package androidx.sqlite.db


interface SupportSQLiteDatabase {

    fun beginTransaction()

    fun setTransactionSuccessful()

    fun endTransaction()

    fun execSQL(sql: String)

}