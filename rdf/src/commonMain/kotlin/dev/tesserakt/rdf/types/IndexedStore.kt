package dev.tesserakt.rdf.types

/**
 * A store that utilises indexes to store its data for efficient data retrieval. It adds no extra methods on
 *  the [Store] API, instead facilitating faster access guarantees for the [Store.iter] method when
 *  using non-null filter parameters.
 */
interface IndexedStore : Store
