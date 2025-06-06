# SPARQL endpoint
## Module overview
The various modules available offer the ability to interact with and/or host your own SPARQL endpoints.
Below, a more complete overview of the various modules is available.

### The `cli` module
This module contains a standalone JVM server using Ktor. It hosts a single, configurable SPARQL endpoint,
backed by a mutable (empty) RDF store. SPARQL updates can be used to add (and remove) in-memory quads.

### The `ktor` module
This module contains a set of server and client tools to provide or consume SPARQL endpoints using the
[ktor](https://ktor.io/) framework.
