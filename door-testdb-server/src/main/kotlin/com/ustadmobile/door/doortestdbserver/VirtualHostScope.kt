package com.ustadmobile.door.doortestdbserver

import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry

class VirtualHostScope: Scope<String> {

    private val activeEndpoints = mutableMapOf<String, ScopeRegistry>()

    val activeEndpointUrls: Set<String>
        get() = activeEndpoints.keys

    override fun getRegistry(context: String): ScopeRegistry = activeEndpoints.getOrPut(context) { StandardScopeRegistry() }

    companion object {

        val Default = VirtualHostScope()

    }
}