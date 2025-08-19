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

| Engine (JVM) | ID                        |
|--------------|---------------------------|
| Jena         | `bench.sparql.jena`       |
| Blazegraph   | `bench.sparql.blazegraph` |
| RDFox        | `bench.sparql.rdfox`      |

| Engine (JS) | ID                      |
|-------------|-------------------------|
| Comunica    | `bench.sparql.comunica` |

These IDs have to be added during the build process, with their values set to `enabled`.
This configuration can be added through multiple build parameters `-P<key>=<value>` or as key-value
pairs in `local.properties` (which has to be created at the root of the repository).

For example, to include Jena support, the following command can be executed:
```
user@path/to/tesserakt$ ./gradlew testing:bench:sparql:installDist -Pbench.sparql.jena=enabled
```

## CLI
The benchmarking tool can be interfaced with through its CLI. The `-h` flag exposes all available modes:
```
Usage: sparql-bench [<options>] <command> [<args>]...

Options:
  -h, --help  Show this message and exit

Commands:
  query   Benchmark the performance when evaluating a specific query over a fixed dataset
  replay  Benchmark the performance when evaluating a specific query over a changing dataset
```

### Query mode
The `query` mode evaluates the performance of the enabled implementation(s) using a fixed (= constant) dataset, using the
queries that are passed in as additional arguments. Executing `sparql-bench query -h` results in the following help text:

```
Usage: sparql-bench query [<options>]

  Benchmark the performance when evaluating a specific query over a fixed dataset

JVM-specific configuration:

  Various properties that are specific to the JVM version of the benchmarking tool

  --force-gc  Request the garbage collector to execute after every query execution

Options:
  -o, --output=<value>                   The output filepath to use
  -e, --use-engine=(tesserakt|all)       Select an engine implementation to use (multiple supported)
  -u, --url=<text>                       Provide a SPARQL endpoint URL to use (multiple supported)
  --runs=<int>                           The number of runs for every benchmark
  -i, --input=<text>                     Select the input filepath(s) to use (has to be a valid
                                         Turtle/TriG file); not providing any results in a single data
                                         test per evaluation, without manipulating the data itself
  -q, --query=<value>                    Query to evaluate
  -h, --help                             Show this message and exit
```

The combination of `-u`/`--url` and no input data (no `-i`/`--input` argument) will result in the tool evaluating the
provided queries to the provided endpoint(s), without manipulating or checking the data/state of said endpoint(s),
assuming endpoint data/state is already configured correctly. This is a fallback strategy for endpoints that do not
support the SPARQL Update protocol, and instead rely on other means of getting data into an endpoint not covered by this
tool. If data is provided using the `-i`/`--input` flag, the provided endpoint(s) are assumed empty (which will be
asserted during evaluation as well), followed by a SPARQL Update request, containing the dataset of the active evaluation.

### Replay mode
Another supported evaluation strategy, `replay`, is also supported by the benchmarking tool. This takes in a specialised
dataset as an argument, which represents a dataset that changes over time using the
[replay benchmark format](../../tooling/replay-benchmark), and a set of queries that should be used during evaluation.
Executing `sparql-bench replay -h` also results in a specialised help text:

```
Usage: sparql-bench replay [<options>]

  Benchmark the performance when evaluating a specific query over a changing dataset

JVM-specific configuration:

  Various properties that are specific to the JVM version of the benchmarking tool

  --force-gc  Request the garbage collector to execute after every query execution

Options:
  -o, --output=<value>                   The output filepath to use
  -e, --use-engine=(tesserakt|all)       Select an engine implementation to use (multiple supported)
  -u, --url=<text>                       Provide a SPARQL endpoint URL to use (multiple supported)
  --runs=<int>                           The number of runs for every benchmark
  -i, --input=<text>                     Select the input filepath to use (has to be a replay format)
  -h, --help                             Show this message and exit
```
