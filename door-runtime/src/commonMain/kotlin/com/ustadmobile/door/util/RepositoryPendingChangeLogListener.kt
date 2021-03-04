package com.ustadmobile.door.util

interface RepositoryPendingChangeLogListener {

    fun onPendingChangeLog(tableIdList: Set<Int>)

}