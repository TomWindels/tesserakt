package sparql.tests

actual fun awaitBenchmarkStart() {
    println("PID: ${ProcessHandle.current().pid()}")
    println("Benchmark ready. Press enter to start")
    System.`in`.read()
}
