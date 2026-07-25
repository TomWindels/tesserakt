# Serialization
## Installation
This module is **not** directly published. Instead, the individual formats are available separately.
* Gradle (Android, Java, Kotlin Multiplatform): The individual formats are available by appending it to `tesserakt-serialization` using a dash. For instance, to get the Turtle serializer, the module is `tesserakt-serialization-turtle`.
* NPM (Node.js): Coming soon.
## Getting started
### Serialization API
When at least one format is configured as a dependency, two APIs become available to deserialize data:
* Create a store using the new factory methods, e.g. from a `java.io.File` (JVM targets):
```kt
val store = Store(/* file */, /* format, e.g. `Turtle` from the `tesserakt-serialization-turtle` module */)
```
* Getting access to the `Serializer` implementation for a format, and using that to iterate over the `Quad`s as they become available:
```kt
val serializer = serializer(/* format, e.g. `Turtle` from the `tesserakt-serialization-turtle` module */)
// getting an iterator, adding it to an existing store using `toStore()`, ...
serializer.deserialize(/* data source */).forEach { quad -> /* use the obtained `quad` */ }
```
The `Serializer` instance obtained through the second method also allows for serialization of `Store`s and `Iterable<Quad>` implementations.
```kt
// generating data
// ... or any other `Iterable<Quad>` source
val store = buildStore {
    /* See rdf-dsl for more information */
    val ex = prefix("ex", "http://example.org/")
    ex("tesserakt") has ex("feature") being ex("SparqlEngine")
}

// using the default Turtle serializer
val serializer = serializer(Turtle)

val ttl = serializer.serialize(store) // Iterator<String> that can be written to a file or concatenated into the complete string representation
```
### Serialization customisation
Some serializers, such as those obtained from the `Turtle` and `TriG` formats, allow for configuration, such as through a DSL.
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
val serializer = serializer(Turtle) {
    usePrettyFormatting {
        // applying the `ex:` prefix defined in the RDF_DSL block above
        withPrefixes(rdf.extractPrefixes())
        // change the indent
        withDynamicIndent()
    }
}

val ttl = serializer.serialize(store) // Iterator<String> that can be written to a file or transformed into a regular string
```
