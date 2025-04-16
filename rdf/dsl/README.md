# RDF DSL
## Installation
* Gradle (Android, Java, Kotlin Multiplatform): this module's artifact ID is `tesserakt-rdf-dsl`.
* NPM (Node.js): Coming soon.
## Features
The DSL does not add new functionality on top of the base RDF types. Instead, it makes typical RDF use patterns more convenient when creating data programmatically.

The RDF DSL functionality is available in RDF "context" blocks. If you want to create an RDF store using the DSL, you can get access to such a "context" block using the `buildStore {}` statement. Inside the block (`{}`) itself, the DSL is available.

The main improvements introduced by the DSL include
* generating triples using statements;
```kt
"http://www.example.org/Subject".asNamedTerm() has type being "http://www.example.org/MyType".asNamedTerm()
// evaluates to `<http://www.example.org/Subject> a <http://www.example.org/MyType>`
```
* using prefixes and ontologies to generate IRIs;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
ex("Subject") has type being ex("MyType")
// evaluates to `<http://www.example.org/Subject> a <http://www.example.org/MyType>`
```
* expressing statement groups in a single (named) graph;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
graph(ex("my-named-graph")) {
    /**
     * Regular DSL statements
     * All produced triples are part of graph `<http://www.example.org/my-named-graph>`
     */
}
```
* convenient blank nodes;
```kt
// inside an RDF DSL block
subject has predicate being blank {
    /**
     * Shortened DSL statements
     * All produced triples have the generated blank node,
     *  used in the initial statement as object,
     *  as their subject.
     */
    // format: `predicate` being `object`
}
```
* and generating common RDF structures, such as RDF lists.
```kt
// inside an RDF DSL block
subject has predicate being list(
    object1, object2, object3
)
```

## Examples
The following code block generates five triples.
```kt
val store = buildStore {
    val example = prefix("", "http://example/")
    val alice = example("alice")
    val bob = example("bob")

    alice has type being FOAF.Person
    alice has FOAF("name") being example("name")

    // a String type in the object position is ambiguous:
    // it can refer to a named term (IRI) or to a string literal
    example("name") has example("firstName") being "Alice".asLiteralTerm()
    example("name") has example("lastName") being "LastName".asLiteralTerm()

    bob has type being FOAF.Person
}
```
Other examples can be found in the various tests using generated RDF data, as these use the DSL in their implementation.
