<h3 align="center">

![Static Badge](https://img.shields.io/badge/platform-kmp-mediumorchid) ![Static Badge](https://img.shields.io/badge/platform-android-brightgreen) ![Static Badge](https://img.shields.io/badge/platform-jvm-darkorange) ![Static Badge](https://img.shields.io/badge/platform-js-yellow)

![GitHub Release](https://img.shields.io/github/v/release/tomwindels/tesserakt?label=stable&color=%23208a48)
</h3>

Tesserakt offers a set of RDF and SPARQL tools, for a wide range of platforms, powered by [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html). The main goal of the library is offering a comprehensive, modular suite of RDF tooling, bringing the power of RDF to the Kotlin (Multiplatform), Java and JavaScript/TypeScript (NodeJS) platforms.

The module layout is further described in detail below. More module specific information, such as their APIs (with examples), can be found in separate READMEs located at the root of these modules.
## Getting started
> [!CAUTION]
> When using multiple modules in your project, make sure these versions are consistent. Mixing different module versions in the same project is not supported.

Note that some modules automatically include (and may expose) other modules in order to function; e.g. a SPARQL query evaluation exposes RDF types: binding result values are RDF terms.
### Gradle (Android, Java, Kotlin Multiplatform)
All artifacts are available on Maven Central. Make sure the repository `mavenCentral()` is configured.
All available modules follow the same naming convention, so the following can be repeated for every module you require.
```kt
// build.gradle.kts
dependencies {
    implementation("io.github.tomwindels:tesserakt-<module>:<version>")
}
```
Submodules can be retrieved by following the entire module path, using `-` as a separator. For instance, the `:rdf:dsl` module can be obtained using artifact ID `tesserakt-rdf-dsl`.
### NPM (NodeJS)
Coming soon.
## Module overview
Most modules have a dedicated README with an API overview and examples to quickly get started.
### RDF
##### [Module link](rdf/)
Core RDF module, defining basic types that are used across all other modules. 
Most modules expose these RDF types. Therefore, depending on this module directly is often unnecessary.
#### RDF DSL
##### [Module link](rdf/dsl/)
A Kotlin DSL with the goal of making RDF generation programmatically more convenient.
#### Snapshot store
##### [Module link](rdf/snapshot-store/)
A special RDF triple store implementation, supporting multiple versions of the same data. Uses an [LDES](https://semiceu.github.io/LinkedDataEventStreams/) to represent store segment changes, allowing the data changes to be recorded and represented in RDF.
### N3
##### [Module link](n3/)
> [!WARNING]  
> The N3 API is currently experimental. Continued N3 support is not guaranteed.

Similar to the RDF module, this defines basic N3 types, extending the RDF types to support graph members. Usage of this module is discouraged, as continued support is not guaranteed.
#### N3 DSL
##### [Module link](n3/dsl/)
An extension of the RDF DSL module, allowing graph members to be defined at the subject, predicate and object positions. Usage of this module is discouraged, as continued support is not guaranteed.
### SPARQL
##### [Module link](sparql/)
A SPARQL engine implementation using incremental evaluation. Supports an ongoing evaluation mode, allowing the input data to be altered at any time, which then updates the query results accordingly. A more in-depth module overview can be found in its [README](sparql/README.md).
### Serialization
##### [Module link](serialization/)
Offers support for various RDF representations, through a consistent API. Specialised modules for the following formats are available: [N-Triples](https://www.w3.org/TR/n-triples/), [Turtle](https://www.w3.org/TR/turtle/) and [TRiG](https://www.w3.org/TR/trig/).
### LDES
##### [Module link](stream/ldes/)
A simple implementation of the [LDES](https://semiceu.github.io/LinkedDataEventStreams/) specification.
### Replay benchmark
##### [Module link](testing/tooling/replay-benchmark/)
A benchmarking tool used to measure the performance of the SPARQL engine's incremental evaluation. Uses the [Snapshot store](rdf/snapshot-store/README.md) type to represent a changing RDF triple store.