package com.ustadmobile.door

import kotlin.jvm.JvmStatic

class DoorDbType {

    companion object {

        const val SQLITE = 1

        const val POSTGRES = 2

        val SUPPORTED_TYPES = listOf(SQLITE, POSTGRES)

        @JvmStatic
        val PRODUCT_NAME_MAP = mapOf("PostgreSQL" to POSTGRES,
                "SQLite" to SQLITE)

        val PRODUCT_INT_TO_NAME_MAP = PRODUCT_NAME_MAP.keys.map { PRODUCT_NAME_MAP[it] to it }.toMap()

        fun typeIntFromProductName(productName: String) = PRODUCT_NAME_MAP[productName] ?: -1

    }
}

