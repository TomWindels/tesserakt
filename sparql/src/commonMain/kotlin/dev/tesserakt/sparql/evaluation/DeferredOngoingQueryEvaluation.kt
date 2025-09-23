package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore

/**
 * An ongoing query evaluation, queuing up changes made to attached [ObservableStore]s until [results] are
 *  requested.
 */
interface DeferredOngoingQueryEvaluation<RT> {

    /**
     * The results of this query evaluation.
     *
     * IMPORTANT: requesting the value can be expensive if the underlying store(s) have changed dramatically
     *  compared to previous versions.
     */
    val results: Collection<RT>

    fun subscribe(store: ObservableStore)

    fun unsubscribe(store: ObservableStore)

    fun debugInformation(): String

}
