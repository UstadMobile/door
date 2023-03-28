package androidx.room

import androidx.annotation.IntDef

/**
 * Set of conflict handling strategies for various [Dao] methods.
 */
@Retention(AnnotationRetention.SOURCE)
@IntDef(OnConflictStrategy.REPLACE, OnConflictStrategy.ROLLBACK, OnConflictStrategy.ABORT, OnConflictStrategy.FAIL, OnConflictStrategy.IGNORE)
annotation class OnConflictStrategy {
    companion object {
        /**
         * OnConflict strategy constant to replace the old data and continue the transaction.
         */
        const val REPLACE = 1
        /**
         * OnConflict strategy constant to rollback the transaction.
         *
         */
        @Deprecated("Does not work with Android's current SQLite bindings. Use {@link #ABORT} to\n" +
                "      roll back the transaction.")
        const val ROLLBACK = 2
        /**
         * OnConflict strategy constant to abort the transaction. *The transaction is rolled
         * back.*
         */
        const val ABORT = 3
        /**
         * OnConflict strategy constant to fail the transaction.
         *
         */
        @Deprecated("Does not work as expected. The transaction is rolled back. Use {@link #ABORT}.")
        const val FAIL = 4
        /**
         * OnConflict strategy constant to ignore the conflict.
         */
        const val IGNORE = 5
    }

}