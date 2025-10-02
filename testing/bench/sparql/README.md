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
After the build has finished, the resulting installation can be located in this module's `build/install/sparql-bench`.

## CLI
The benchmarking tool can be interfaced with through its CLI. The `-h` flag exposes all available modes:
```
Usage: sparql-bench [<options>] <command> [<args>]...

Options:
  -h, --help  Show this message and exit

Commands:
  query   Benchmark the performance when evaluating a specific query over a fixed dataset
  replay  Benchmark the performance when evaluating a specific query over a changing dataset
  update  Benchmark the performance of a query over a dataset that is altered with a specific update between executions
```

### Query mode
The `query` mode evaluates the performance of the specified endpoints using a fixed (= constant) dataset, using the
queries that are passed in as additional arguments. Executing `sparql-bench query -h` results in the following help text:

```
Usage: sparql-bench query [<options>]

  Benchmark the performance when evaluating a specific query over a fixed dataset

Options:
  -o, --output=<value>  The output filepath to use
  --url=<value>         Provide a SPARQL endpoint URL to use (multiple supported)
  -i, --input=<text>    Select the input filepath(s) to use (has to be a valid Turtle/TriG file); not providing any results in a
                        single data test per evaluation, without manipulating the data itself
  -q, --query=<value>   Query to evaluate
  -h, --help            Show this message and exit
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

Options:
  -o, --output=<value>  The output filepath to use
  --url=<value>         Provide a SPARQL endpoint URL to use (multiple supported)
  -i, --input=<text>    Select the input filepath to use (has to be a replay format)
  -h, --help            Show this message and exit
```

### Update mode
The final evaluation strategy is called `update`, and allows benchmarks to evaluate a single query's performance before
and after applying a single SPARQL Update.

```
Usage: sparql-bench update [<options>]

  Benchmark the performance of a query over a dataset that is altered with a specific update between executions

Options:
  -o, --output=<value>      The output filepath to use
  --url=<value>             Provide a SPARQL endpoint URL to use (multiple supported)
  -u, --update-file=<text>  Path to the file containing the update
  -q, --query=<value>       Query to evaluate
  --warmup-query=<value>    The warmup query to evaluate. This query is executed before the first evaluation of the
                            to-be-evaluated query during the warmup phase
  --warmup-runs=<int>       The number of executions of all warmup queries before evaluation
  -h, --help                Show this message and exit
```
