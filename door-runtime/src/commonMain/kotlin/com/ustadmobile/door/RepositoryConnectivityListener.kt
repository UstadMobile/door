package com.ustadmobile.door

interface RepositoryConnectivityListener {

    fun onConnectivityStatusChanged(newStatus: Int)

    fun onNewMirrorAvailable(mirror: MirrorEndpoint)

}