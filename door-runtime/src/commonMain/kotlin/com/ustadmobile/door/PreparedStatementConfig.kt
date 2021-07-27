package com.ustadmobile.door

import com.ustadmobile.door.jdbc.StatementConstantsKmp

/**
 * Wrapper that contains the information needed to prepare a statement. Used by generated code.
 */
data class PreparedStatementConfig(
    val sql: String,
    val hasListParams: Boolean = false,
    val generatedKeys: Int = StatementConstantsKmp.NO_GENERATED_KEYS
)
