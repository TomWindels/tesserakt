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
Usage: java -jar sparql-endpoint.jar [<options>]

Options:
  --path=<text>    The name of the endpoint
  --port=<int>     The port number
  --disable-cache  Disables the use of in-memory query caches
  -h, --help       Show this message and exit
```
