/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
