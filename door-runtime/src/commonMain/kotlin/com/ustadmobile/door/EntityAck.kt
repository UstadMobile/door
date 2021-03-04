package com.ustadmobile.door

/**
 * Class that is sent by the client to acknowledge receipt of an entity
 */
data class EntityAck(val epk: Long, val csn: Int)
