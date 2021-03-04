package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)

/**
 * Postgres does not offer the same REPLACE INTO functionality that SQLite provides. It provides an
 * ON CONFLICT clause, see: https://www.postgresql.org/docs/12/sql-insert.html
 *
 * This annotation can be added to a method that is annotated with @Insert using OnConflictStrategy.REPLACE .
 * By default door will generate an onconflict clause that only considers the primary key. If you
 * want to do something else (e.g. do an insert when a unique index has been duplicated) this annotation
 * can be added.
 *
 * e.g.
 *
 * @Insert(onConflict = OnConflictStrategy.REPLACE)
 * @PgOnConflict("ON CONFLICT(uniquecol1, uniquecol2) DO UPDATE SET timestamp = excluded.timestamp")
 * fun insertSomeEntity(entity: SomeEntity)
 */
annotation class PgOnConflict(val value: String)
