package dev.tesserakt.sparql.endpoint.server.impl

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.server.SparqlEndpoint

fun SparqlEndpoint(store: ObservableStore = ObservableStore()): SparqlEndpoint = SparqlEndpointImpl(store)
