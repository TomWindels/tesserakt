# RDF
## Installation
> [!NOTE]  
> The RDF module is a core module to most other modules' API, so directly depending on this module is often unnecessary.
* Gradle (Android, Java, Kotlin Multiplatform): this module's artifact ID is `tesserakt-rdf`.
* NPM (Node.js): Coming soon.
## Features
The `Quad` type represents RDF triples/quads. New quads can be created manually.
The `Store` type represents a basic collection of RDF quads. Basic collection operations are supported.
The `MutableStore` type is an extension of the regular `Store` type. It supports listeners to subscribe to store changes, invoking a callback whenever the underlying store is altered. The main purpose of this store type is in combination with the incremental SPARQL engine.

Common ontology definitions are also included. This can be used to create quads using these ontologies.
## Usage/Examples
> [!TIP]
> It is recommended to use the RDF DSL module, as it makes data creation programmatically more convenient.

Basic quads can be created manually.
```kt
val myQuad = Quad(
    s = /* any `Quad.Subject` instance */,
    p = /* any `Quad.Predicate` instance */,
    o = /* any `Quad.Object` instance */,
    g = /* optional graph parameter, defaults to the default graph */,
)
```
A `Store` can be used as a collection of quads.
```kt
val myStore = Store(myQuad)
myStore.size // 1
```
Alternatively, a `MutableStore` instance can be used, allowing data to be altered after creation.
```kt
val myMutableStore = MutableStore()
myMutableStore.add(myQuad)
```
Finally, an `ObservableStore` instance can be used, allowing data changes to be observed by various listeners, e.g. when using SPARQL queries.
```kt
val myObservableStore = ObservableStore()
myObservableStore.addListener(/* a listener implementation */)
myObservableStore.add(myQuad) // notifies the listener added above
```

## Performance
When targeting the JVM, certain operations can benefit from multithreading. Various deserialization and store-related
operations can split up work across multiple worker threads when available. This can be accomplished using the following
configuration:
```kt
// use the default multithreaded configuration, using a cached threadpool configured as daemon threads
ConcurrencyMode.set(MultiThreaded)
// or, using an existing executor service
ConcurrencyMode.set(MultiThreaded(myExecutorService))
// or, go back to single threaded once the performance critical section ends
ConcurrencyMode.set(SingleThreaded)
```
This should be set once, before the performance-critical section is executed. Whilst this is available in both
Kotlin/JVM and Kotlin/Android, it is not recommended when targeting Android, as the multithreaded implementation favors
throughput over computational efficiency.