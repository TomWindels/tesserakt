# SPARQL benchmarking
## Module overview
This is a benchmarking tool, utilising the replay benchmark format to evaluate different SPARQL engine implementations.
Depending on the version and build configuration, different implementations are available, alongside support for SPARQL
endpoints.

## Getting started
An executable JVM implementation can be built from source. The build task can be executed from the root of this
repository, using the `installDist` task of this module.
```
user@path/to/tesserakt$ ./gradlew testing:bench:sparql:installDist
```
After the build has finished, the resulting installation can be located in `build/install/sparql-bench`.

## CLI
The benchmarking tool can be interfaced with through its CLI. The `-h` flag exposes all available options:
```
Usage: sparql-bench [<options>]

Options:
  -i, --input=<text>                     Select the input filepath to use (can be a replay format)
  -o, --output=<text>                    The output filepath to use
  -e, --use-engine=(jena|tesserakt|all)  Select an engine implementation to use (multiple supported)
  -u, --url=<text>                       Provide a SPARQL endpoint URL to use (multiple supported)
  -h, --help                             Show this message and exit
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
