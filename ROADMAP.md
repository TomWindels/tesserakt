# Roadmap
## TODOs
### SPARQL Compiler
#### Analyser
* Current Analyser impl can be slightly more fragmented in function calls: `SELECT DISTINCT` & `SELECT *` should move
 to a different stub instead of reusing the `SELECT` and `SELECT ?binding` stub
* Compiled queries need to be able to be exported as `String` again with exact output semantics (binding names of
 main `SELECT` query cannot be renamed for instance) for use with SPARQL endpoints
#### Other
* Optimiser for pattern blocks using input heuristics
* Optimiser reducing names for inner bindings not exposed or only reused in subqueries
* DSL for (almost) compiled queries instead of `String` for use in local queries, using similar style of RDF writing
### Querying
* Implementation of query flow below using recursion to generate new bindings of input data
`input -> base query -> subquery1 -> ... -> subqueryN: resultsN -> resultsN-1 -> ... -> query results`.
* Sub-results and not-yet-completely-resolved bindings from queries above can be "kept alive" for processing deltas in
 `QueryResult`(?) classes
* Support for querying with incremental changes (main focus being addition)
### Other functionality
* Migrate over code from LDESTS to classes here (`commonMain/core/rdf` mostly) for RDF interop
* Change use of N3 types in JS port to only allow conversion to the N3 types, so all main logic uses the same types
* Migrate over (non-)LDESTS ontologies for use here
* Porting/migrating to K2
* Renaming/refactoring classes from above with K2 context receivers instead of extension functions (`RDFContext`?)
## Planned features
... TODO: Write this prior to initial release
## Timeline
... TODO: Write this prior to initial release