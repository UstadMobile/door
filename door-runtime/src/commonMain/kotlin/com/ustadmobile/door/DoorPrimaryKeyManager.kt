package com.ustadmobile.door

import com.ustadmobile.door.util.*
import kotlinx.atomicfu.AtomicLong
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Manage generation of unique primary keys for each table. This is inspired by the Twitter snowflake
 * approach. It is slightly modified so that the 64bit keys are generated as follows:
 *
 * 31 bits: unix timestamp (offset by CUSTOM_EPOCH)
 * 20 bits: Node Id
 * 12 bits: Sequence number
 * 1 bit (sign bit): unused
 *
 * This allows 1,048,576 nodes to create 4,096 unique new entries per second (per table). This supports
 * more unique nodes than snowflake with fewer unique keys per second. This seems appropriate as most
 * work is delegated to the client.
 *
 * @param tableIdList a list of all tableIds on the database.
 */
class DoorPrimaryKeyManager(tableIdList: Collection<Int>) {

    private val tableKeyManagers = threadSafeMapOf<Int, TablePrimaryKeyManager>()

    private inline val timestamp: Long
        get() = (systemTimeInMillis() / 1000) - CUSTOM_EPOCH

    val nodeId: Int by lazy(LazyThreadSafetyMode.NONE) {
        generateDoorNodeId(MAX_NODE_ID)
    }

    init {
        tableIdList.forEach {
            tableKeyManagers[it] = TablePrimaryKeyManager()
        }
    }

    private inner class TablePrimaryKeyManager() {

        /**
         * We have two values that we are tracking concurrently: the sequence number and the
         * timestamp. When the timestamp advances, we can reset the sequence number to zero.
         *
         * These two values are wrapped in a single long so that we can use a single AtomicLong
         * wrapper instead of requiring thread locking.
         */
        private val atomicWrapper = atomic(0L)

        //If this is not inline, then AtomicFu will complain
        @Suppress("NOTHING_TO_INLINE")
        private inline fun AtomicLong.nextWrappedTimeAndSeqNum(): Long = updateAndGet { lastVal ->
            val lastTimestamp = lastVal shr 32
            val lastSeq = lastVal and Int.MAX_VALUE.toLong()

            val newTimestamp = timestamp
            val newSeq = if(newTimestamp > lastTimestamp) {
                0
            }else {
                lastSeq + 1
            }

            (newTimestamp shl 32) or newSeq
        }

        private fun Long.unwrapTime() = this shr 32

        private fun Long.unwrapSeqNum() = this and Int.MAX_VALUE.toLong()

        private fun generateId(currentTimestamp: Long, nodeId: Long, seqNum: Long) : Long{
            return currentTimestamp shl (NODE_ID_BITS + SEQUENCE_BITS) or
                    (nodeId shl SEQUENCE_BITS) or
                    seqNum
        }

        fun nextId(): Long {
            val nextWrappedId = atomicWrapper.nextWrappedTimeAndSeqNum()
            val seqNum = nextWrappedId.unwrapSeqNum()

            if(seqNum < MAX_SEQUENCE) {
                return generateId(nextWrappedId.unwrapTime(), nodeId.toLong(), seqNum)
            }else {
                //wait long enough for the next second
                waitBlocking(1001 - (systemTimeInMillis() % 1000))
                return nextId()
            }
        }

        suspend fun nextIdAsync() : Long {
            val nextWrappedId = atomicWrapper.nextWrappedTimeAndSeqNum()
            val seqNum = nextWrappedId.unwrapSeqNum()

            if(seqNum < MAX_SEQUENCE) {
                return generateId(nextWrappedId.unwrapTime(), nodeId.toLong(), seqNum)
            }else {
                //wait long enough for the next second
                delay(1001 - (systemTimeInMillis() % 1000))
                return nextId()
            }
        }

    }

    fun nextId(tableId: Int): Long {
        //Note: it is required that the list of valid table ids is passed as a constructor parameter
        //Hence the return result should never be null
        return tableKeyManagers[tableId]!!.nextId()
    }

    suspend fun nextIdAsync(tableId: Int): Long = tableKeyManagers[tableId]!!.nextIdAsync()

    companion object {
        const val UNUSED_BITS = 1

        const val EPOCH_BITS = 31 // Time in seconds - works for up to 34 years from 1/Jan/2020

        const val NODE_ID_BITS = 20

        const val SEQUENCE_BITS = 12

        val MAX_NODE_ID = 2f.pow(NODE_ID_BITS).toInt() // Up to 1,048,576 nodes

        val MAX_SEQUENCE = 2f.pow(SEQUENCE_BITS).toInt() //Up to 4.096

        // Custom Epoch (January 1, 2015 Midnight UTC = 2015-01-01T00:00:00Z)

        //01/Jan/2020 at 00:00:00 UTC
        const val CUSTOM_EPOCH = 1577836800
    }

}
