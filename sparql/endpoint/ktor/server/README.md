# SPARQL endpoint Ktor server integration
## Installation
* Gradle (Java): this module's artifact ID is `tesserakt-sparql-endpoint-ktor-server`.
* NPM (Node.js): Unsupported

## Module overview
The server module allows existing Ktor applications to host their own SPARQL endpoint(s).

### Getting started
After adding the module as a dependency in your project, SPARQL endpoint modules can be added to your server setup:
```kt
// inside of Route configuration, e.g. through the top-level `routing {}` block or an inner `route("...") {}` block
routing {
    // this: Route
    sparqlEndpoint(/* optional endpoint configuration */)
}
```

### Endpoint configuration
Various configuration options are available to fine-tune the created SPARQL endpoint.
* `path` (String) - the path name under which the endpoint is registered
* `endpoint` (SparqlEndpoint) - the endpoint instance that should be used to evaluate SELECT and UPDATE queries. A caching variant is available, which reuses query results as much as possible.
* `json` (Json) - the serializer used to create the serialized representation of binding results
