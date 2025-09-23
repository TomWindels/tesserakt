# SPARQL endpoint Ktor client integration
## Installation
* Gradle (Android, Java, Kotlin Multiplatform): this module's artifact ID is `tesserakt-sparql-endpoint-ktor-client`.
* NPM (Node.js): Unsupported

## Module overview
The client module allows existing Ktor applications to interact with SPARQL endpoints.

### Getting started
After adding the module as a dependency in your project, the `HttpClient` has to be configured before it can use SELECT
queries. The [ContentNegotiation](https://ktor.io/docs/client-serialization.html) plugin has to be configured as follows:
```kt
install(ContentNegotiation) {
    sparql()
    // other content negotiation configuration can be added as well
}
```

### Execute a SPARQL SELECT query
Using an existing `HttpClient` instance, a SPARQL SELECT query can be executed as follows:
```kt
val response = client.sparqlQuery("SELECT ...", "https://example.org/sparql")
```
The returned `HttpResponse` instance is identical to other Ktor calls.

Using the `HttpResponse::bodyAsBindings` extension method, bindings created through a SELECT query can be obtained:
```kt
val bindings = response.bodyAsBindings() // List<Bindings>
```

### Execute a SPARQL UPDATE query
The module offers two APIs to execute UPDATEs: using regular stores, and using the [RDF DSL](../../../../rdf/dsl).
Both methods can be used interchangeably: the resulting query is created and simplified just before sending the request
to the endpoint.

#### Using stores
Using an existing `HttpClient` instance, a SPARQL UPDATE query can be executed as follows:
```kt
val response = client.sparqlUpdate("https://example.org/sparql") {
    add(/* store with to-be-added triples */)
    remove(/* store with to-be-removed triples */)
}
```
The returned `HttpResponse` instance is identical to other Ktor calls.

#### Using the RDF DSL
Using an existing `HttpClient` instance, a SPARQL UPDATE query can be executed as follows:
```kt
val response = client.sparqlUpdate("https://example.org/sparql") {
    insert {
        /* RDF DSL */
    }
    delete {
        /* RDF DSL */
    }
}
```
The returned `HttpResponse` instance is identical to other Ktor calls.
