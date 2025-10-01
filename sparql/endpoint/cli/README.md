# SPARQL endpoint CLI
## Module overview
The CLI version of the SPARQL endpoint manages a standalone webserver that exposes a single SPARQL endpoint, backed by
an (initially empty) in-memory RDF store. Interacting with this store can be done using SPARQL SELECT and UPDATE queries.

## Getting started
The JAR file can be built from source. The build task can be executed from the root of this repository, using
the `buildFatJar` task of this module.
```
user@path/to/tesserakt$ ./gradlew sparql:endpoint:cli:buildFatJar
```
After the build has finished, the resulting JAR file can be located in `build/libs`.

## CLI
With the JAR file ready, the server can be started through the commandline. The `-h` flag exposes all available options:
```
Usage: tesserakt-endpoint [<options>]

  Create a tesserakt SPARQL endpoint from the command line

Options:
  --path=<text>       The name of the endpoint
  --port=<int>        The port number
  --cache-size=<int>  The number of queries to cache, with 0 being disabled (= default)
  --verbose           Enable additional logging
  --from-file=<path>  Use a dataset as an initial value for the in-memory store (can be N-Triples, Turtle or TriG)
  -h, --help          Show this message and exit
```
