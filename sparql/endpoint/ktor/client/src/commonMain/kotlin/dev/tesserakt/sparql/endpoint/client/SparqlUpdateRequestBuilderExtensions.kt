package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store


/**
 * Inserts all quads that are generated inside the [builder] block. Quads also present in the DELETE operation
 *  cancel each other out.
 */
fun SparqlUpdateRequestBuilder.insert(builder: RDF.() -> Unit) {
    additions.addAll(buildStore(block = builder))
}

/**
 * Deletes all quads that are generated inside the [builder] block. Quads also present in the INSERT operation
 *  cancel each other out.
 */
fun SparqlUpdateRequestBuilder.delete(builder: RDF.() -> Unit) {
    deletions.addAll(buildStore(block = builder))
}

/**
 * Adds a single [quad] for insertion. If this quad is present inside the DELETE operation, they cancel each other out.
 */
fun SparqlUpdateRequestBuilder.add(quad: Quad) {
    additions.add(quad)
}

/**
 * Adds all [Quad]s found in the [data] [Store] for insertion. Quads present inside the DELETE operation cancel each
 *  other out.
 */
fun SparqlUpdateRequestBuilder.add(data: Store) {
    additions.addAll(data)
}

/**
 * Adds a single [quad] for deletion. If this quad is present inside the INSERT operation, they cancel each other out.
 */
fun SparqlUpdateRequestBuilder.remove(quad: Quad) {
    deletions.add(quad)
}

/**
 * Adds all [Quad]s found in the [data] [Store] for deletion. Quads present inside the INSERT operation cancel each
 *  other out.
 */
fun SparqlUpdateRequestBuilder.remove(data: Store) {
    deletions.addAll(data)
}
