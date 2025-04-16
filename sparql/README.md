# SPARQL
## Installation
* Gradle (Android, Java, Kotlin Multiplatform): this module's artifact ID is `tesserakt-sparql`.
* NPM (Node.js): Coming soon.
## Description
An incremental SPARQL engine, supporting query evaluation on a changing data store. For regular querying, an iterable source of `Quad`s suffices. The engine evaluates every quad exactly once, incrementally updating the output. If the underlying set of data is expected to change over time, an ongoing query evaluation object can be used instead, supporting additions and removals of `Quad`s at any point in time, updating the results accordingly.
## Getting started
### Regular query execution
For regular data stores, executing SPARQL queries is straightforward.
```kt
// generating data
// ... or any other `Iterable<Quad>` source
val store = buildStore {
    /* See rdf-dsl for more information */
    val ex = prefix("ex", "http://example.org/")
    ex("tesserakt") has ex("feature") being ex("SparqlEngine")
}

// creating the query object
val query = Query.Select("SELECT ?s { ?s ?p ?o }")

// executing the query, getting its results
val bindings = store.query(query) // bindings = [{s = <http://example.org/tesserakt>}]
```
### Ongoing query execution
The incremental evaluation supports mutating the data whilst maintaining up-to-date query results. The most straightforward way of achieving this is by using a `MutableStore` instance.
```kt
// generating data
// ... or any other `Iterable<Quad>` source
val quad = Quad(
    "tesserakt".asNamedTerm(),
    // ...,
    // ...
)
val store = MutableStore()
store.add(quad)

// creating the query object
val query = Query.Select("SELECT ?s { ?s ?p ?o }")

// executing the query, getting its results
val evaluation = store.query(query)
var bindings = evaluation.results // bindings = [{s = <tesserakt>}]

// removing the data
store.remove(quad)

// updating the value of the bindings
bindings = evaluation.results // bindings = []
```
## Module overview
Most projects using the SPARQL engine only have to depend on the main `tesserakt-sparql` module. If you do need access to (some of) the internals, this overview describing the responsibilities of the various internal modules can come in handy.

### The `common` module
Defines external data structures, such as the basic set of SPARQL (output) types (e.g. query bindings).

### The `core` module
Defines internal data structures, such as the SPARQL query AST.

### The `compiler` module
Houses the compiler logic. The compiler is responsible for converting raw query strings into the query AST.

### The `runtime` module
Houses the incremental query evaluation logic. The incremental evaluation calculates the impact of an incoming data change (insertion or deletion), propagates these changes to the entire query state, and updates the results.
