# SPARQL endpoint CLI
## Module overview
The CLI version of the SPARQL endpoint manages a standalone webserver that exposes a single SPARQL endpoint, backed by
an (initially empty) in-memory RDF store. Interacting with this store can be done using SPARQL SELECT and UPDATE queries.

## Getting started

### JAR
The JAR file can be built from source. The build task can be executed from the root of this repository, using
the `buildFatJar` task of this module.
```
user@path/to/tesserakt$ ./gradlew sparql:endpoint:cli:buildFatJar
```
After the build has finished, the resulting JAR file can be located in `build/libs`.

### Native (GraalVM)
The module also supports the creation of a native version using GraalVM. A functional GraalVM install is required.
The creation of the native build can be done by executing the `nativeCompile` task of this module.
```
user@path/to/tesserakt$ ./gradlew sparql:endpoint:cli:nativeCompile
```
Alternatively, the JAR file created using the `buildFatJar` task can be converted using the GraalVM tooling. It's
recommended to use the same configuration applied in the `nativeCompile` task, which can be seen in this
module's [build.gradle.kts](build.gradle.kts).

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
