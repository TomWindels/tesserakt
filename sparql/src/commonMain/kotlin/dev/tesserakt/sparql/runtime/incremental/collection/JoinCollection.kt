package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping

interface JoinCollection {

    val mappings: List<Mapping>

    fun join(other: JoinCollection): List<Mapping>

    fun join(mapping: Mapping): List<Mapping>

    fun join(mappings: List<Mapping>): List<Mapping>

    fun add(mapping: Mapping)

    fun addAll(mappings: Collection<Mapping>)

    fun remove(mapping: Mapping)

    fun removeAll(mappings: Collection<Mapping>)

}
