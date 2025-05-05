# Serialization
## Installation
This module is **not** directly published. Instead, the individual formats are available separately.
* Gradle (Android, Java, Kotlin Multiplatform): The individual formats are available by appending it to `tesserakt-serialization` using a dash. For instance, to get the Turtle serializer, the module is `tesserakt-serialization-turtle`.
* NPM (Node.js): Coming soon.
## Description
A series of supported RDF serialization formats. Supports both reading from and writing to text documents using these formats using iterators. Some serializers support additional configuration.
## Getting started
### Serialization API
All serializers extend the basic `Serializer` type, exposing functionality to `serialize` `Quad`s and `deserialize` from a `DataSource`.
```kt
// generating data
// ... or any other `Iterable<Quad>` source
val store = buildStore {
    /* See rdf-dsl for more information */
    val ex = prefix("ex", "http://example.org/")
    ex("tesserakt") has ex("feature") being ex("SparqlEngine")
}

// using the default Turtle serializer
val serializer = TurtleSerializer

val ttl = serializer.serialize(store) // Iterator<String> that can be written to a file or transformed into a regular string
```
### Serialization customisation
Some serializers, such as `TurtleSerializer` and `TriGSerializer`, allow for configuration, such as through a DSL.
```kt
// generating data
// ... or any other `Iterable<Quad>` source
val rdf: RDF_DSL = {
    /* See rdf-dsl for more information */
    val ex = prefix("ex", "http://example.org/")
    ex("tesserakt") has ex("feature") being ex("SparqlEngine")
}

val store = buildStore(block = rdf)

// using the default Turtle serializer
val serializer = turtle {
    usePrettyFormatting {
        // applying the `ex:` prefix
        withPrefixes(rdf.extractPrefixes())
        // change the indent
        withDynamicIndent()
    }
}

val ttl = serializer.serialize(store) // Iterator<String> that can be written to a file or transformed into a regular string
```
