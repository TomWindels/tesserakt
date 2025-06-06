package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store


fun SparqlUpdateRequestBuilder.insert(builder: RDF.() -> Unit) {
    additions.addAll(buildStore(block = builder))
}

fun SparqlUpdateRequestBuilder.remove(builder: RDF.() -> Unit) {
    deletions.addAll(buildStore(block = builder))
}

fun SparqlUpdateRequestBuilder.add(quad: Quad) {
    additions.add(quad)
}

fun SparqlUpdateRequestBuilder.add(data: Store) {
    additions.addAll(data)
}

fun SparqlUpdateRequestBuilder.delete(quad: Quad) {
    deletions.add(quad)
}

fun SparqlUpdateRequestBuilder.delete(data: Store) {
    deletions.addAll(data)
}
