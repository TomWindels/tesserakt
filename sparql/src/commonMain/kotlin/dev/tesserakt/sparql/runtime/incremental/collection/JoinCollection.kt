package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping

internal interface JoinCollection {

    val mappings: List<Mapping>

    fun join(other: JoinCollection): List<Mapping>

    /**
     * A specialised variant of the [join] method, where a collection of [Mapping]s [ignore] is skipped at join time,
     *  acting as if it has been [remove]d
     */
    fun join(other: JoinCollection, ignore: Iterable<Mapping>): List<Mapping>

    fun join(mapping: Mapping): List<Mapping>

    /**
     * A specialised variant of the [join] method, where a collection of [Mapping]s [ignore] is skipped at join time,
     *  acting as if it has been [remove]d
     */
    fun join(mapping: Mapping, ignore: Iterable<Mapping>): List<Mapping>

    fun join(mappings: List<Mapping>): List<Mapping>

    /**
     * A specialised variant of the [join] method, where a collection of [Mapping]s [ignore] is skipped at join time,
     *  acting as if it has been [remove]d
     */
    fun join(mappings: List<Mapping>, ignore: Iterable<Mapping>): List<Mapping>

    fun add(mapping: Mapping)

    fun addAll(mappings: Collection<Mapping>)

    fun remove(mapping: Mapping)

    fun removeAll(mappings: Collection<Mapping>)

}
