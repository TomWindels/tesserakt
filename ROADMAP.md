# Roadmap
## TODOs
### SPARQL Compiler
#### Other
* Optimiser for pattern blocks using input heuristics
* DSL for (almost) compiled queries instead of `String` for use in local queries, using similar style of RDF writing
### Querying
* Implementation of query flow below using recursion to generate new bindings of input data
`input -> base query -> subquery1 -> ... -> subqueryN: resultsN -> resultsN-1 -> ... -> query results`.
* Sub-results and not-yet-completely-resolved bindings from queries above can be "kept alive" for processing deltas in
 `QueryResult`(?) classes
* Support for querying with incremental changes (main focus being addition)
### Other functionality
* Change use of N3 types in JS port to only allow conversion to the N3 types, so all main logic uses the same types
* Renaming/refactoring classes from above with K2 context receivers instead of extension functions (`RDFContext`?)
## Planned features
... TODO: Write this prior to initial release
## Timeline
... TODO: Write this prior to initial release