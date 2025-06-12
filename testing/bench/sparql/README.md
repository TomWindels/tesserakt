# SPARQL benchmarking
## Module overview
This is a benchmarking tool, utilising the replay benchmark format to evaluate different SPARQL engine implementations.
Depending on the version and build configuration, different implementations are available, alongside support for SPARQL
endpoints.

## Installation
An executable JVM implementation can be built from source. The build task can be executed from the root of this
repository, using the `installDist` task of this module.
```
user@path/to/tesserakt$ ./gradlew testing:bench:sparql:installDist
```
After the build has finished, the resulting installation can be located in `build/install/sparql-bench`.

### Configuration
Without further configuration, only tesserakt and endpoint evaluation is available. Other implementations can be added
through build configuration.

The following implementations, and their identifiers, are as follows:

| Engine (JVM) | ID |
|---|---|
| Jena |`bench.sparql.jena` |
| Blazegraph | `bench.sparql.blazegraph` |
| RDFox | `bench.sparql.rdfox` |

| Engine (JS) | ID |
|---|---|
| Comunica | `bench.sparql.comunica` |

These IDs have to be added during the build process, with their values set to `enabled`.
This configuration can be added through multiple build parameters `-P<key>=<value>` or as key-value
pairs in `local.properties` (which has to be created at the root of the repository).

For example, to include Jena support, the following command can be executed:
```
user@path/to/tesserakt$ ./gradlew testing:bench:sparql:installDist -Pbench.sparql.jena=enabled
```

## CLI
The benchmarking tool can be interfaced with through its CLI. The `-h` flag exposes all available options:
```
Usage: sparql-bench [<options>]

Options:
  -i, --input=<text>                                Select the input filepath to use (can be a replay format)
  -o, --output=<text>                               The output filepath to use
  -e, --use-engine=(tesserakt|all)                  Select an engine implementation to use (multiple supported)
  -u, --url=<text>                                  Provide a SPARQL endpoint URL to use (multiple supported)
  --warmups=<int>                                   The number of (complete) runs before measuring performance
  --runs=<int>                                      The number of runs for every benchmark
  -h, --help                                        Show this message and exit
```
The `input` and `output` parameters are required. The `input` path can also point to (multiple) directories, in which
case files with the `ttl` extension will be chosen (this operation is **NOT** recursive).

### Example
The following command runs a single benchmark `path/to/file.ttl` (which can have multiple queries), writing its output
to `./output-folder/`, and tests both the endpoint hosted at `http://localhost:3000/sparql` and the tesserakt (JVM)
implementation.
```
./sparql-bench -i path/to/file.ttl -o ./output-folder/ -u http://localhost:3000/sparql -e tesserakt
```
