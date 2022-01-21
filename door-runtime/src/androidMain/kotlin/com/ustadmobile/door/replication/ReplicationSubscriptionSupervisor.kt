package com.ustadmobile.door.replication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.ustadmobile.door.DoorDatabaseRepository

actual class ReplicationSubscriptionSupervisor actual constructor(
    private val replicationSubscriptionManager: ReplicationSubscriptionManager,
    repository: DoorDatabaseRepository
) {

    private val acceptableNetworks = mutableListOf<Network>()

    private val networkCallback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            acceptableNetworks += network
            replicationSubscriptionManager.enabled = true
        }

        override fun onLost(network: Network) {
            acceptableNetworks -= network
            replicationSubscriptionManager.enabled = acceptableNetworks.isNotEmpty()
        }

        override fun onUnavailable() {
            replicationSubscriptionManager.enabled = false
        }
    }

    init {
        val context: Context = (repository.config.context as Context)
        val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

}