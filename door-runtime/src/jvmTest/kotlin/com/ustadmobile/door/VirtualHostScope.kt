package com.ustadmobile.door

import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry

class VirtualHostScope(): Scope<String> {

    private val activeHosts = mutableMapOf<String, ScopeRegistry>()

    override fun getRegistry(context: String): ScopeRegistry = activeHosts.getOrPut(context) { StandardScopeRegistry() }

}