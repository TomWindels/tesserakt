# RDF DSL
## Installation
* Gradle (Android, Java, Kotlin Multiplatform): this module's artifact ID is `tesserakt-rdf-dsl`.
* NPM (Node.js): Coming soon.
## Features
The DSL does not add new functionality on top of the base RDF types. Instead, it makes typical RDF usage patterns more convenient when creating data programmatically.

The RDF DSL functionality is available in RDF "context" blocks. If you want to create an RDF store using the DSL, you can get access to such a "context" block using the `buildStore {}` statement. Inside the block (`{}`) itself, the DSL is available.

The API is inspired by Turtle / TriG syntax, with URIs using `(`, `)` brackets instead of `<`, `>`, and string literals with language tags using the `%` operator.

The main improvements introduced by the DSL include
* generating triples using statements;
```kt
val example = prefix("", "http://www.example.org/")
(example / "Subject") a (example / "MyType")
// evaluates to `<http://www.example.org/Subject> a <http://www.example.org/MyType>`
```
* expressing statement groups in a single (named) graph;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
(ex / "my-named-graph") {
    /**
     * Regular DSL statements
     * All produced triples are part of graph `<http://www.example.org/my-named-graph>`
     */
}
```
* creating typed literals;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
(ex / subject) (ex / "predicate1") ("date" `^^` XSD.dateTime)
(ex / subject) (ex / "predicate2") ("value" `^^` ex / "MyType")
```
* inferring literal types;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
(ex / subject) (ex / "predicate") (123) // creates a typed literal with type `XSD.int`
```
* creating string literals with a language tag;
```kt
// inside an RDF DSL block
val ex = prefix("ex", "http://www.example.org/")
(ex / subject) (ex / "name") ("name" % "en")
```
* convenient blank nodes;
```kt
// inside an RDF DSL block
(subject) (predicate) blank {
    /**
     * Shortened DSL statements
     * All produced triples have the generated blank node,
     *  used in the initial statement as object,
     *  as their subject.
     */
    // format: (predicate) (object)
    a (type)
    (p) (o)
    (p) blank {
        // another blank node
    }
}
```
* associating multiple objects with a subject-predicate pair;
```kt
// inside an RDF DSL block
(subject) (predicate) (
    object1, object2, object3
)
// generates 3 triples: <subject> <predicate> <object{1,2,3}>
```
* and generating RDF list structures.
```kt
// inside an RDF DSL block
(subject) (predicate) list listOf(
    (object1), (object2), (object3)
)
// uses `rdf:first` / `rdf:next` / `rdf:nil` ... to generate the structure
```

## Examples
The following code block generates five triples.
```kt
// uses an `FOAF` object, which implements the `Ontology` interface
val store = buildStore {
    val example = prefix("", "http://example/")
    val alice = example / "alice"
    val bob = example / "bob"

    // special case: RDF-type can be used without `(`, `)`
    alice a FOAF.Person
    (alice) (FOAF / "name") (example / "name")

    // a String type in the object position is ambiguous:
    // it can refer to a named term (IRI) or to a string literal
    // therefore, these have to be constructed manually
    (example / "name") (example / "firstName") ("Alice" `^^` XSD.string)
    (example / "name") (example / "lastName") ("LastName" `^^` XSD.string)

    bob a FOAF.Person
}
```
Other examples can be found in the various tests using generated RDF data, as these use the DSL in their implementation.
