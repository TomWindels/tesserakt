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
    s = /* any `Quad.Term` any */,
    p = /* any `Quad.NamedTerm` instance */,
    o = /* any `Quad.Term` instance */,
    g = /* optional graph parameter, defaults to the default graph */,
)
```
A `Store` can be used as a collection of quads.
```kt
val myStore = Store()
myStore.add(myQuad)
```
Alternatively, a `MutableStore` instance can be used, allowing listeners to subscribe to store changes.
```kt
val myMutableStore = MutableStore()
myMutableStore.add(myQuad)
```
